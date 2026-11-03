package com.nanling.mahjong.api.dto;

import java.util.List;

/**
 * 牌桌视图，按玩家视角裁剪后下发。
 */
public record TableView(
        String roomId,
        String ownerId,
        String status,
        int round,
        int maxRounds,
        String phase,
        int currentSeat,
        int remainingTiles,
        String wildcard,
        String wildcardLabel,
        String lastDiscardTile,
        int lastDiscardSeat,
        long deadlineAt,
        long serverTime,
        List<PlayerView> players,
        ResultView result
) {
}
