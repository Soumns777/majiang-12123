package com.nanling.mahjong.domain.game;

import java.util.List;

/**
 * 一局结算结果。
 *
 * @param drawGame    是否荒庄
 * @param winnerSeat  胡牌座位，荒庄为 -1
 * @param loserSeat   点炮座位，自摸/荒庄为 -1
 * @param fan         总番
 * @param score       单家应付分
 * @param flavors     番型名称
 * @param winningHand 胡牌牌型（解析后）
 */
public record GameResult(
        boolean drawGame,
        int winnerSeat,
        int loserSeat,
        int fan,
        int score,
        List<String> flavors,
        List<Integer> winningHand
) {
    public static GameResult draw() {
        return new GameResult(true, -1, -1, 0, 0, List.of(), List.of());
    }
}
