package com.nanling.mahjong.domain.rule;

/**
 * 癞子（宝）的确定方式。
 */
public enum WildcardMode {
    /** 无癞子局 */
    NONE,
    /** 开局翻一张牌，取其后一张为癞子（翻宝） */
    FLIP_NEXT,
    /** 房主指定固定癞子牌 */
    FIXED
}
