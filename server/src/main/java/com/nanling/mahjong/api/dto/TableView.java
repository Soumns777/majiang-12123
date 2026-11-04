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
        /** 底分：单家应付欢乐豆 = 牌型分 × 底分 */
        int baseScore,
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
        /** 正在投票的延时申请，没有则为 null */
        ExtendView extend,
        List<PlayerView> players,
        ResultView result
) {

    /**
     * 延时申请状态。
     *
     * @param seat      申请人座位
     * @param approvals 已同意的座位列表（不含申请人）
     * @param need      需要几家同意（固定 3 家）
     */
    public record ExtendView(int seat, List<Integer> approvals, int need) {
    }
}
