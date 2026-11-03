package com.nanling.mahjong.domain.game;

/**
 * 玩家可执行的动作。
 */
public enum ActionType {
    /** 摸牌（服务端自动执行） */
    DRAW,
    /** 出牌 */
    DISCARD,
    /** 碰 */
    PONG,
    /** 直杠（别人打出的牌 + 手上三张） */
    KONG_MING,
    /** 暗杠（手上四张） */
    KONG_AN,
    /** 补杠（碰后摸到第四张） */
    KONG_BU,
    /** 胡 */
    HU,
    /** 过 */
    PASS
}
