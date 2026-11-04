package com.nanling.mahjong.room;

import com.nanling.mahjong.api.dto.ViewMapper;
import com.nanling.mahjong.common.BusinessException;
import com.nanling.mahjong.domain.game.ActionType;
import com.nanling.mahjong.domain.game.GamePhase;
import com.nanling.mahjong.engine.GameTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 机器人代打。
 *
 * <p>要守住的两条：</p>
 * <ol>
 *   <li>机器人能补满座位、自动准备，让房主一个人也能开局；</li>
 *   <li><b>打牌必须依次来</b>——一个 tick 只允许一个机器人做一个动作，
 *       不能在一瞬间把整局牌打完。</li>
 * </ol>
 */
class BotTurnTest {

    private RoomService service;
    private Room room;
    private static final String OWNER = "p0";

    @BeforeEach
    void setUp() {
        ApplicationEventPublisher noop = event -> { };
        // think-ms=0 + 碰牌概率=0：每次 tick 都能推进一个机器人，且行为完全确定
        service = new RoomService(new InMemoryRoomRepository(), new ViewMapper(), noop,
                1, 8, 15, 20, 1800, 0, 0.0);
        room = service.createRoom(OWNER, "房主", "", null);
    }

    @Test
    @DisplayName("房主补 3 个机器人后可以一个人开局")
    void ownerCanStartWithBots() {
        service.addBots(room.getId(), OWNER, 3);
        service.setReady(room.getId(), OWNER, true);

        assertEquals(4, room.getPlayers().size());
        assertTrue(room.allReady());
        assertTrue(room.getPlayers().stream().filter(Player::isBot).count() == 3);

        service.startGame(room.getId(), OWNER);
        assertEquals(RoomStatus.PLAYING, room.getStatus());
        assertEquals(GamePhase.PLAYER_TURN, room.getTable().getPhase());
    }

    @Test
    @DisplayName("机器人自动占满空座位，座位号不重复")
    void botsFillFreeSeats() {
        service.addBots(room.getId(), OWNER, 3);
        List<Integer> seats = room.sortedPlayers().stream().map(Player::getSeat).toList();
        assertEquals(List.of(0, 1, 2, 3), seats);
        assertTrue(room.getPlayers().stream()
                .filter(Player::isBot)
                .allMatch(player -> player.isReady() && player.isOnline() && player.getSessionId() == null));
    }

    @Test
    @DisplayName("座位已满时再加机器人会报错")
    void cannotAddBotsWhenFull() {
        service.addBots(room.getId(), OWNER, 3);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.addBots(room.getId(), OWNER, 1));
        assertTrue(ex.getMessage().contains("座位已满"));
    }

    @Test
    @DisplayName("只有房主可以加机器人")
    void onlyOwnerCanAddBots() {
        service.joinRoom(room.getId(), "p1", "玩家1", "");
        assertThrows(BusinessException.class, () -> service.addBots(room.getId(), "p1", 2));
    }

    @Test
    @DisplayName("开局后不能再加机器人")
    void cannotAddBotsAfterStart() {
        service.addBots(room.getId(), OWNER, 3);
        service.setReady(room.getId(), OWNER, true);
        service.startGame(room.getId(), OWNER);
        assertThrows(BusinessException.class, () -> service.addBots(room.getId(), OWNER, 1));
    }

    @Test
    @DisplayName("机器人依次出牌：每次 tick 最多推进一个动作")
    void botsActOneByOne() {
        service.addBots(room.getId(), OWNER, 3);
        service.setReady(room.getId(), OWNER, true);
        service.startGame(room.getId(), OWNER);

        GameTable table = room.getTable();
        // 庄家是 0 号位（房主），先由真人打一张，把牌权交给机器人
        assertEquals(0, table.getCurrentSeat());
        service.action(room.getId(), OWNER, ActionType.DISCARD, table.getHand(0).get(0));
        assertEquals(1, actionCount(table));

        int previous = actionCount(table);
        int advanced = 0;
        for (int i = 0; i < 8; i++) {
            service.tick();
            GameTable current = room.getTable();
            if (current.getPhase() == GamePhase.ENDED) {
                break;
            }
            int now = actionCount(current);
            // 核心断言：一次 tick 不可能同时冒出两个动作（出牌 / 碰 / 杠 都算）
            assertTrue(now - previous <= 1,
                    "第 " + i + " 次 tick 推进了 " + (now - previous) + " 个动作，机器人没有依次打牌");
            if (now > previous) {
                advanced++;
            }
            previous = now;
        }
        assertTrue(advanced >= 3, "8 次 tick 只推进了 " + advanced + " 步，机器人没有在打牌");
    }

    @Test
    @DisplayName("轮不到机器人时不动作：真人在出牌阶段牌桌保持不变")
    void botsWaitForHuman() {
        service.addBots(room.getId(), OWNER, 3);
        service.setReady(room.getId(), OWNER, true);
        service.startGame(room.getId(), OWNER);

        int before = actionCount(room.getTable());
        for (int i = 0; i < 5; i++) {
            service.tick();
        }
        assertEquals(before, actionCount(room.getTable()));
        assertEquals(0, room.getTable().getCurrentSeat());
        assertFalse(room.getTable().isTurn(1));
    }

    /** 出牌堆张数 + 副露组数，用来衡量「动作推进了多少步」。 */
    private int actionCount(GameTable table) {
        int count = 0;
        for (int seat = 0; seat < 4; seat++) {
            count += table.getDiscards(seat).size();
            count += table.getMelds(seat).size();
        }
        return count;
    }
}
