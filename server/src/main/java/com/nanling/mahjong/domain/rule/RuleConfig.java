package com.nanling.mahjong.domain.rule;

import com.nanling.mahjong.domain.tile.Tiles;

/**
 * 一局麻将的规则参数。
 *
 * <p>南陵麻将默认：有癞子、不吃、不设七对、有癞子的手牌必须命中调味剂。</p>
 */
public class RuleConfig {

    /** 是否启用癞子 */
    private boolean wildcardEnabled = true;

    /** 癞子确定方式 */
    private WildcardMode wildcardMode = WildcardMode.FLIP_NEXT;

    /** FIXED 模式下指定的癞子牌 id */
    private Integer fixedWildcardTile;

    /** 本局实际生效的癞子牌（开局时由牌墙写入） */
    private Integer wildcardTile;

    /** 手牌含癞子时，是否必须命中 9支/11支/混一色/跑风 之一 */
    private boolean requireFlavorForJokerHand = true;

    /** 底分 */
    private int baseScore = 1;

    /** 局数 */
    private int maxRounds = 8;

    /** 碰杠胡响应超时（秒） */
    private int actionTimeoutSeconds = 15;

    /** 出牌超时（秒） */
    private int turnTimeoutSeconds = 20;

    public RuleConfig copy() {
        RuleConfig copy = new RuleConfig();
        copy.wildcardEnabled = wildcardEnabled;
        copy.wildcardMode = wildcardMode;
        copy.fixedWildcardTile = fixedWildcardTile;
        copy.wildcardTile = wildcardTile;
        copy.requireFlavorForJokerHand = requireFlavorForJokerHand;
        copy.baseScore = baseScore;
        copy.maxRounds = maxRounds;
        copy.actionTimeoutSeconds = actionTimeoutSeconds;
        copy.turnTimeoutSeconds = turnTimeoutSeconds;
        return copy;
    }

    /** 某张牌在本局是否为癞子。 */
    public boolean isWildcard(int tile) {
        return wildcardEnabled && wildcardTile != null && wildcardTile == tile;
    }

    public boolean isWildcardEnabled() {
        return wildcardEnabled;
    }

    public void setWildcardEnabled(boolean wildcardEnabled) {
        this.wildcardEnabled = wildcardEnabled;
    }

    public WildcardMode getWildcardMode() {
        return wildcardMode;
    }

    public void setWildcardMode(WildcardMode wildcardMode) {
        this.wildcardMode = wildcardMode;
    }

    public Integer getFixedWildcardTile() {
        return fixedWildcardTile;
    }

    public void setFixedWildcardTile(Integer fixedWildcardTile) {
        this.fixedWildcardTile = fixedWildcardTile;
    }

    public Integer getWildcardTile() {
        return wildcardTile;
    }

    public void setWildcardTile(Integer wildcardTile) {
        this.wildcardTile = wildcardTile;
    }

    public String getWildcardDisplay() {
        return wildcardTile == null ? "无" : Tiles.display(wildcardTile);
    }

    public boolean isRequireFlavorForJokerHand() {
        return requireFlavorForJokerHand;
    }

    public void setRequireFlavorForJokerHand(boolean requireFlavorForJokerHand) {
        this.requireFlavorForJokerHand = requireFlavorForJokerHand;
    }

    public int getBaseScore() {
        return baseScore;
    }

    public void setBaseScore(int baseScore) {
        this.baseScore = baseScore;
    }

    public int getMaxRounds() {
        return maxRounds;
    }

    public void setMaxRounds(int maxRounds) {
        this.maxRounds = maxRounds;
    }

    public int getActionTimeoutSeconds() {
        return actionTimeoutSeconds;
    }

    public void setActionTimeoutSeconds(int actionTimeoutSeconds) {
        this.actionTimeoutSeconds = actionTimeoutSeconds;
    }

    public int getTurnTimeoutSeconds() {
        return turnTimeoutSeconds;
    }

    public void setTurnTimeoutSeconds(int turnTimeoutSeconds) {
        this.turnTimeoutSeconds = turnTimeoutSeconds;
    }
}
