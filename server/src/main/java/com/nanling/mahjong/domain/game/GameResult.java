package com.nanling.mahjong.domain.game;

import com.nanling.mahjong.domain.rule.Flavor;

import java.util.List;

/**
 * 一局结算结果。
 *
 * @param drawGame    是否荒庄
 * @param winnerSeat  胡牌座位，荒庄为 -1
 * @param loserSeat   点炮座位；南陵麻将只允许自摸，所以恒为 -1
 * @param fan         牌型分合计（基础 1 + 各项叠加，不含底分倍数）
 * @param score       单家应付欢乐豆 = 牌型分 × 底分
 * @param flavors     命中的牌型分项（带分值，用于结算说明）
 * @param winningHand 胡牌牌型（解析后）
 */
public record GameResult(
        boolean drawGame,
        int winnerSeat,
        int loserSeat,
        int fan,
        int score,
        List<Flavor> flavors,
        List<Integer> winningHand
) {
    public static GameResult draw() {
        return new GameResult(true, -1, -1, 0, 0, List.of(), List.of());
    }

    /** 牌型名列表，兼容只关心名字的调用方。 */
    public List<String> flavorLabels() {
        return flavors.stream().map(Flavor::getLabel).toList();
    }
}
