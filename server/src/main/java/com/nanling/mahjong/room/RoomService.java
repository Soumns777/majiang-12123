package com.nanling.mahjong.room;

import com.nanling.mahjong.api.dto.CreateRoomRequest;
import com.nanling.mahjong.api.dto.TableView;
import com.nanling.mahjong.api.dto.ViewMapper;
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

    public RoomService(RoomRepository repository,
                       ViewMapper viewMapper,
                       ApplicationEventPublisher publisher,
                       @Value("${mahjong.game.base-score:1}") int baseScore,
                       @Value("${mahjong.game.max-rounds:8}") int maxRounds,
                       @Value("${mahjong.game.action-timeout-seconds:15}") int actionTimeoutSeconds,
                       @Value("${mahjong.game.turn-timeout-seconds:20}") int turnTimeoutSeconds,
                       @Value("${mahjong.room.idle-timeout-seconds:1800}") int idleTimeoutSeconds) {
        this.repository = repository;
        this.viewMapper = viewMapper;
        this.publisher = publisher;
        this.baseScore = baseScore;
        this.maxRounds = maxRounds;
        this.actionTimeoutSeconds = actionTimeoutSeconds;
        this.turnTimeoutSeconds = turnTimeoutSeconds;
        this.idleTimeoutSeconds = idleTimeoutSeconds;
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
            } else {
                if (room.getStatus() != RoomStatus.WAITING) {
                    throw new BusinessException("对局已开始，无法加入");
                }
                if (room.isFull()) {
                    throw new BusinessException("房间已满");
                }
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

    public Room leaveRoom(String roomId, String playerId) {
        Room room = requireRoom(roomId);
        synchronized (room) {
            Player player = room.findPlayer(playerId)
                    .orElseThrow(() -> new BusinessException("你不在这个房间里"));
            if (room.getStatus() == RoomStatus.PLAYING) {
                throw new BusinessException("对局进行中，不能退出房间");
            }
            room.getPlayers().remove(player);
            if (room.getPlayers().isEmpty()) {
                repository.delete(roomId);
                return room;
            }
            if (room.getOwnerId().equals(playerId)) {
                Player next = room.sortedPlayers().get(0);
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

    public TableView viewFor(String roomId, String playerId) {
        Room room = requireRoom(roomId);
        return viewMapper.toView(room, playerId);
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
        }
        publish(roomId, online ? "online" : "offline");
        return room;
    }

    public Room findBySession(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        for (Room room : repository.findAll()) {
            if (room.findBySession(sessionId).isPresent()) {
                return room;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 定时任务：超时自动出牌 / 自动放弃
    // ------------------------------------------------------------------

    @Scheduled(fixedDelay = 1000)
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
                && room.getTable().getPhase() != GamePhase.ENDED
                && now > room.getTable().getDeadlineAt()) {
            boolean changed = false;
            synchronized (room) {
                GameTable table = room.getTable();
                if (table.getPhase() != GamePhase.ENDED && now > table.getDeadlineAt()) {
                    table.onTimeout();
                    if (table.getPhase() == GamePhase.ENDED) {
                        settle(room);
                    }
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
        boolean idle = now - room.getUpdatedAt() > idleTimeoutSeconds * 1000L;
        if (idle && room.getStatus() == RoomStatus.WAITING) {
            repository.delete(room.getId());
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private Player requirePlayer(Room room, String playerId) {
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
