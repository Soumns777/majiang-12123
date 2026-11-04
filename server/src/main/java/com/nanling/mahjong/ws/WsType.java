package com.nanling.mahjong.ws;

/**
 * WebSocket 消息类型。
 *
 * <p>客户端 → 服务端：HELLO / ROOM_CREATE / ROOM_JOIN / ROOM_LEAVE / ROOM_READY /
 * ROOM_RULE / GAME_START / GAME_ACTION / NEXT_ROUND / PING</p>
 * <p>服务端 → 客户端：HELLO_ACK / ROOM_STATE / ERROR / PONG</p>
 */
public enum WsType {
    HELLO,
    HELLO_ACK,
    ROOM_CREATE,
    ROOM_JOIN,
    ROOM_LEAVE,
    ROOM_READY,
    /** 房间内切换自制规则，目前支持 voidWildcardOnDiscard */
    ROOM_RULE,
    ROOM_STATE,
    GAME_START,
    GAME_ACTION,
    NEXT_ROUND,
    LEFT,
    ERROR,
    PING,
    PONG
}
