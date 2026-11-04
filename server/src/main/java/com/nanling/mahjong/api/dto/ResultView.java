package com.nanling.mahjong.api.dto;

import java.util.List;

/**
 * 本局结算。
 *
 * @param fan         牌型分合计
 * @param score       单家应付欢乐豆（= 牌型分 × 底分）
 * @param baseScore   本局底分
 * @param flavors     牌型名列表（兼容）
 * @param breakdown   牌型分明细：每项名字 + 分值，用于「胡了什么」的说明
 * @param deltas      四家欢乐豆增减，前端结算弹窗直接展示，不再自己算
 * @param winningHand 胡牌牌型
 */
public record ResultView(
        boolean drawGame,
        int winnerSeat,
        int loserSeat,
        int fan,
        int score,
        int baseScore,
        List<String> flavors,
        List<FlavorItem> breakdown,
        List<SeatBeans> deltas,
        List<String> winningHand
) {

    /** 单项牌型分。 */
    public record FlavorItem(String label, int fan) {
    }

    /** 某一座位本局欢乐豆增减。 */
    public record SeatBeans(int seat, int beans) {
    }
}
