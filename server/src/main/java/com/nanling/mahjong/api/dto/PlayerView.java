package com.nanling.mahjong.api.dto;

import java.util.List;

/**
 * 单个玩家的可见信息。别人的手牌只给张数，不给牌面。
 */
public record PlayerView(
        int seat,
        String playerId,
        String nickname,
        String avatar,
        int score,
        boolean ready,
        boolean online,
        /** 是否机器人（前端画个 🤖 角标） */
        boolean bot,
        boolean dealer,
        boolean current,
        int handCount,
        List<String> hand,
        List<MeldView> melds,
        List<String> discards,
        List<String> actions
) {
}
