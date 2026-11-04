package com.nanling.mahjong.engine;

import com.nanling.mahjong.domain.game.ActionType;
import com.nanling.mahjong.domain.game.GamePhase;
import com.nanling.mahjong.domain.rule.MeldType;
import com.nanling.mahjong.domain.rule.RuleConfig;
import com.nanling.mahjong.domain.rule.WildcardMode;
import com.nanling.mahjong.domain.tile.Tiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class GameTableTest {

    private RuleConfig noWildcard() {
        RuleConfig config = new RuleConfig();
        config.setWildcardEnabled(false);
        config.setWildcardMode(WildcardMode.NONE);
        return config;
    }

    @Test
    @DisplayName("发牌：庄家 14 张，其余 13 张，牌墙剩余正确")
    void dealShouldGiveDealerExtraTile() {
        GameTable table = GameTable.start(noWildcard(), 20261103L, 0);

        assertEquals(14, table.getHandCount(0));
        assertEquals(13, table.getHandCount(1));
        assertEquals(13, table.getHandCount(2));
        assertEquals(13, table.getHandCount(3));
        assertEquals(136 - 53, table.getRemainingTiles());
        assertEquals(GamePhase.PLAYER_TURN, table.getPhase());
        assertEquals(0, table.getCurrentSeat());
    }

    @Test
    @DisplayName("翻宝：癞子牌从牌墙翻出，剩余张数少 1")
    void flipWildcard() {
        RuleConfig config = new RuleConfig();
        config.setWildcardMode(WildcardMode.FLIP_NEXT);
        GameTable table = GameTable.start(config, 7L, 0);

        assertNotNull(table.getWildcardTile());
        assertEquals(136 - 1 - 53, table.getRemainingTiles());
    }

    @Test
    @DisplayName("出牌后轮到下家，且总牌数守恒")
    void discardShouldPassTurn() {
        GameTable table = GameTable.start(noWildcard(), 99L, 0);
        int tile = table.getHand(0).get(0);

        table.discard(0, tile);

        assertEquals(13, table.getHandCount(0));
        assertTrue(table.getDiscards(0).contains(tile));
        if (table.getPhase() == GamePhase.PLAYER_TURN) {
            assertEquals(1, table.getCurrentSeat());
            assertEquals(14, table.getHandCount(1));
        } else {
            assertEquals(GamePhase.WAITING_CLAIM, table.getPhase());
            assertTrue(table.getPendingSeats().size() > 0);
        }
    }

    @Test
    @DisplayName("碰：扣除两张手牌，生成碰面子，并由碰牌者出牌")
    void pongShouldCreateMeld() {
        for (long seed = 1; seed <= 3000; seed++) {
            GameTable table = GameTable.start(noWildcard(), seed, 0);
            int tile = table.getHand(0).get(0);
            table.discard(0, tile);
            if (table.getPhase() != GamePhase.WAITING_CLAIM) {
                continue;
            }
            boolean hasHu = table.getPendingSeats().stream()
                    .anyMatch(seat -> table.claimsFor(seat).contains(ActionType.HU));
            if (hasHu) {
                continue;
            }
            int claimer = -1;
            for (int seat : table.getPendingSeats()) {
                if (table.claimsFor(seat).contains(ActionType.PONG)) {
                    claimer = seat;
                    break;
                }
            }
            if (claimer < 0) {
                continue;
            }
            int before = table.getHandCount(claimer);
            table.claim(claimer, ActionType.PONG);
            for (int seat : new ArrayList<>(table.getPendingSeats())) {
                if (seat != claimer) {
                    table.pass(seat);
                }
            }

            assertEquals(GamePhase.PLAYER_TURN, table.getPhase());
            assertEquals(claimer, table.getCurrentSeat());
            assertTrue(table.getMelds(claimer).stream()
                    .anyMatch(meld -> meld.getType() == MeldType.PONG));
            assertEquals(before - 2, table.getHandCount(claimer));
            return;
        }
        fail("3000 个随机种子内没有出现可碰的局面");
    }

    @Test
    @DisplayName("超时：出牌阶段自动打出，响应阶段自动过")
    void timeoutShouldAutoPlay() {
        GameTable table = GameTable.start(noWildcard(), 5L, 0);
        table.onTimeout();
        assertTrue(table.getHandCount(0) <= 13);

        GameTable claimTable = GameTable.start(noWildcard(), 5L, 0);
        claimTable.discard(0, claimTable.getHand(0).get(0));
        if (claimTable.getPhase() == GamePhase.WAITING_CLAIM) {
            claimTable.onTimeout();
            assertTrue(claimTable.getPhase() == GamePhase.PLAYER_TURN
                    || claimTable.getPhase() == GamePhase.ENDED);
        }
    }

    @Test
    @DisplayName("整局冒烟：自动摸打直到荒庄，不抛异常")
    void fullRoundSmoke() {
        GameTable table = GameTable.start(noWildcard(), 2026L, 0);
        int guard = 0;

        while (table.getPhase() != GamePhase.ENDED && guard++ < 500) {
            if (table.getPhase() == GamePhase.PLAYER_TURN) {
                int seat = table.getCurrentSeat();
                List<Integer> hand = table.getHand(seat);
                table.discard(seat, hand.get(hand.size() - 1));
            } else if (table.getPhase() == GamePhase.WAITING_CLAIM) {
                for (int seat : new ArrayList<>(table.getPendingSeats())) {
                    table.pass(seat);
                }
            }
        }

        assertEquals(GamePhase.ENDED, table.getPhase());
        assertNotNull(table.getResult());
    }

    // ------------------------------------------------------------------
    // 自制规则：打出癞子，全场作废
    // ------------------------------------------------------------------

    private RuleConfig voidOnDiscard(boolean enabled) {
        RuleConfig config = new RuleConfig();
        int wildcard = Tiles.fromCode("5T");
        config.setWildcardEnabled(true);
        config.setWildcardMode(WildcardMode.FIXED);
        config.setFixedWildcardTile(wildcard);
        config.setWildcardTile(wildcard);
        config.setVoidWildcardOnDiscard(enabled);
        return config;
    }

    /** 找一个庄家手上有癞子的种子，返回 [牌桌, 癞子id]。 */
    private GameTable tableWhereDealerHoldsWildcard(RuleConfig config, int wildcard) {
        for (long seed = 1; seed <= 3000; seed++) {
            GameTable table = GameTable.start(config, seed, 0);
            if (table.getHand(0).contains(wildcard)) {
                return table;
            }
        }
        fail("3000 个随机种子内庄家都没摸到癞子");
        return null;
    }

    @Test
    @DisplayName("自制规则：打出癞子后全场癞子作废")
    void discardingWildcardVoidsAllWildcards() {
        int wildcard = Tiles.fromCode("5T");
        GameTable table = tableWhereDealerHoldsWildcard(voidOnDiscard(true), wildcard);

        assertFalse(table.isWildcardVoided(), "开局时不应作废");
        table.discard(0, wildcard);

        assertTrue(table.isWildcardVoided(), "打出癞子后应全场作废");
    }

    @Test
    @DisplayName("未开启规则时打出癞子不作废")
    void discardingWildcardDoesNothingWhenRuleOff() {
        int wildcard = Tiles.fromCode("5T");
        GameTable table = tableWhereDealerHoldsWildcard(voidOnDiscard(false), wildcard);

        table.discard(0, wildcard);

        assertFalse(table.isWildcardVoided());
    }

    @Test
    @DisplayName("规则开启但打出的是普通牌，不作废")
    void discardingNormalTileDoesNotVoid() {
        int wildcard = Tiles.fromCode("5T");
        GameTable table = tableWhereDealerHoldsWildcard(voidOnDiscard(true), wildcard);

        int normal = table.getHand(0).stream()
                .filter(tile -> tile != wildcard)
                .findFirst()
                .orElseThrow();
        table.discard(0, normal);

        assertFalse(table.isWildcardVoided());
    }
}
