package com.nanling.mahjong.domain.game;

/**
 * 欢乐豆结算公式。
 *
 * <p>南陵麻将只能自摸，所以是「三家付」：赢家 {@code +3 × 单家分}，其他每家 {@code -单家分}。
 * 头家再单独 ±1（头家自摸多 +1，头家输牌多扣 1，另外两家不受影响）。</p>
 *
 * <p>抽成静态纯函数是为了让结算（{@code RoomService}）和视图（{@code ViewMapper} 给前端算增减）
 * 用同一套算术，不会出现「弹窗说 +6、余额只加 5」这种对不上的情况。</p>
 */
public final class BeanSettlement {

    private BeanSettlement() {
    }

    /**
     * 某个座位本局的欢乐豆增减。
     *
     * @param seat       座位
     * @param winnerSeat 赢家座位
     * @param dealerSeat 头家座位
     * @param score      单家应付欢乐豆（= 牌型分 × 底分）
     */
    public static int deltaFor(int seat, int winnerSeat, int dealerSeat, int score) {
        int delta = seat == winnerSeat ? 3 * score : -score;
        if (seat == dealerSeat) {
            delta += seat == winnerSeat ? 1 : -1;
        }
        return delta;
    }
}
