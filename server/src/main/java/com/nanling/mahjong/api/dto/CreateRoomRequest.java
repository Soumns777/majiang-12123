package com.nanling.mahjong.api.dto;

/**
 * 创建房间参数。全部可选，缺省走服务端配置。
 */
public record CreateRoomRequest(
        Integer baseScore,
        Integer maxRounds,
        String wildcardMode,
        String wildcardTile,
        Boolean requireFlavorForJokerHand
) {
}
