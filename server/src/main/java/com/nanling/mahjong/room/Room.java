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
    private final long createdAt;
    private RoomStatus status = RoomStatus.WAITING;
    private GameTable table;
    private int round;
    private int settledRound;
    private long updatedAt;
    private long version;

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

    public List<Player> sortedPlayers() {
        return players.stream().sorted(Comparator.comparingInt(Player::getSeat)).toList();
    }

    public Player getOwner() {
        return findPlayer(ownerId).orElse(null);
    }
}
