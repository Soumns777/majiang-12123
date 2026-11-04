package com.nanling.mahjong.ws;

/**
 * WebSocket 消息类型。
 *
 * <p>客户端 → 服务端：HELLO / ROOM_CREATE / ROOM_JOIN / ROOM_WATCH / ROOM_LEAVE /
 * ROOM_READY / ROOM_RULE / ROOM_BOT / GAME_START / GAME_ACTION / NEXT_ROUND / PING</p>
 * <p>服务端 → 客户端：HELLO_ACK / ROOM_STATE / LEFT / ERROR / PONG</p>
 */
public enum WsType {
    HELLO,
    HELLO_ACK,
    ROOM_CREATE,
    ROOM_JOIN,
    /** 以观战身份进入房间：房间满员或已开局时也能进，看不到任何人的手牌 */
    ROOM_WATCH,
    ROOM_LEAVE,
    ROOM_READY,
    /** 房间内切换自制规则，目前支持 voidWildcardOnDiscard */
    ROOM_RULE,
    /** 房主往空座位补机器人，机器人由服务端逐个代打 */
    ROOM_BOT,
    ROOM_STATE,
    GAME_START,
    GAME_ACTION,
    NEXT_ROUND,
    LEFT,
    ERROR,
    PING,
    PONG
}
