package com.nanling.mahjong.domain.rule;

/**
 * 胡牌牌型分项（按《芜湖麻将-南陵》计分规则）。
 *
 * <p>全部牌型分<b>可叠加</b>，唯一的例外是清一色与混一色互斥；
 * 9 支本身不加分，它是「基础分」的由来，所以分值记 0，只在结算里作为说明出现。</p>
 *
 * <ul>
 *   <li>基础分 1 分 —— 只要胡牌必拿</li>
 *   <li>单钓 +1 / 跑风 +1 / 11支 +3 / 混一色 +2 / 清一色 +4</li>
 *   <li>附加（胡牌才结算）：每个杠 +1、杠开 +2、头家 ±1（在结算层处理）</li>
 * </ul>
 */
public enum Flavor {

    /** 基础分：只要胡牌就有，9 支胡牌也靠它拿 1 分 */
    PING_HU("基础", 1),
    /** 9 支：只是牌型说明，分值已含在基础分里 */
    JIU_ZHI("9支", 0),
    SHI_YI_ZHI("11支", 3),
    HUN_YI_SE("混一色", 2),
    QING_YI_SE("清一色", 4),
    /** 癞子充当将头对子胡牌 */
    PAO_FENG("跑风", 1),
    /** 胡的那张正好是将牌（单吊将） */
    DAN_DIAO("单钓", 1),
    /** 每个杠 +1，多个累加 */
    KONG("杠", 1),
    /** 杠完补牌直接胡，与杠分叠加 */
    KONG_KAI("杠开", 2);

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
