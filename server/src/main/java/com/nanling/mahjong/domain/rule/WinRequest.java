package com.nanling.mahjong.domain.rule;

import java.util.List;

/**
 * 胡牌判定入参。
 *
 * @param concealedTiles 手牌（含刚摸到的那张，不含已亮出的面子）
 * @param melds          已固定的面子
 * @param config         规则配置
 * @param selfDraw       是否自摸（南陵麻将只允许自摸，恒为 true）
 * @param wildcardVoided 本局癞子是否已作废（有人打出过癞子且规则开启）；
 *                       作废后癞子按自身牌面参与判定，不再当万能牌
 * @param winningTile    胡的那张牌，用于判定「单钓」；-1 表示未知
 * @param kongKai        是否杠开（杠完补牌直接胡），用于 +2 分
 */
public record WinRequest(
        List<Integer> concealedTiles,
        List<Meld> melds,
        RuleConfig config,
        boolean selfDraw,
        boolean wildcardVoided,
        int winningTile,
        boolean kongKai
) {

    /** 兼容不关心癞子作废 / 单钓 / 杠开的调用方。 */
    public WinRequest(List<Integer> concealedTiles, List<Meld> melds, RuleConfig config, boolean selfDraw) {
        this(concealedTiles, melds, config, selfDraw, false, -1, false);
    }

    public WinRequest(List<Integer> concealedTiles, List<Meld> melds, RuleConfig config,
                      boolean selfDraw, boolean wildcardVoided) {
        this(concealedTiles, melds, config, selfDraw, wildcardVoided, -1, false);
    }

    public static WinRequest of(List<Integer> concealedTiles, List<Meld> melds, RuleConfig config) {
        return new WinRequest(concealedTiles, melds, config, false, false, -1, false);
    }

    /** 自摸判定（南陵只有这一种胡法）。 */
    public static WinRequest selfDraw(List<Integer> concealedTiles, List<Meld> melds, RuleConfig config,
                                      boolean wildcardVoided, int winningTile, boolean kongKai) {
        return new WinRequest(concealedTiles, melds, config, true, wildcardVoided, winningTile, kongKai);
    }
}
