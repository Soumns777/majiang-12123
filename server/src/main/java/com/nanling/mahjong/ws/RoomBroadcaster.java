package com.nanling.mahjong.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanling.mahjong.room.Player;
import com.nanling.mahjong.room.Room;
import com.nanling.mahjong.room.RoomService;
import com.nanling.mahjong.room.RoomUpdatedEvent;
import com.nanling.mahjong.room.Spectator;
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
        // 先取快照再推送：广播在房间锁之外执行，直接遍历会和并发的加入/退出撞车
        for (Player player : room.sortedPlayers()) {
            push(room, player.getId(), player.getSessionId());
        }
        // 观战者也要跟着刷：他们看到的是一份「所有人手牌都隐藏」的视图
        for (Spectator spectator : room.spectatorSnapshot()) {
            push(room, spectator.getId(), spectator.getSessionId());
        }
    }

    private void push(Room room, String viewerId, String sessionId) {
        WebSocketSession session = registry.get(sessionId);
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            WsSender.send(session, objectMapper, WsType.ROOM_STATE.name(),
                    roomService.viewFor(room.getId(), viewerId));
        } catch (Exception ex) {
            log.warn("push room state failed, room={}, viewer={}", room.getId(), viewerId, ex);
        }
    }
}
