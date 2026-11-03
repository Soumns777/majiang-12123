package com.nanling.mahjong.api.dto;

import java.util.List;

/**
 * 本局结算。
 */
public record ResultView(
        boolean drawGame,
        int winnerSeat,
        int loserSeat,
        int fan,
        int score,
        List<String> flavors,
        List<String> winningHand
) {
}
