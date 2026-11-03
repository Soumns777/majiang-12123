package com.nanling.mahjong.domain.rule;

/**
 * 面子类型。
 */
public enum MeldType {
    /** 顺子 */
    CHOW(3),
    /** 碰 */
    PONG(3),
    /** 明杠（碰后加杠 / 直杠） */
    KONG_MING(4),
    /** 暗杠 */
    KONG_AN(4),
    /** 补杠（碰后摸到第四张） */
    KONG_BU(4);

    private final int physicalSize;

    MeldType(int physicalSize) {
        this.physicalSize = physicalSize;
    }

    public int getPhysicalSize() {
        return physicalSize;
    }

    public boolean isKong() {
        return this == KONG_MING || this == KONG_AN || this == KONG_BU;
    }

    /** 结构上始终算一副面子（3 张的份额）。 */
    public int getStructuralSize() {
        return 3;
    }
}
