package com.nanling.mahjong.engine;

import com.nanling.mahjong.common.BusinessException;
import com.nanling.mahjong.domain.game.ActionType;
import com.nanling.mahjong.domain.game.GamePhase;
import com.nanling.mahjong.domain.rule.RuleConfig;
import com.nanling.mahjong.domain.rule.WildcardMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 只允许自摸 + 延时申请。
 */
class ExtendAndSelfDrawTest {

    private RuleConfig config() {
        RuleConfig config = new RuleConfig();
        config.setWildcardEnabled(false);
        config.setWildcardMode(WildcardMode.NONE);
        return config;
    }

    // ------------------------------------------------------------------
    // 只允许自摸
    // ------------------------------------------------------------------

    @Test
    @DisplayName("打出的牌永远不能被别人胡：整局里响应列表都不含 HU")
    void discardIsNeverClaimableAsWin() {
        GameTable table = GameTable.start(config(), 20261104L, 0);
        int claimPhases = 0;

        for (int step = 0; step < 400 && table.getPhase() != GamePhase.ENDED; step++) {
            if (table.getPhase() == GamePhase.PLAYER_TURN) {
                int seat = table.getCurrentSeat();
                List<Integer> hand = table.getHand(seat);
                table.discard(seat, hand.get(hand.size() - 1));
                continue;
            }
            if (table.getPhase() == GamePhase.WAITING_CLAIM) {
                claimPhases++;
                for (int seat : table.getPendingSeats()) {
                    assertFalse(table.claimsFor(seat).contains(ActionType.HU),
                            "南陵麻将不允许胡别人打出的牌，座位 " + seat + " 不该出现 HU");
                }
                for (int seat : List.copyOf(table.getPendingSeats())) {
                    if (table.getPhase() == GamePhase.WAITING_CLAIM) {
                        table.pass(seat);
                    }
                }
            }
        }
        assertTrue(claimPhases > 0, "整局都没出现过响应阶段，用例没覆盖到");
    }

    @Test
    @DisplayName("响应阶段直接发 HU 会被拒绝，并提示只能自摸")
    void claimHuIsRejected() {
        GameTable table = GameTable.start(config(), 20261104L, 0);
        for (int step = 0; step < 400 && table.getPhase() != GamePhase.ENDED; step++) {
            if (table.getPhase() == GamePhase.PLAYER_TURN) {
                int seat = table.getCurrentSeat();
                List<Integer> hand = table.getHand(seat);
                table.discard(seat, hand.get(hand.size() - 1));
                continue;
            }
            int seat = table.getPendingSeats().iterator().next();
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> table.claim(seat, ActionType.HU));
            assertTrue(ex.getMessage().contains("只能自摸"), ex.getMessage());
            for (int pending : List.copyOf(table.getPendingSeats())) {
                if (table.getPhase() == GamePhase.WAITING_CLAIM) {
                    table.pass(pending);
                }
            }
            return;
        }
        throw new AssertionError("整局都没进入响应阶段");
    }

    // ------------------------------------------------------------------
    // 延时申请
    // ------------------------------------------------------------------

    @Test
    @DisplayName("申请延时先冻结时间，三家都同意后延长一个基础时长")
    void extensionNeedsThreeApprovals() {
        GameTable table = GameTable.start(config(), 20261104L, 0);
        long before = table.getDeadlineAt();

        table.requestExtension(0);
        assertTrue(table.isExtensionPending());
        assertEquals(0, table.getExtendSeat());
        assertTrue(table.getDeadlineAt() > before, "投票窗口内先把截止时间推后，避免边投票边超时");

        table.voteExtension(1, true);
        table.voteExtension(2, true);
        assertTrue(table.isExtensionPending(), "还差一家同意，不能生效");

        table.voteExtension(3, true);
        assertFalse(table.isExtensionPending(), "三家同意后申请结束");
        long remaining = table.getDeadlineAt() - System.currentTimeMillis();
        assertTrue(remaining > 30_000, "剩余时间应接近「原剩余 20s + 基础 20s」，实际 " + remaining);
    }

    @Test
    @DisplayName("任意一家不同意就恢复原来的剩余时间")
    void singleVetoRestoresDeadline() {
        GameTable table = GameTable.start(config(), 20261104L, 0);
        table.requestExtension(0);

        table.voteExtension(2, false);
        assertFalse(table.isExtensionPending());
        long remaining = table.getDeadlineAt() - System.currentTimeMillis();
        assertTrue(remaining <= 21_000, "应该只还回原来的 20 秒，实际 " + remaining);
    }

    @Test
    @DisplayName("不能给自己投票，也不能替别人申请")
    void invalidRequests() {
        GameTable table = GameTable.start(config(), 20261104L, 0);
        assertThrows(BusinessException.class, () -> table.requestExtension(2));

        table.requestExtension(0);
        BusinessException ex = assertThrows(BusinessException.class, () -> table.voteExtension(0, true));
        assertTrue(ex.getMessage().contains("不能给自己"), ex.getMessage());
    }

    @Test
    @DisplayName("出牌后延时申请自动失效，不会带到下一家")
    void extensionClearedAfterDiscard() {
        GameTable table = GameTable.start(config(), 20261104L, 0);
        table.requestExtension(0);
        List<Integer> hand = table.getHand(0);
        table.discard(0, hand.get(hand.size() - 1));

        assertFalse(table.isExtensionPending());
    }
}
