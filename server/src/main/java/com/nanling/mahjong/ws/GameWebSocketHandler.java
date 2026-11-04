package com.nanling.mahjong.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanling.mahjong.api.dto.CreateRoomRequest;
import com.nanling.mahjong.common.BusinessException;
import com.nanling.mahjong.domain.game.ActionType;
import com.nanling.mahjong.domain.tile.Tiles;
import com.nanling.mahjong.room.PlayerProfile;
import com.nanling.mahjong.room.Room;
import com.nanling.mahjong.room.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * 麻将 WebSocket 入口。
 *
 * <p>协议见 docs/03-通信协议.md。所有消息形如 {@code {"type":"...","data":{...}}}。</p>
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);
    private static final String ATTR_PLAYER_ID = "playerId";
    private static final String ATTR_ROOM_ID = "roomId";

    private final ObjectMapper objectMapper;
    private final RoomService roomService;
    private final WsSessionRegistry registry;

    public GameWebSocketHandler(ObjectMapper objectMapper, RoomService roomService, WsSessionRegistry registry) {
        this.objectMapper = objectMapper;
        this.roomService = roomService;
        this.registry = registry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        registry.add(session);
        log.info("ws connected, session={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        WsMessage wsMessage;
        try {
            wsMessage = objectMapper.readValue(message.getPayload(), WsMessage.class);
        } catch (Exception ex) {
            sendError(session, 4000, "消息格式错误");
            return;
        }
        try {
            dispatch(session, wsMessage);
        } catch (BusinessException ex) {
            sendError(session, ex.getCode(), ex.getMessage());
        } catch (IllegalArgumentException ex) {
            sendError(session, 4001, ex.getMessage());
        } catch (Exception ex) {
            log.error("handle ws message failed: {}", message.getPayload(), ex);
            sendError(session, 5000, "服务器处理失败");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.remove(session.getId());
        Room room = roomService.findBySession(session.getId());
        if (room != null) {
            room.findBySession(session.getId()).ifPresent(player ->
                    roomService.markOnline(room.getId(), player.getId(), null, false));
            room.findSpectatorBySession(session.getId()).ifPresent(spectator ->
                    roomService.markOnline(room.getId(), spectator.getId(), null, false));
        }
        log.info("ws closed, session={}, status={}", session.getId(), status);
    }

    private void dispatch(WebSocketSession session, WsMessage message) throws Exception {
        if (message.type() == null) {
            throw new BusinessException("缺少消息类型");
        }
        WsType type;
        try {
            type = WsType.valueOf(message.type().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("未知消息类型: " + message.type());
        }
        JsonNode data = message.data();

        switch (type) {
            case HELLO -> onHello(session, data);
            case ROOM_CREATE -> onRoomCreate(session, data);
            case ROOM_JOIN -> onRoomJoin(session, data);
            case ROOM_WATCH -> onRoomWatch(session, data);
            case ROOM_LEAVE -> onRoomLeave(session);
            case ROOM_READY -> onReady(session, data);
            case ROOM_RULE -> onRoomRule(session, data);
            case ROOM_BOT -> onRoomBot(session, data);
            case GAME_START -> onGameStart(session);
            case GAME_ACTION -> onGameAction(session, data);
            case GAME_EXTEND -> onGameExtend(session, data);
            case NEXT_ROUND -> onNextRound(session);
            case PING -> WsSender.send(session, objectMapper, WsType.PONG.name(), Map.of());
            default -> throw new BusinessException("该消息类型不支持客户端发送: " + type);
        }
    }

    // ------------------------------------------------------------------
    // 消息处理
    // ------------------------------------------------------------------

    private void onHello(WebSocketSession session, JsonNode data) throws Exception {
        String playerId = text(data, "playerId");
        String nickname = text(data, "nickname");
        String avatar = text(data, "avatar");
        PlayerProfile profile = roomService.registerPlayer(playerId, nickname, avatar);
        session.getAttributes().put(ATTR_PLAYER_ID, profile.id());
        session.getAttributes().put("nickname", profile.nickname());
        session.getAttributes().put("avatar", profile.avatar());
        WsSender.send(session, objectMapper, WsType.HELLO_ACK.name(), Map.of(
                "playerId", profile.id(),
                "nickname", profile.nickname(),
                "avatar", profile.avatar()
        ));
    }

    private void onRoomCreate(WebSocketSession session, JsonNode data) throws Exception {
        String playerId = requirePlayerId(session);
        CreateRoomRequest request = (data == null || data.isNull())
                ? null
                : objectMapper.convertValue(data, CreateRoomRequest.class);
        Room room = roomService.createRoom(playerId, nickname(session), avatar(session), request);
        session.getAttributes().put(ATTR_ROOM_ID, room.getId());
        roomService.markOnline(room.getId(), playerId, session.getId(), true);
        pushState(session, room.getId(), playerId);
    }

    private void onRoomJoin(WebSocketSession session, JsonNode data) throws Exception {
        String playerId = requirePlayerId(session);
        String roomId = text(data, "roomId");
        if (roomId == null || roomId.isBlank()) {
            throw new BusinessException("请输入房间号");
        }
        Room room = roomService.joinRoom(roomId, playerId, nickname(session), avatar(session));
        session.getAttributes().put(ATTR_ROOM_ID, room.getId());
        roomService.markOnline(room.getId(), playerId, session.getId(), true);
        pushState(session, room.getId(), playerId);
    }

    /**
     * 主动观战：不论房间是否满员/是否已开局，都以观战身份进入。
     *
     * <p>和 {@code ROOM_JOIN} 的区别：ROOM_JOIN 有空位就坐下，满员才降级观战；
     * ROOM_WATCH 永远不占座位。</p>
     */
    private void onRoomWatch(WebSocketSession session, JsonNode data) throws Exception {
        String playerId = requirePlayerId(session);
        String roomId = text(data, "roomId");
        if (roomId == null || roomId.isBlank()) {
            throw new BusinessException("请输入房间号");
        }
        Room room = roomService.watchRoom(roomId, playerId, nickname(session), avatar(session));
        session.getAttributes().put(ATTR_ROOM_ID, room.getId());
        roomService.markOnline(room.getId(), playerId, session.getId(), true);
        pushState(session, room.getId(), playerId);
    }

    private void onRoomLeave(WebSocketSession session) throws Exception {
        String playerId = requirePlayerId(session);
        String roomId = requireRoomId(session);
        roomService.leaveRoom(roomId, playerId);
        session.getAttributes().remove(ATTR_ROOM_ID);
        WsSender.send(session, objectMapper, WsType.LEFT.name(), Map.of("roomId", roomId));
    }

    private void onReady(WebSocketSession session, JsonNode data) throws Exception {
        String playerId = requirePlayerId(session);
        String roomId = requireRoomId(session);
        boolean ready = data == null || data.isNull() || !data.has("ready") || data.get("ready").asBoolean();
        roomService.setReady(roomId, playerId, ready);
    }

    /**
     * 房间内切换自制规则，目前只支持「打出癞子，全场作废」。
     *
     * <p>请求体：{@code {"voidWildcardOnDiscard": true}}</p>
     */
    private void onRoomRule(WebSocketSession session, JsonNode data) throws Exception {
        String playerId = requirePlayerId(session);
        String roomId = requireRoomId(session);
        if (data == null) {
            throw new BusinessException("缺少规则参数");
        }
        // 一次可以只带其中一个字段：{voidWildcardOnDiscard:true} 或 {baseScore:2}
        boolean handled = false;
        if (data.hasNonNull("voidWildcardOnDiscard")) {
            roomService.setVoidWildcardOnDiscard(roomId, playerId, data.get("voidWildcardOnDiscard").asBoolean());
            handled = true;
        }
        if (data.hasNonNull("baseScore")) {
            roomService.setBaseScore(roomId, playerId, data.get("baseScore").asInt());
            handled = true;
        }
        if (!handled) {
            throw new BusinessException("缺少规则参数");
        }
    }

    /**
     * 补机器人。请求体：{@code {"count": 3}}，不传默认补满空位。
     *
     * <p>机器人不是「一次算完全部动作」，服务端每个 tick 只让当前该动的那一位出一张牌，
     * 所以客户端看到的是正常的一轮一轮的节奏。</p>
     */
    private void onRoomBot(WebSocketSession session, JsonNode data) throws Exception {
        String playerId = requirePlayerId(session);
        String roomId = requireRoomId(session);
        int count = (data == null || !data.hasNonNull("count")) ? 3 : data.get("count").asInt(3);
        roomService.addBots(roomId, playerId, count);
    }

    private void onGameStart(WebSocketSession session) throws Exception {
        String playerId = requirePlayerId(session);
        String roomId = requireRoomId(session);
        roomService.startGame(roomId, playerId);
    }

    private void onGameAction(WebSocketSession session, JsonNode data) throws Exception {
        String playerId = requirePlayerId(session);
        String roomId = requireRoomId(session);
        String actionText = text(data, "action");
        if (actionText == null) {
            throw new BusinessException("缺少 action 参数");
        }
        ActionType action;
        try {
            action = ActionType.valueOf(actionText.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("未知操作: " + actionText);
        }
        Integer tile = null;
        String tileCode = text(data, "tile");
        if (tileCode != null && !tileCode.isBlank()) {
            tile = Tiles.fromCode(tileCode);
        }
        roomService.action(roomId, playerId, action, tile);
    }

    /**
     * 延时申请 / 投票。
     *
     * <p>请求体：{@code {}} 表示发起申请；{@code {"agree": true|false}} 表示投票。</p>
     */
    private void onGameExtend(WebSocketSession session, JsonNode data) throws Exception {
        String playerId = requirePlayerId(session);
        String roomId = requireRoomId(session);
        if (data == null || data.isNull() || !data.hasNonNull("agree")) {
            roomService.requestExtension(roomId, playerId);
            return;
        }
        roomService.voteExtension(roomId, playerId, data.get("agree").asBoolean());
    }

    private void onNextRound(WebSocketSession session) throws Exception {
        String playerId = requirePlayerId(session);
        String roomId = requireRoomId(session);
        roomService.nextRound(roomId, playerId);
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private void pushState(WebSocketSession session, String roomId, String playerId) throws Exception {
        WsSender.send(session, objectMapper, WsType.ROOM_STATE.name(), roomService.viewFor(roomId, playerId));
    }

    private String requirePlayerId(WebSocketSession session) {
        Object value = session.getAttributes().get(ATTR_PLAYER_ID);
        if (value == null) {
            throw new BusinessException(4010, "请先发送 HELLO 完成登录");
        }
        return value.toString();
    }

    private String requireRoomId(WebSocketSession session) {
        Object value = session.getAttributes().get(ATTR_ROOM_ID);
        if (value == null) {
            throw new BusinessException(4011, "你还没有加入房间");
        }
        return value.toString();
    }

    private String nickname(WebSocketSession session) {
        Object value = session.getAttributes().get("nickname");
        return value == null ? null : value.toString();
    }

    private String avatar(WebSocketSession session) {
        Object value = session.getAttributes().get("avatar");
        return value == null ? "" : value.toString();
    }

    private String text(JsonNode data, String field) {
        if (data == null || !data.hasNonNull(field)) {
            return null;
        }
        return data.get(field).asText();
    }

    private void sendError(WebSocketSession session, int code, String message) {
        try {
            WsSender.send(session, objectMapper, WsType.ERROR.name(), Map.of("code", code, "message", message));
        } catch (Exception ex) {
            log.warn("send error failed", ex);
        }
    }
}
