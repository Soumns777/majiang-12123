package com.nanling.mahjong.room;

/**
 * 房间状态变更事件，WS 层监听后向房间内所有连接推送最新视图。
 */
public record RoomUpdatedEvent(String roomId, String reason) {
}
