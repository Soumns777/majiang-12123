package com.nanling.mahjong.domain.rule;

import com.nanling.mahjong.domain.tile.Tiles;

import java.util.ArrayList;
import java.util.List;

/**
 * 南陵麻将胡牌判定。
 *
 * <p>判定顺序固定为三层，任何一层不过都不能胡：</p>
 * <ol>
 *   <li><b>基础牌型</b>：14 张 = 4 副面子（顺子/刻子）+ 1 对将牌</li>
 *   <li><b>缺一门</b>：万、筒、条三色至少缺一门（字牌不影响判定）</li>
 *   <li><b>调味剂</b>：手牌含癞子时，必须命中 9支 / 11支 / 混一色 / 跑风 之一；
 *       无癞子手牌可裸胡（无癞子单钓）</li>
 * </ol>
 *
 * <p>癞子在这里是万能牌：可以被当成任意一张牌去补顺子、补刻子或补将牌。
 * 算法把癞子“解析”成具体牌面后再统计花色，因此 9支 的口径与规则一致：
 * 实牌 + 癞子合计超过 9 张才算，癞子凑出刚好 9 张不算。</p>
 *
 * <p><b>自制规则「打出癞子，全场作废」</b>：当 {@link WinRequest#wildcardVoided()} 为 true 时，
 * 癞子不再被抽出来当万能牌，而是直接按自身牌面计入 {@code counts}。
 * 由此自然得出：不能补面子、不能作将（跑风失效）、9支/11支 只数真实牌面，
 * 且因为手里“没有癞子”，也就没有调味剂要求，可以裸胡。</p>
 */
public final class WinChecker {

    /** 单次判定最多保留的拆解方案数，防止极端牌型爆炸 */
    private static final int MAX_DECOMPOSITIONS = 400;

    private WinChecker() {
    }

    public static WinResult check(WinRequest request) {
        List<Integer> concealed = request.concealedTiles();
        List<Meld> melds = request.melds();
        RuleConfig config = request.config();

        if (concealed == null || melds == null) {
            return WinResult.fail("手牌数据为空");
        }
        if (melds.size() > 4) {
            return WinResult.fail("面子数量超过 4 副");
        }
        int expected = 14 - melds.size() * 3;
        if (concealed.size() != expected) {
            return WinResult.fail("手牌张数不合法：应为 " + expected + " 张，实际 " + concealed.size() + " 张");
        }

        int[] counts = new int[Tiles.KIND_COUNT];
        int jokers = 0;
        // 癞子作废后（有人打出过癞子），癞子按自身牌面当普通牌统计
        boolean wildcardVoided = request.wildcardVoided();
        for (int tile : concealed) {
            if (tile < 0 || tile >= Tiles.KIND_COUNT) {
                return WinResult.fail("手牌中存在非法牌");
            }
            if (!wildcardVoided && config.isWildcard(tile)) {
                jokers++;
            } else {
                counts[tile]++;
            }
        }

        List<Decomposition> decompositions = new ArrayList<>();
        int needMelds = 4 - melds.size();

        // 将牌枚举：两张实牌 / 一实一癞（跑风）/ 两张癞子
        for (int pairTile = 0; pairTile < Tiles.KIND_COUNT; pairTile++) {
            if (counts[pairTile] >= 2) {
                counts[pairTile] -= 2;
                search(counts, jokers, needMelds, new ArrayList<>(), decompositions, pairTile, false);
                counts[pairTile] += 2;
            }
            if (counts[pairTile] >= 1 && jokers >= 1) {
                counts[pairTile] -= 1;
                search(counts, jokers - 1, needMelds, new ArrayList<>(), decompositions, pairTile, true);
                counts[pairTile] += 1;
            }
        }
        if (jokers >= 2) {
            search(counts, jokers - 2, needMelds, new ArrayList<>(), decompositions, -1, true);
        }

        if (decompositions.isEmpty()) {
            return WinResult.fail("牌型不成立：无法组成 4 副面子 + 1 对将牌");
        }

        int jokerInHand = jokers;
        boolean needsFlavor = config.isRequireFlavorForJokerHand() && jokerInHand > 0;

        Evaluation best = null;
        boolean anyLackingSuit = false;
        for (Decomposition decomposition : decompositions) {
            Evaluation evaluation = evaluate(decomposition, melds, config, request.selfDraw(), needsFlavor);
            if (evaluation.lackingSuit()) {
                anyLackingSuit = true;
            }
            if (evaluation.valid() && (best == null || evaluation.fan() > best.fan())) {
                best = evaluation;
            }
        }

        if (best == null) {
            if (!anyLackingSuit) {
                return WinResult.fail("未满足缺一门：万、筒、条必须少一门");
            }
            return WinResult.fail("有癞子手牌未命中调味剂（9支 / 11支 / 混一色 / 跑风）");
        }

        return WinResult.success(best.flavors(), best.fan(), best.fan() * config.getBaseScore(),
                best.pairUsedJoker(), jokerInHand, best.winningHand());
    }

    /**
     * 递归拆解面子。
     *
     * @param counts   非癞子牌的计数数组
     * @param jokers   剩余癞子数
     * @param meldsNeeded 还需要的面子数
     * @param acc      当前已解析出的面子牌（每副 3 张）
     */
    private static void search(int[] counts, int jokers, int meldsNeeded, List<Integer> acc,
                               List<Decomposition> out, int pairTile, boolean pairUsedJoker) {
        if (out.size() >= MAX_DECOMPOSITIONS) {
            return;
        }
        if (meldsNeeded == 0) {
            if (jokers == 0 && isAllZero(counts)) {
                out.add(new Decomposition(new ArrayList<>(acc), pairTile, pairUsedJoker));
            }
            return;
        }

        int first = firstNonZero(counts);
        if (first < 0) {
            // 只剩癞子，全部补成刻子
            if (jokers == meldsNeeded * 3) {
                int fill = neutralTile(acc);
                List<Integer> copy = new ArrayList<>(acc);
                for (int i = 0; i < meldsNeeded; i++) {
                    copy.add(fill);
                    copy.add(fill);
                    copy.add(fill);
                }
                out.add(new Decomposition(copy, pairTile, pairUsedJoker));
            }
            return;
        }

        // 方案一：以 first 组成刻子（优先用实牌，缺的用癞子补）
        int realUsed = Math.min(counts[first], 3);
        int jokerNeeded = 3 - realUsed;
        if (jokers >= jokerNeeded) {
            counts[first] -= realUsed;
            acc.add(first);
            acc.add(first);
            acc.add(first);
            search(counts, jokers - jokerNeeded, meldsNeeded - 1, acc, out, pairTile, pairUsedJoker);
            acc.remove(acc.size() - 1);
            acc.remove(acc.size() - 1);
            acc.remove(acc.size() - 1);
            counts[first] += realUsed;
        }

        // 方案二：以 first 作为顺子起点（字牌不参与）
        if (Tiles.isNumberTile(first) && Tiles.rankOf(first) <= 7) {
            int second = first + 1;
            int third = first + 2;
            boolean secondReal = counts[second] > 0;
            boolean thirdReal = counts[third] > 0;
            int missing = (secondReal ? 0 : 1) + (thirdReal ? 0 : 1);
            if (jokers >= missing) {
                counts[first]--;
                if (secondReal) {
                    counts[second]--;
                }
                if (thirdReal) {
                    counts[third]--;
                }
                acc.add(first);
                acc.add(second);
                acc.add(third);
                search(counts, jokers - missing, meldsNeeded - 1, acc, out, pairTile, pairUsedJoker);
                acc.remove(acc.size() - 1);
                acc.remove(acc.size() - 1);
                acc.remove(acc.size() - 1);
                if (thirdReal) {
                    counts[third]++;
                }
                if (secondReal) {
                    counts[second]++;
                }
                counts[first]++;
            }
        }
    }

    private static Evaluation evaluate(Decomposition decomposition, List<Meld> melds, RuleConfig config,
                                      boolean selfDraw, boolean needsFlavor) {
        List<Integer> tiles = new ArrayList<>(decomposition.melds());
        int pairTile = decomposition.pairTile() >= 0 ? decomposition.pairTile() : neutralTile(tiles);
        tiles.add(pairTile);
        tiles.add(pairTile);
        for (Meld meld : melds) {
            tiles.addAll(meld.getTiles());
        }

        int[] suitCount = new int[3];
        int honorCount = 0;
        for (int tile : tiles) {
            if (Tiles.isHonor(tile)) {
                honorCount++;
            } else {
                suitCount[Tiles.suitOf(tile).ordinal()]++;
            }
        }
        int suitKinds = 0;
        int maxSuit = 0;
        for (int count : suitCount) {
            if (count > 0) {
                suitKinds++;
            }
            maxSuit = Math.max(maxSuit, count);
        }

        // 第二层：缺一门
        boolean lackingSuit = suitKinds <= 2;
        if (!lackingSuit) {
            return Evaluation.invalid(false);
        }

        boolean jiuZhi = maxSuit > 9;
        boolean shiYiZhi = maxSuit > 10;
        boolean hunYiSe = suitKinds == 1;
        boolean qingYiSe = suitKinds == 1 && honorCount == 0;
        boolean paoFeng = decomposition.pairUsedJoker();

        // 第三层：调味剂
        boolean hasFlavor = jiuZhi || shiYiZhi || hunYiSe || paoFeng;
        if (needsFlavor && !hasFlavor) {
            return Evaluation.invalid(true);
        }

        List<Flavor> flavors = new ArrayList<>();
        int fan = 0;
        if (qingYiSe) {
            flavors.add(Flavor.QING_YI_SE);
            fan += Flavor.QING_YI_SE.getFan();
        } else if (hunYiSe) {
            flavors.add(Flavor.HUN_YI_SE);
            fan += Flavor.HUN_YI_SE.getFan();
        }
        if (jiuZhi) {
            flavors.add(Flavor.JIU_ZHI);
            fan += Flavor.JIU_ZHI.getFan();
        }
        if (shiYiZhi) {
            flavors.add(Flavor.SHI_YI_ZHI);
            fan += Flavor.SHI_YI_ZHI.getFan();
        }
        if (paoFeng) {
            flavors.add(Flavor.PAO_FENG);
            fan += Flavor.PAO_FENG.getFan();
        }
        if (selfDraw) {
            flavors.add(Flavor.ZI_MO);
            fan += Flavor.ZI_MO.getFan();
        }
        long kongCount = melds.stream().filter(m -> m.getType().isKong()).count();
        if (kongCount > 0) {
            flavors.add(Flavor.KONG);
            fan += (int) kongCount * Flavor.KONG.getFan();
        }
        if (flavors.isEmpty()) {
            flavors.add(Flavor.PING_HU);
            fan += Flavor.PING_HU.getFan();
        }

        List<Integer> hand = new ArrayList<>(tiles);
        hand.sort(Integer::compareTo);
        return new Evaluation(true, true, flavors, fan, paoFeng, hand);
    }

    private static boolean isAllZero(int[] counts) {
        for (int count : counts) {
            if (count != 0) {
                return false;
            }
        }
        return true;
    }

    private static int firstNonZero(int[] counts) {
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > 0) {
                return i;
            }
        }
        return -1;
    }

    /** 癞子需要“变成”某张牌时，取已有牌里最多的花色，保证不影响混一色/9支判定。 */
    private static int neutralTile(List<Integer> tiles) {
        int[] suitCount = new int[3];
        for (int tile : tiles) {
            if (!Tiles.isHonor(tile)) {
                suitCount[Tiles.suitOf(tile).ordinal()]++;
            }
        }
        int best = 0;
        for (int i = 1; i < 3; i++) {
            if (suitCount[i] > suitCount[best]) {
                best = i;
            }
        }
        return best == 0 ? Tiles.WAN_BASE : best == 1 ? Tiles.TONG_BASE : Tiles.TIAO_BASE;
    }

    private record Decomposition(List<Integer> melds, int pairTile, boolean pairUsedJoker) {
    }

    private record Evaluation(boolean valid, boolean lackingSuit, List<Flavor> flavors, int fan,
                              boolean pairUsedJoker, List<Integer> winningHand) {

        static Evaluation invalid(boolean lackingSuit) {
            return new Evaluation(false, lackingSuit, List.of(), 0, false, List.of());
        }
    }
}
