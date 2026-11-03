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

    public Player(String id, String nickname, String avatar, int seat) {
        this.id = id;
        this.nickname = nickname;
        this.avatar = avatar;
        this.seat = seat;
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

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
