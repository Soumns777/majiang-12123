package com.nanling.mahjong.room;

/**
 * 房间内的观战者。
 *
 * <p>刻意不复用 {@link Player}：观战者没有座位、没有准备态、不参与结算，
 * 一旦混进 {@code players} 列表，{@code isFull()}、{@code allReady()}、
 * {@code nextFreeSeat()} 这些按座位算的逻辑就全都要加特判。</p>
 */
public class Spectator {

    private final String id;
    private String nickname;
    private String avatar;
    private String sessionId;

    public Spectator(String id, String nickname, String avatar) {
        this.id = id;
        this.nickname = nickname;
        this.avatar = avatar;
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

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
