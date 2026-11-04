package com.nanling.mahjong.room;

import com.nanling.mahjong.domain.game.GameResult;
import com.nanling.mahjong.domain.rule.Flavor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 欢乐豆结算：三家付 + 头家 ±1。
 */
class SettlementTest {

    private List<Player> players;

    @BeforeEach
    void setUp() {
        players = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            Player player = new Player("p" + seat, "玩家" + seat, "", seat);
            player.setBeans(20);
            players.add(player);
        }
    }

    private GameResult win(int winnerSeat, int fan, int score) {
        return new GameResult(false, winnerSeat, -1, fan, score,
                List.of(Flavor.PING_HU), List.of());
    }

    @Test
    @DisplayName("闲家自摸：赢家 +3×单家分，其余三家各 -单家分")
    void nonDealerWins() {
        // 座位 1 胡牌，庄家是座位 0；牌型分 4，底分 1 → 单家付 4
        RoomService.applySettlement(players, win(1, 4, 4), 0);

        assertEquals(32, players.get(1).getBeans(), "赢家 20 + 3×4");
        assertEquals(15, players.get(0).getBeans(), "庄家输牌多扣 1：20 - 4 - 1 = 15");
        assertEquals(16, players.get(2).getBeans());
        assertEquals(16, players.get(3).getBeans());
    }

    @Test
    @DisplayName("庄家自摸：赢家额外 +1")
    void dealerWins() {
        RoomService.applySettlement(players, win(0, 3, 3), 0);

        assertEquals(20 + 9 + 1, players.get(0).getBeans(), "赢家 20 + 3×3 + 头家 1");
        assertEquals(17, players.get(1).getBeans());
        assertEquals(17, players.get(2).getBeans());
        assertEquals(17, players.get(3).getBeans());
    }

    @Test
    @DisplayName("底分 2 时单家付翻倍：牌型分 3 → 每家付 6")
    void baseScoreDoublesPayment() {
        RoomService.applySettlement(players, win(2, 3, 6), 0);

        assertEquals(20 + 18, players.get(2).getBeans());
        assertEquals(13, players.get(0).getBeans(), "庄家 20 - 6 - 1 = 13");
        assertEquals(14, players.get(1).getBeans(), "20 - 6");
        assertEquals(14, players.get(3).getBeans(), "20 - 6");
    }

    @Test
    @DisplayName("荒庄不结算")
    void drawGameChangesNothing() {
        RoomService.applySettlement(players, GameResult.draw(), 0);

        players.forEach(player -> assertEquals(20, player.getBeans()));
    }
}
