package com.nanling.mahjong.domain.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 胡牌判定结果。
 */
public class WinResult {

    private final boolean win;
    private final String reason;
    private final List<Flavor> flavors;
    private final int fan;
    private final int score;
    private final boolean pairUsedJoker;
    private final int jokerCount;
    private final List<Integer> winningHand;

    private WinResult(boolean win, String reason, List<Flavor> flavors, int fan, int score,
                      boolean pairUsedJoker, int jokerCount, List<Integer> winningHand) {
        this.win = win;
        this.reason = reason;
        this.flavors = Collections.unmodifiableList(new ArrayList<>(flavors));
        this.fan = fan;
        this.score = score;
        this.pairUsedJoker = pairUsedJoker;
        this.jokerCount = jokerCount;
        this.winningHand = Collections.unmodifiableList(new ArrayList<>(winningHand));
    }

    public static WinResult fail(String reason) {
        return new WinResult(false, reason, List.of(), 0, 0, false, 0, List.of());
    }

    public static WinResult success(List<Flavor> flavors, int fan, int score,
                                    boolean pairUsedJoker, int jokerCount, List<Integer> winningHand) {
        return new WinResult(true, null, flavors, fan, score, pairUsedJoker, jokerCount, winningHand);
    }

    public boolean isWin() {
        return win;
    }

    public String getReason() {
        return reason;
    }

    public List<Flavor> getFlavors() {
        return flavors;
    }

    public int getFan() {
        return fan;
    }

    public int getScore() {
        return score;
    }

    public boolean isPairUsedJoker() {
        return pairUsedJoker;
    }

    public int getJokerCount() {
        return jokerCount;
    }

    public List<Integer> getWinningHand() {
        return winningHand;
    }

    public List<String> getFlavorLabels() {
        return flavors.stream().map(Flavor::getLabel).toList();
    }
}
