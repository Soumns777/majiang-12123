package com.nanling.mahjong.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话表。sessionId → 连接。
 */
@Component
public class WsSessionRegistry {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void add(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    public WebSocketSession get(String sessionId) {
        return sessionId == null ? null : sessions.get(sessionId);
    }

    public Collection<WebSocketSession> all() {
        return sessions.values();
    }

    public int size() {
        return sessions.size();
    }
}
