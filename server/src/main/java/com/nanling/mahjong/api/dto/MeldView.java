package com.nanling.mahjong.api.dto;

import java.util.List;

/**
 * 已亮出的面子。暗杠只对本人展示牌面。
 */
public record MeldView(
        String type,
        List<String> tiles,
        int fromSeat,
        boolean exposed
) {
}
