package com.nanling.mahjong.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;

/**
 * 发送工具：WebSocketSession 不是线程安全的，发送时按 session 加锁。
 */
public final class WsSender {

    private WsSender() {
    }

    public static void send(WebSocketSession session, ObjectMapper mapper, String type, Object data)
            throws IOException {
        if (session == null || !session.isOpen()) {
            return;
        }
        Map<String, Object> envelope = Map.of("type", type, "data", data == null ? Map.of() : data);
        String payload = mapper.writeValueAsString(envelope);
        synchronized (session) {
            session.sendMessage(new TextMessage(payload));
        }
    }
}
