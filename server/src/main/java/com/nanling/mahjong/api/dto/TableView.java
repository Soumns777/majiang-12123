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
        /** 房间是否开启了自制规则「打出癞子，全场作废」 */
        boolean voidWildcardOnDiscard,
        /** 本局癞子是否已经作废（有人打出过癞子） */
        boolean wildcardVoided,
        String lastDiscardTile,
        int lastDiscardSeat,
        long deadlineAt,
        long serverTime,
        /** 当前连接者是不是观战者（是则看不到任何人的手牌） */
        boolean spectator,
        /** 观战者昵称列表 */
        List<String> spectators,
        List<PlayerView> players,
        ResultView result
) {
}
