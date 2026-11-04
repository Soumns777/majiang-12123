package com.nanling.mahjong.bot;

import com.nanling.mahjong.domain.game.ActionType;
import com.nanling.mahjong.domain.rule.RuleConfig;
import com.nanling.mahjong.domain.tile.Suit;
import com.nanling.mahjong.domain.tile.Tiles;
import com.nanling.mahjong.engine.GameTable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 机器人决策。
 *
 * <p>刻意做成「无状态 + 纯函数」：传入牌桌和座位，返回一个动作，
 * 真正的执行、节奏控制（打一张隔一会儿再打下一张）都交给
 * {@code RoomService#tickBots}，这样决策逻辑可以单独写单测，
 * 也不会因为机器人思考而把房间锁占太久。</p>
 *
 * <p>策略很朴素，够用即可：</p>
 * <ul>
 *   <li>能胡就胡（不贪番，先跑通流程）；</li>
 *   <li>别人打的牌能碰就有一半概率碰，能杠不杠（杠会多摸一张，容易把演示节奏打乱）；</li>
 *   <li>出牌时优先丢「最没用」的：孤张字牌 → 孤张幺九 → 有搭子的牌，癞子永远不打。</li>
 * </ul>
 */
public final class BotBrain {

    private BotBrain() {
    }

    /**
     * 轮到自己出牌时，选一张要打的牌。
     *
     * @return 手牌中确实存在的一张牌
     */
    public static int chooseDiscard(GameTable table, int seat) {
        List<Integer> hand = table.getHand(seat);
        if (hand.isEmpty()) {
            throw new IllegalStateException("机器人手牌为空");
        }
        RuleConfig config = table.getConfig();
        int bestTile = hand.get(0);
        int bestScore = Integer.MAX_VALUE;
        for (int tile : hand) {
            int score = uselessness(hand, tile, config);
            if (score < bestScore) {
                bestScore = score;
                bestTile = tile;
            }
        }
        return bestTile;
    }

    /**
     * 这张牌有多「没用」，分数越低越该打出去。
     *
     * <p>评分维度：同张越多越有用、能连成顺子的邻牌越多越有用、
     * 字牌天生没用（不能组顺子）、癞子直接给最高分（绝对不打）。</p>
     */
    private static int uselessness(List<Integer> hand, int tile, RuleConfig config) {
        if (config.isWildcard(tile)) {
            return 10_000;
        }
        int score = Collections.frequency(hand, tile) * 14;
        if (Tiles.isHonor(tile)) {
            score -= 8;
        } else {
            score += neighbours(hand, tile) * 5;
            int rank = Tiles.rankOf(tile);
            if (rank == 1 || rank == 9) {
                score -= 2;
            }
        }
        return score;
    }

    /** 同花色 ±1 / ±2 的邻牌张数，用来粗略衡量「能不能组成顺子」。 */
    private static int neighbours(List<Integer> hand, int tile) {
        Suit suit = Tiles.suitOf(tile);
        int count = 0;
        for (int delta : new int[]{-2, -1, 1, 2}) {
            int other = tile + delta;
            if (other < 0 || other >= Tiles.KIND_COUNT) {
                continue;
            }
            if (Tiles.suitOf(other) != suit) {
                continue;
            }
            if (hand.contains(other)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 别人打出牌、轮到自己响应时，选一个动作。
     *
     * <p>优先级：胡 &gt; 碰（有概率） &gt; 过。明杠故意不选。</p>
     *
     * @param pongChance 碰牌概率，0 表示只胡不碰（单测里用 0 让行为完全确定）
     */
    public static ActionType chooseClaim(List<ActionType> claims, double pongChance) {
        if (claims.contains(ActionType.HU)) {
            return ActionType.HU;
        }
        if (claims.contains(ActionType.PONG) && ThreadLocalRandom.current().nextDouble() < pongChance) {
            return ActionType.PONG;
        }
        return ActionType.PASS;
    }
}
