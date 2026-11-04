package com.nanling.mahjong.room;

import com.nanling.mahjong.domain.rule.RuleConfig;
import com.nanling.mahjong.engine.GameTable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 一个房间（4 人桌）。
 *
 * <p>数据库接入后，这里会变成 RoomEntity + RoomPlayerEntity 的聚合根，
 * 目前所有状态放在内存里。</p>
 */
public class Room {

    private final String id;
    private String ownerId;
    private final RuleConfig ruleConfig;
    private final List<Player> players = new ArrayList<>();
    /** 观战者：不占座位、不参与结算，只读牌桌 */
    private final List<Spectator> spectators = new ArrayList<>();
    private final long createdAt;
    private RoomStatus status = RoomStatus.WAITING;
    private GameTable table;
    private int round;
    private int settledRound;
    private long updatedAt;
    private long version;
    /**
     * 下一个机器人动作的最早时间戳（毫秒）。
     *
     * <p>机器人不是「一瞬间把牌全打出去」，而是每次动作后把时间推到未来
     * {@code mahjong.bot.think-ms} 之后，由定时器逐个唤醒——这样画面上就是
     * 上家打完、下家才动，节奏和人手一致。</p>
     */
    private long botActAt;

    public Room(String id, String ownerId, RuleConfig ruleConfig) {
        this.id = id;
        this.ownerId = ownerId;
        this.ruleConfig = ruleConfig;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    public String getId() {
        return id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    /** 房主退出时把房主身份交给下一位玩家。 */
    public void transferOwner(String newOwnerId) {
        this.ownerId = newOwnerId;
        touch();
    }

    public RuleConfig getRuleConfig() {
        return ruleConfig;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
        touch();
    }

    public GameTable getTable() {
        return table;
    }

    public void setTable(GameTable table) {
        this.table = table;
        touch();
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
        touch();
    }

    public int getSettledRound() {
        return settledRound;
    }

    public void setSettledRound(int settledRound) {
        this.settledRound = settledRound;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public long getBotActAt() {
        return botActAt;
    }

    public void setBotActAt(long botActAt) {
        this.botActAt = botActAt;
    }

    /** 房间内是否还有真人（全是机器人时房间可以回收）。 */
    public boolean hasHumanPlayer() {
        synchronized (this) {
            return players.stream().anyMatch(player -> !player.isBot());
        }
    }

    /** 座位上的玩家是不是机器人。加锁：tick 线程读 players 时可能有玩家正在加入。 */
    public boolean isBotSeat(int seat) {
        synchronized (this) {
            return findBySeat(seat).map(Player::isBot).orElse(false);
        }
    }

    public void touch() {
        this.updatedAt = System.currentTimeMillis();
        this.version++;
    }

    public Optional<Player> findPlayer(String playerId) {
        return players.stream().filter(p -> p.getId().equals(playerId)).findFirst();
    }

    public Optional<Player> findBySession(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        return players.stream().filter(p -> sessionId.equals(p.getSessionId())).findFirst();
    }

    public Optional<Player> findBySeat(int seat) {
        return players.stream().filter(p -> p.getSeat() == seat).findFirst();
    }

    public boolean isFull() {
        return players.size() >= 4;
    }

    // ------------------------------------------------------------------
    // 观战
    // ------------------------------------------------------------------

    public List<Spectator> getSpectators() {
        return spectators;
    }

    public Optional<Spectator> findSpectator(String playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        return spectators.stream().filter(s -> s.getId().equals(playerId)).findFirst();
    }

    public Optional<Spectator> findSpectatorBySession(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        return spectators.stream().filter(s -> sessionId.equals(s.getSessionId())).findFirst();
    }

    /** 加入观战；已在观战列表里则只更新昵称/头像。 */
    public Spectator addSpectator(String playerId, String nickname, String avatar) {
        Spectator spectator = findSpectator(playerId).orElse(null);
        if (spectator == null) {
            spectator = new Spectator(playerId, nickname, avatar);
            spectators.add(spectator);
        } else {
            spectator.setNickname(nickname);
            spectator.setAvatar(avatar);
        }
        touch();
        return spectator;
    }

    public void removeSpectator(String playerId) {
        spectators.removeIf(s -> s.getId().equals(playerId));
        touch();
    }

    public boolean isSpectator(String playerId) {
        return findSpectator(playerId).isPresent();
    }

    /** 观战者昵称列表，用于牌桌上显示「观战 N 人」。 */
    public List<String> spectatorNames() {
        synchronized (this) {
            return spectators.stream().map(Spectator::getNickname).toList();
        }
    }

    public boolean allReady() {
        return players.size() == 4 && players.stream().allMatch(Player::isReady);
    }

    /** 下一个可用座位（0-3）。 */
    public int nextFreeSeat() {
        for (int seat = 0; seat < 4; seat++) {
            int candidate = seat;
            boolean used = players.stream().anyMatch(p -> p.getSeat() == candidate);
            if (!used) {
                return seat;
            }
        }
        return -1;
    }

    /**
     * 按座位号排序的玩家快照。
     *
     * <p>加锁 + 返回副本：广播是在 {@code synchronized(room)} 之外跑的，
     * 直接流式遍历 {@code players} 会和正在加入的玩家撞出 ConcurrentModificationException。</p>
     */
    public List<Player> sortedPlayers() {
        synchronized (this) {
            return players.stream().sorted(Comparator.comparingInt(Player::getSeat)).toList();
        }
    }

    /** 观战者快照，理由同上。 */
    public List<Spectator> spectatorSnapshot() {
        synchronized (this) {
            return List.copyOf(spectators);
        }
    }

    public Player getOwner() {
        return findPlayer(ownerId).orElse(null);
    }
}
