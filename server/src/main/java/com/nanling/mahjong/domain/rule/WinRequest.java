package com.nanling.mahjong.domain.rule;

import java.util.List;

/**
 * 胡牌判定入参。
 *
 * @param concealedTiles 手牌（含刚摸到/刚点炮的那张，不含已亮出的面子）
 * @param melds          已固定的面子
 * @param config         规则配置
 * @param selfDraw       是否自摸
 * @param wildcardVoided 本局癞子是否已作废（有人打出过癞子且规则开启）；
 *                       作废后癞子按自身牌面参与判定，不再当万能牌
 */
public record WinRequest(
        List<Integer> concealedTiles,
        List<Meld> melds,
        RuleConfig config,
        boolean selfDraw,
        boolean wildcardVoided
) {

    /** 兼容不关心癞子作废的调用方。 */
    public WinRequest(List<Integer> concealedTiles, List<Meld> melds, RuleConfig config, boolean selfDraw) {
        this(concealedTiles, melds, config, selfDraw, false);
    }

    public static WinRequest of(List<Integer> concealedTiles, List<Meld> melds, RuleConfig config) {
        return new WinRequest(concealedTiles, melds, config, false, false);
    }
}
