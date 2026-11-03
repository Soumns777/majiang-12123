package com.nanling.mahjong.domain.tile;

/**
 * 花色。字牌不能组顺子，也不参与“缺一门”判定。
 */
public enum Suit {
    /** 万 */
    WAN("万", true),
    /** 筒 */
    TONG("筒", true),
    /** 条 */
    TIAO("条", true),
    /** 字牌：东南西北中发白 */
    HONOR("字", false);

    private final String label;
    private final boolean sequenceSuit;

    Suit(String label, boolean sequenceSuit) {
        this.label = label;
        this.sequenceSuit = sequenceSuit;
    }

    public String getLabel() {
        return label;
    }

    /** 该花色能否组成顺子（字牌不行）。 */
    public boolean isSequenceSuit() {
        return sequenceSuit;
    }

    public static Suit ofTile(int tile) {
        if (tile < 9) {
            return WAN;
        }
        if (tile < 18) {
            return TONG;
        }
        if (tile < 27) {
            return TIAO;
        }
        return HONOR;
    }
}
