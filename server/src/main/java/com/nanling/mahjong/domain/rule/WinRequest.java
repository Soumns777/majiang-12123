package com.nanling.mahjong.domain.rule;

import java.util.List;

/**
 * 胡牌判定入参。
 *
 * @param concealedTiles 手牌（含刚摸到/刚点炮的那张，不含已亮出的面子）
 * @param melds          已固定的面子
 * @param config         规则配置
 * @param selfDraw       是否自摸
 */
public record WinRequest(
        List<Integer> concealedTiles,
        List<Meld> melds,
        RuleConfig config,
        boolean selfDraw
) {
    public static WinRequest of(List<Integer> concealedTiles, List<Meld> melds, RuleConfig config) {
        return new WinRequest(concealedTiles, melds, config, false);
    }
}
