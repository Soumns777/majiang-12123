package com.nanling.mahjong.domain.rule;

/**
 * 胡牌调味剂（加分项）。番数为初版默认值，后续由产品配置表驱动。
 */
public enum Flavor {

    JIU_ZHI("9支", 1),
    SHI_YI_ZHI("11支", 2),
    HUN_YI_SE("混一色", 2),
    QING_YI_SE("清一色", 4),
    PAO_FENG("跑风", 1),
    ZI_MO("自摸", 1),
    KONG("杠", 1),
    PING_HU("平胡", 1);

    private final String label;
    private final int fan;

    Flavor(String label, int fan) {
        this.label = label;
        this.fan = fan;
    }

    public String getLabel() {
        return label;
    }

    public int getFan() {
        return fan;
    }
}
