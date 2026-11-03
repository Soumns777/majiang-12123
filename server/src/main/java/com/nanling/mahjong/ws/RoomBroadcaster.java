package com.nanling.mahjong.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanling.mahjong.room.Player;
import com.nanling.mahjong.room.Room;
import com.nanling.mahjong.room.RoomService;
import com.nanling.mahjong.room.RoomUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * 房间状态变更后，向房间内每个在线玩家推送“他自己的”视图。
 */
@Component
public class RoomBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(RoomBroadcaster.class);

    private final RoomService roomService;
    private final WsSessionRegistry registry;
    private final ObjectMapper objectMapper;

    public RoomBroadcaster(RoomService roomService, WsSessionRegistry registry, ObjectMapper objectMapper) {
        this.roomService = roomService;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void onRoomUpdated(RoomUpdatedEvent event) {
        Room room = roomService.getRoomOrNull(event.roomId());
        if (room == null) {
            return;
        }
        for (Player player : room.sortedPlayers()) {
            WebSocketSession session = registry.get(player.getSessionId());
            if (session == null || !session.isOpen()) {
                continue;
            }
            try {
                WsSender.send(session, objectMapper, WsType.ROOM_STATE.name(),
                        roomService.viewFor(room.getId(), player.getId()));
            } catch (Exception ex) {
                log.warn("push room state failed, room={}, player={}", room.getId(), player.getId(), ex);
            }
        }
    }
}
