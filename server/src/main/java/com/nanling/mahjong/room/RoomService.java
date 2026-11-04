package com.nanling.mahjong.room;

import com.nanling.mahjong.api.dto.CreateRoomRequest;
import com.nanling.mahjong.api.dto.TableView;
import com.nanling.mahjong.api.dto.ViewMapper;
import com.nanling.mahjong.bot.BotBrain;
import com.nanling.mahjong.common.BusinessException;
import com.nanling.mahjong.domain.game.ActionType;
import com.nanling.mahjong.domain.game.GamePhase;
import com.nanling.mahjong.domain.game.GameResult;
import com.nanling.mahjong.domain.rule.RuleConfig;
import com.nanling.mahjong.domain.rule.WildcardMode;
import com.nanling.mahjong.domain.tile.Tiles;
import com.nanling.mahjong.engine.GameTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 房间与对局编排。
 *
 * <p>所有写操作都在房间对象锁内完成，避免同一桌并发请求互相覆盖。</p>
 */
@Service
public class RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    /** 单个房间的观战人数上限，防止一个房间号被拿来当直播间刷爆内存 */
    private static final int MAX_SPECTATORS = 20;
    /** 机器人昵称，按座位号取，方便一眼分清谁是谁 */
    private static final String[] BOT_NAMES = {"阿发", "小筒", "老万", "幺鸡"};

    private final RoomRepository repository;
    private final ViewMapper viewMapper;
    private final ApplicationEventPublisher publisher;

    /** 内存版玩家档案，数据库接入后由 user 表替代 */
    private final Map<String, PlayerProfile> profiles = new ConcurrentHashMap<>();

    private final int baseScore;
    private final int maxRounds;
    private final int actionTimeoutSeconds;
    private final int turnTimeoutSeconds;
    private final int idleTimeoutSeconds;
    /** 机器人每张牌之间的「思考时间」，决定画面上出牌的节奏 */
    private final int botThinkMs;
    /** 机器人碰牌概率，0 表示只胡不碰 */
    private final double botPongChance;

    public RoomService(RoomRepository repository,
                       ViewMapper viewMapper,
                       ApplicationEventPublisher publisher,
                       @Value("${mahjong.game.base-score:1}") int baseScore,
                       @Value("${mahjong.game.max-rounds:8}") int maxRounds,
                       @Value("${mahjong.game.action-timeout-seconds:15}") int actionTimeoutSeconds,
                       @Value("${mahjong.game.turn-timeout-seconds:20}") int turnTimeoutSeconds,
                       @Value("${mahjong.room.idle-timeout-seconds:1800}") int idleTimeoutSeconds,
                       @Value("${mahjong.bot.think-ms:1100}") int botThinkMs,
                       @Value("${mahjong.bot.pong-chance:0.5}") double botPongChance) {
        this.repository = repository;
        this.viewMapper = viewMapper;
        this.publisher = publisher;
        this.baseScore = baseScore;
        this.maxRounds = maxRounds;
        this.actionTimeoutSeconds = actionTimeoutSeconds;
        this.turnTimeoutSeconds = turnTimeoutSeconds;
        this.idleTimeoutSeconds = idleTimeoutSeconds;
        this.botThinkMs = botThinkMs;
        this.botPongChance = botPongChance;
    }

    // ------------------------------------------------------------------
    // 玩家
    // ------------------------------------------------------------------

    public PlayerProfile registerPlayer(String playerId, String nickname, String avatar) {
        String id = (playerId == null || playerId.isBlank())
                ? UUID.randomUUID().toString().replace("-", "")
                : playerId;
        String name = (nickname == null || nickname.isBlank())
                ? "玩家" + id.substring(0, Math.min(4, id.length()))
                : nickname.trim();
        PlayerProfile profile = new PlayerProfile(id, name, avatar == null ? "" : avatar);
        profiles.put(id, profile);
        return profile;
    }

    public PlayerProfile findProfile(String playerId) {
        return profiles.get(playerId);
    }

    // ------------------------------------------------------------------
    // 房间
    // ------------------------------------------------------------------

    public Room createRoom(String playerId, String nickname, String avatar, CreateRoomRequest request) {
        PlayerProfile profile = registerPlayer(playerId, nickname, avatar);
        RuleConfig config = new RuleConfig();
        config.setBaseScore(baseScore);
        config.setMaxRounds(maxRounds);
        config.setActionTimeoutSeconds(actionTimeoutSeconds);
        config.setTurnTimeoutSeconds(turnTimeoutSeconds);
        applyRequest(config, request);

        Room room = new Room(generateRoomId(), profile.id(), config);
        Player owner = new Player(profile.id(), profile.nickname(), profile.avatar(), 0);
        owner.setOnline(true);
        room.getPlayers().add(owner);
        repository.save(room);
        publish(room.getId(), "created");
        return room;
    }

    private void applyRequest(RuleConfig config, CreateRoomRequest request) {
        if (request == null) {
            return;
        }
        if (request.baseScore() != null && request.baseScore() > 0) {
            config.setBaseScore(request.baseScore());
        }
        if (request.maxRounds() != null && request.maxRounds() > 0) {
            config.setMaxRounds(request.maxRounds());
        }
        if (request.wildcardMode() != null && !request.wildcardMode().isBlank()) {
            config.setWildcardMode(parseWildcardMode(request.wildcardMode()));
        }
        if (request.wildcardTile() != null && !request.wildcardTile().isBlank()) {
            config.setFixedWildcardTile(Tiles.fromCode(request.wildcardTile()));
            config.setWildcardMode(WildcardMode.FIXED);
        }
        if (request.requireFlavorForJokerHand() != null) {
            config.setRequireFlavorForJokerHand(request.requireFlavorForJokerHand());
        }
        if (request.voidWildcardOnDiscard() != null) {
            config.setVoidWildcardOnDiscard(request.voidWildcardOnDiscard());
        }
    }

    private WildcardMode parseWildcardMode(String value) {
        try {
            return WildcardMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("不支持的癞子模式: " + value);
        }
    }

    public Room joinRoom(String roomId, String playerId, String nickname, String avatar) {
        Room room = requireRoom(roomId);
        PlayerProfile profile = registerPlayer(playerId, nickname, avatar);
        synchronized (room) {
            Player existing = room.findPlayer(profile.id()).orElse(null);
            if (existing != null) {
                existing.setOnline(true);
                existing.setNickname(profile.nickname());
                repository.save(room);
            } else if (room.getStatus() != RoomStatus.WAITING || room.isFull()) {
                // 满员 / 已开局：不再直接报错，转为观战。
                // 这是「用房间号看别人打牌」的唯一入口，和坐下打牌共用同一个房间号。
                if (room.findSpectator(profile.id()).isEmpty()
                        && room.getSpectators().size() >= MAX_SPECTATORS) {
                    throw new BusinessException("观战人数已满");
                }
                room.addSpectator(profile.id(), profile.nickname(), profile.avatar());
                repository.save(room);
            } else {
                int seat = room.nextFreeSeat();
                if (seat < 0) {
                    throw new BusinessException("没有空座位");
                }
                Player player = new Player(profile.id(), profile.nickname(), profile.avatar(), seat);
                player.setOnline(true);
                room.getPlayers().add(player);
                room.touch();
                repository.save(room);
            }
        }
        publish(roomId, "joined");
        return room;
    }

    /**
     * 主动观战：不管房间满没满、开没开局，都只以观战身份进入。
     *
     * <p>如果这个人本来就坐在座位上，直接返回原房间（不降级成观战）。</p>
     */
    public Room watchRoom(String roomId, String playerId, String nickname, String avatar) {
        Room room = requireRoom(roomId);
        PlayerProfile profile = registerPlayer(playerId, nickname, avatar);
        synchronized (room) {
            if (room.findPlayer(profile.id()).isPresent()) {
                room.findPlayer(profile.id()).ifPresent(p -> p.setOnline(true));
                repository.save(room);
            } else {
                if (room.findSpectator(profile.id()).isEmpty()
                        && room.getSpectators().size() >= MAX_SPECTATORS) {
                    throw new BusinessException("观战人数已满");
                }
                room.addSpectator(profile.id(), profile.nickname(), profile.avatar());
                repository.save(room);
            }
        }
        publish(roomId, "watched");
        return room;
    }

    public Room leaveRoom(String roomId, String playerId) {
        Room room = requireRoom(roomId);
        synchronized (room) {
            Player player = room.findPlayer(playerId).orElse(null);
            if (player == null) {
                // 观战者退出：随时可以，不受对局状态限制
                if (room.findSpectator(playerId).isEmpty()) {
                    throw new BusinessException("你不在这个房间里");
                }
                room.removeSpectator(playerId);
                if (room.getPlayers().isEmpty()) {
                    repository.delete(roomId);
                } else {
                    repository.save(room);
                }
                publish(roomId, "left");
                return room;
            }
            if (room.getStatus() == RoomStatus.PLAYING) {
                throw new BusinessException("对局进行中，不能退出房间");
            }
            room.getPlayers().remove(player);
            if (!room.hasHumanPlayer()) {
                // 真人都走了，剩一堆机器人没有意义，直接回收房间
                repository.delete(roomId);
                return room;
            }
            if (room.getOwnerId().equals(playerId)) {
                Player next = room.sortedPlayers().stream()
                        .filter(candidate -> !candidate.isBot())
                        .findFirst()
                        .orElseThrow(() -> new BusinessException("没有可移交房主的玩家"));
                room.transferOwner(next.getId());
            }
            room.touch();
            repository.save(room);
        }
        publish(roomId, "left");
        return room;
    }

    public Room setReady(String roomId, String playerId, boolean ready) {
        Room room = requireRoom(roomId);
        synchronized (room) {
            if (room.getStatus() != RoomStatus.WAITING) {
                throw new BusinessException("对局已开始，不能修改准备状态");
            }
            Player player = requirePlayer(room, playerId);
            player.setReady(ready);
            player.setOnline(true);
            room.touch();
            repository.save(room);
        }
        publish(roomId, "ready");
        return room;
    }

    /**
     * 房间内切换自制规则「打出癞子，全场作废」。
     *
     * <p>只有房主可以改，且必须在开局前（{@link RoomStatus#WAITING}）。
     * 改完会广播 {@code ROOM_STATE}，房间内所有人都能看到开关状态。</p>
     */
    public Room setVoidWildcardOnDiscard(String roomId, String playerId, boolean enabled) {
        Room room = requireRoom(roomId);
        synchronized (room) {
            if (!room.getOwnerId().equals(playerId)) {
                throw new BusinessException("只有房主可以修改规则");
            }
            if (room.getStatus() != RoomStatus.WAITING) {
                throw new BusinessException("对局已开始，不能修改规则");
            }
            room.getRuleConfig().setVoidWildcardOnDiscard(enabled);
            room.touch();
            repository.save(room);
        }
        publish(roomId, "rule");
        return room;
    }

    /**
     * 房主往空座位上补机器人。
     *
     * <p>机器人始终「在线 + 已准备」，所以一个人也能凑齐 4 人开局；
     * 它们由 {@link #tickBots} 逐个代打，节奏和人手一样，不会一瞬间把牌全打光。</p>
     *
     * @param count 想补几个，超过空位数时按空位补齐
     */
    public Room addBots(String roomId, String playerId, int count) {
        Room room = requireRoom(roomId);
        synchronized (room) {
            if (!room.getOwnerId().equals(playerId)) {
                throw new BusinessException("只有房主可以添加机器人");
            }
            if (room.getStatus() != RoomStatus.WAITING) {
                throw new BusinessException("对局已开始，不能再添加机器人");
            }
            int wanted = Math.min(Math.max(count, 1), 4);
            int added = 0;
            for (int i = 0; i < wanted; i++) {
                int seat = room.nextFreeSeat();
                if (seat < 0) {
                    break;
                }
                room.getPlayers().add(Player.bot(
                        "bot-" + room.getId() + "-" + seat, BOT_NAMES[seat % BOT_NAMES.length], seat));
                added++;
            }
            if (added == 0) {
                throw new BusinessException("座位已满，不需要再加机器人");
            }
            room.touch();
            repository.save(room);
        }
        publish(roomId, "bot");
        return room;
    }

    public Room startGame(String roomId, String playerId) {
        Room room = requireRoom(roomId);
        synchronized (room) {
            if (!room.getOwnerId().equals(playerId)) {
                throw new BusinessException("只有房主可以开始游戏");
            }
            if (room.getStatus() == RoomStatus.PLAYING) {
                throw new BusinessException("对局已经开始");
            }
            if (room.getPlayers().size() < 4) {
                throw new BusinessException("需要 4 名玩家才能开始");
            }
            if (!room.allReady()) {
                throw new BusinessException("还有玩家未准备");
            }
            room.setStatus(RoomStatus.PLAYING);
            room.setRound(1);
            room.setSettledRound(0);
            room.setTable(GameTable.start(room.getRuleConfig(), System.nanoTime(), 0));
            room.setBotActAt(System.currentTimeMillis() + botThinkMs);
            room.touch();
            repository.save(room);
        }
        publish(roomId, "started");
        return room;
    }

    public Room action(String roomId, String playerId, ActionType type, Integer tile) {
        Room room = requireRoom(roomId);
        synchronized (room) {
            if (room.getStatus() != RoomStatus.PLAYING) {
                throw new BusinessException("当前不在对局中");
            }
            GameTable table = room.getTable();
            if (table == null) {
                throw new BusinessException("牌桌尚未初始化");
            }
            if (table.getPhase() == GamePhase.ENDED) {
                throw new BusinessException("本局已结束");
            }
            Player player = requirePlayer(room, playerId);
            int seat = player.getSeat();
            switch (type) {
                case DISCARD -> {
                    requireTile(tile);
                    table.discard(seat, tile);
                }
                case PONG, KONG_MING -> table.claim(seat, type);
                case KONG_AN -> {
                    requireTile(tile);
                    table.selfKongAn(seat, tile);
                }
                case KONG_BU -> {
                    requireTile(tile);
                    table.selfKongBu(seat, tile);
                }
                case HU -> {
                    if (table.getPhase() == GamePhase.WAITING_CLAIM
                            && table.getPendingSeats().contains(seat)) {
                        table.claim(seat, ActionType.HU);
                    } else {
                        table.selfHu(seat);
                    }
                }
                case PASS -> table.pass(seat);
                default -> throw new BusinessException("不支持的操作: " + type);
            }
            if (table.getPhase() == GamePhase.ENDED) {
                settle(room);
            }
            // 真人每次操作后，机器人都要「想一想」再动，避免人手一打完机器人立刻抢着出牌
            room.setBotActAt(System.currentTimeMillis() + botThinkMs);
            room.touch();
            repository.save(room);
        }
        publish(roomId, "action");
        return room;
    }

    public Room nextRound(String roomId, String playerId) {
        Room room = requireRoom(roomId);
        synchronized (room) {
            if (!room.getOwnerId().equals(playerId)) {
                throw new BusinessException("只有房主可以开始下一局");
            }
            GameTable table = room.getTable();
            if (table == null || table.getPhase() != GamePhase.ENDED) {
                throw new BusinessException("本局还没有结束");
            }
            int next = room.getRound() + 1;
            if (next > room.getRuleConfig().getMaxRounds()) {
                room.setStatus(RoomStatus.FINISHED);
                room.touch();
                repository.save(room);
                publish(roomId, "finished");
                return room;
            }
            GameResult result = table.getResult();
            int dealer = (result != null && !result.drawGame()) ? result.winnerSeat() : table.getDealerSeat();
            room.setRound(next);
            room.setTable(GameTable.start(room.getRuleConfig(), System.nanoTime(), dealer));
            room.setStatus(RoomStatus.PLAYING);
            room.setBotActAt(System.currentTimeMillis() + botThinkMs);
            room.touch();
            repository.save(room);
        }
        publish(roomId, "next-round");
        return room;
    }

    // ------------------------------------------------------------------
    // 结算
    // ------------------------------------------------------------------

    private void settle(Room room) {
        if (room.getSettledRound() == room.getRound()) {
            return;
        }
        GameTable table = room.getTable();
        GameResult result = table == null ? null : table.getResult();
        if (result == null) {
            return;
        }
        if (!result.drawGame()) {
            Player winner = room.findBySeat(result.winnerSeat())
                    .orElseThrow(() -> new BusinessException("结算失败：找不到胡牌玩家"));
            int score = result.score();
            if (result.loserSeat() >= 0) {
                Player loser = room.findBySeat(result.loserSeat())
                        .orElseThrow(() -> new BusinessException("结算失败：找不到点炮玩家"));
                winner.addScore(score);
                loser.addScore(-score);
            } else {
                for (Player player : room.getPlayers()) {
                    if (player.getSeat() != winner.getSeat()) {
                        player.addScore(-score);
                        winner.addScore(score);
                    }
                }
            }
        }
        room.setSettledRound(room.getRound());
        if (room.getRound() >= room.getRuleConfig().getMaxRounds()) {
            room.setStatus(RoomStatus.FINISHED);
        }
    }

    // ------------------------------------------------------------------
    // 查询 / 连接状态
    // ------------------------------------------------------------------

    /**
     * 生成某个连接者视角的牌桌视图。
     *
     * <p>加房间锁：视图要读手牌、副业、观战者列表，而广播发生在写操作释放锁之后，
     * 不加锁就会读到「一半的」房间状态（甚至 ConcurrentModificationException）。</p>
     */
    public TableView viewFor(String roomId, String playerId) {
        Room room = requireRoom(roomId);
        synchronized (room) {
            return viewMapper.toView(room, playerId);
        }
    }

    public Room requireRoom(String roomId) {
        return repository.findById(roomId)
                .orElseThrow(() -> new BusinessException(4040, "房间不存在或已解散"));
    }

    public Room getRoomOrNull(String roomId) {
        return repository.findById(roomId).orElse(null);
    }

    public List<Room> listWaitingRooms() {
        return repository.findAll().stream()
                .filter(room -> room.getStatus() == RoomStatus.WAITING)
                .toList();
    }

    public Room markOnline(String roomId, String playerId, String sessionId, boolean online) {
        Room room = repository.findById(roomId).orElse(null);
        if (room == null) {
            return null;
        }
        synchronized (room) {
            room.findPlayer(playerId).ifPresent(player -> {
                player.setOnline(online);
                player.setSessionId(sessionId);
                room.touch();
                repository.save(room);
            });
            room.findSpectator(playerId).ifPresent(spectator -> {
                spectator.setSessionId(sessionId);
                room.touch();
                repository.save(room);
            });
        }
        publish(roomId, online ? "online" : "offline");
        return room;
    }

    public Room findBySession(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        for (Room room : repository.findAll()) {
            if (room.findBySession(sessionId).isPresent()
                    || room.findSpectatorBySession(sessionId).isPresent()) {
                return room;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 定时任务：超时自动出牌 / 自动放弃
    // ------------------------------------------------------------------

    @Scheduled(fixedDelay = 300)
    public void tick() {
        long now = System.currentTimeMillis();
        for (Room room : repository.findAll()) {
            try {
                tickRoom(room, now);
            } catch (Exception ex) {
                log.warn("room tick failed, roomId={}", room.getId(), ex);
            }
        }
    }

    private void tickRoom(Room room, long now) {
        if (room.getStatus() == RoomStatus.PLAYING && room.getTable() != null
                && room.getTable().getPhase() != GamePhase.ENDED) {
            if (now > room.getTable().getDeadlineAt()) {
                boolean changed = false;
                synchronized (room) {
                    GameTable table = room.getTable();
                    if (table.getPhase() != GamePhase.ENDED && now > table.getDeadlineAt()) {
                        table.onTimeout();
                        if (table.getPhase() == GamePhase.ENDED) {
                            settle(room);
                        }
                        room.setBotActAt(now + botThinkMs);
                        room.touch();
                        repository.save(room);
                        changed = true;
                    }
                }
                if (changed) {
                    publish(room.getId(), "timeout");
                }
                return;
            }
            tickBots(room, now);
            return;
        }
        boolean idle = now - room.getUpdatedAt() > idleTimeoutSeconds * 1000L;
        if (idle && room.getStatus() == RoomStatus.WAITING) {
            repository.delete(room.getId());
        }
    }

    /**
     * 机器人代打：每次 tick 最多让「一个」机器人做「一个」动作。
     *
     * <p>这是「打牌要依次来」的核心：座位是轮流坐庄的，一个 tick 只处理当前该动的那一位，
     * 动作完再把 {@code botActAt} 推到 {@code now + think-ms}，下一个 tick 还没到点就直接返回。
     * 于是画面上看到的是——上家出牌 → 停一下 → 下家摸打 → 停一下，而不是一帧内全部打完。</p>
     */
    private void tickBots(Room room, long now) {
        if (now < room.getBotActAt()) {
            return;
        }
        GameTable snapshot = room.getTable();
        int seat = nextBotSeat(room, snapshot);
        if (seat < 0) {
            return;
        }
        boolean changed = false;
        synchronized (room) {
            GameTable table = room.getTable();
            if (table == null || table.getPhase() == GamePhase.ENDED) {
                return;
            }
            if (nextBotSeat(room, table) != seat) {
                // 期间被真人抢先操作了（碰/胡/出牌），本 tick 放弃
                return;
            }
            try {
                actForBot(table, seat);
            } catch (BusinessException ex) {
                log.warn("bot action failed, roomId={}, seat={}, msg={}", room.getId(), seat, ex.getMessage());
                fallbackForBot(table, seat);
            }
            room.setBotActAt(now + botThinkMs);
            if (table.getPhase() == GamePhase.ENDED) {
                settle(room);
            }
            room.touch();
            repository.save(room);
            changed = true;
        }
        if (changed) {
            publish(room.getId(), "bot");
        }
    }

    /** 当前轮到谁「该机器人动」，没有就返回 -1。 */
    private int nextBotSeat(Room room, GameTable table) {
        if (table == null) {
            return -1;
        }
        if (table.getPhase() == GamePhase.PLAYER_TURN) {
            int seat = table.getCurrentSeat();
            return room.isBotSeat(seat) ? seat : -1;
        }
        if (table.getPhase() == GamePhase.WAITING_CLAIM) {
            for (int seat : table.getPendingSeats()) {
                if (table.getPendingClaims().containsKey(seat)) {
                    continue;
                }
                if (room.isBotSeat(seat)) {
                    return seat;
                }
            }
        }
        return -1;
    }

    private void actForBot(GameTable table, int seat) {
        if (table.getPhase() == GamePhase.WAITING_CLAIM) {
            table.claim(seat, BotBrain.chooseClaim(table.claimsFor(seat), botPongChance));
            return;
        }
        if (table.selfActions(seat).contains(ActionType.HU)) {
            table.selfHu(seat);
            return;
        }
        table.discard(seat, BotBrain.chooseDiscard(table, seat));
    }

    /** 决策异常时的兜底，避免整桌卡死。 */
    private void fallbackForBot(GameTable table, int seat) {
        if (table.getPhase() == GamePhase.WAITING_CLAIM) {
            table.pass(seat);
            return;
        }
        List<Integer> hand = table.getHand(seat);
        if (!hand.isEmpty() && hand.size() % 3 == 2) {
            table.discard(seat, hand.get(hand.size() - 1));
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private Player requirePlayer(Room room, String playerId) {
        if (room.isSpectator(playerId)) {
            throw new BusinessException("观战中不能进行牌局操作");
        }
        return room.findPlayer(playerId)
                .orElseThrow(() -> new BusinessException("你不在这个房间里"));
    }

    private void requireTile(Integer tile) {
        if (tile == null) {
            throw new BusinessException("缺少牌参数");
        }
        Tiles.check(tile);
    }

    private String generateRoomId() {
        for (int i = 0; i < 50; i++) {
            int value = 100000 + RANDOM.nextInt(900000);
            String id = String.valueOf(value);
            if (!repository.exists(id)) {
                return id;
            }
        }
        throw new BusinessException(5001, "房间号分配失败，请重试");
    }

    private void publish(String roomId, String reason) {
        publisher.publishEvent(new RoomUpdatedEvent(roomId, reason));
    }
}
