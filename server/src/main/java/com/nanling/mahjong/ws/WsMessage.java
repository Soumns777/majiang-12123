package com.nanling.mahjong.ws;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 统一消息信封：{ "type": "ROOM_STATE", "data": { ... } }
 */
public record WsMessage(String type, JsonNode data) {
}
