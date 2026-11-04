package com.nanling.mahjong.room;

/**
 * 房间内的玩家。
 */
public class Player {

    private final String id;
    private String nickname;
    private String avatar;
    private int seat;
    private boolean ready;
    private boolean online;
    private int score;
    private String sessionId;
    /** 机器人：由服务端定时器代打，没有 WebSocket 会话 */
    private boolean bot;

    public Player(String id, String nickname, String avatar, int seat) {
        this.id = id;
        this.nickname = nickname;
        this.avatar = avatar;
        this.seat = seat;
    }

    /**
     * 造一个机器人玩家。
     *
     * <p>机器人始终「在线 + 已准备」，这样房主一个人也能凑齐 4 人开局；
     * 它的 sessionId 恒为 null，广播时会被跳过。</p>
     */
    public static Player bot(String id, String nickname, int seat) {
        Player player = new Player(id, nickname, "🤖", seat);
        player.bot = true;
        player.ready = true;
        player.online = true;
        return player;
    }

    public String getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public int getSeat() {
        return seat;
    }

    public void setSeat(int seat) {
        this.seat = seat;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public void addScore(int delta) {
        this.score += delta;
    }

    public boolean isBot() {
        return bot;
    }

    public void setBot(boolean bot) {
        this.bot = bot;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
