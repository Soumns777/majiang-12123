package com.nanling.mahjong.room;

import com.nanling.mahjong.api.dto.MeldView;
import com.nanling.mahjong.api.dto.PlayerView;
import com.nanling.mahjong.api.dto.TableView;
import com.nanling.mahjong.api.dto.ViewMapper;
import com.nanling.mahjong.common.BusinessException;
import com.nanling.mahjong.domain.game.ActionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 满员房间的观战能力。
 *
 * <p>核心约束：观战者能进、能看、能走，但看不到任何人的手牌，也不能出牌。</p>
 */
class RoomSpectatorTest {

    private RoomService service;
    private Room room;

    private static final String[] SEATS = {"p0", "p1", "p2", "p3"};

    @BeforeEach
    void setUp() {
        ApplicationEventPublisher noop = event -> { };
        service = new RoomService(new InMemoryRoomRepository(), new ViewMapper(), noop,
                1, 8, 15, 20, 1800, 0, 0.0);
        room = service.createRoom(SEATS[0], "房主", "", null);
        for (int i = 1; i < SEATS.length; i++) {
            service.joinRoom(room.getId(), SEATS[i], "玩家" + i, "");
            service.setReady(room.getId(), SEATS[i], true);
        }
        service.setReady(room.getId(), SEATS[0], true);
    }

    @Test
    @DisplayName("房间坐满 4 人后，第 5 个人用房间号进来变成观战而不是报错")
    void fifthPlayerBecomesSpectator() {
        service.joinRoom(room.getId(), "watcher", "围观群众", "");

        assertEquals(4, room.getPlayers().size());
        assertEquals(1, room.getSpectators().size());
        assertTrue(room.isSpectator("watcher"));
        assertEquals("围观群众", room.getSpectators().get(0).getNickname());
    }

    @Test
    @DisplayName("对局开始后，其他人依然可以用房间号进来观战")
    void canWatchAfterGameStarted() {
        service.startGame(room.getId(), SEATS[0]);

        service.joinRoom(room.getId(), "watcher", "围观群众", "");

        assertTrue(room.isSpectator("watcher"));
        TableView view = service.viewFor(room.getId(), "watcher");
        assertTrue(view.spectator());
        assertEquals("PLAYING", view.status());
    }

    @Test
    @DisplayName("观战视图里所有人的手牌都是空的，只暴露张数")
    void spectatorViewHidesEveryHand() {
        service.startGame(room.getId(), SEATS[0]);
        service.joinRoom(room.getId(), "watcher", "围观群众", "");

        TableView view = service.viewFor(room.getId(), "watcher");
        assertEquals(4, view.players().size());
        for (PlayerView player : view.players()) {
            assertTrue(player.hand().isEmpty(), "观战者不应该看到任何手牌");
            assertTrue(player.actions().isEmpty(), "观战者不应该有可用操作");
            assertTrue(player.handCount() > 0, "手牌张数仍然要能看到");
        }
        // 暗杠对观战者同样不可见
        assertTrue(view.players().stream()
                .flatMap(p -> p.melds().stream())
                .allMatch(MeldView::exposed));
    }

    @Test
    @DisplayName("观战者不能出牌，报错信息要说明是观战身份")
    void spectatorCannotAct() {
        service.startGame(room.getId(), SEATS[0]);
        service.joinRoom(room.getId(), "watcher", "围观群众", "");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.action(room.getId(), "watcher", ActionType.DISCARD, 0));
        assertTrue(ex.getMessage().contains("观战"));
    }

    @Test
    @DisplayName("对局进行中观战者可以随时退出，玩家却不行")
    void spectatorCanLeaveDuringPlay() {
        service.startGame(room.getId(), SEATS[0]);
        service.joinRoom(room.getId(), "watcher", "围观群众", "");

        service.leaveRoom(room.getId(), "watcher");
        assertEquals(0, room.getSpectators().size());
        assertFalse(room.isSpectator("watcher"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.leaveRoom(room.getId(), SEATS[1]));
        assertTrue(ex.getMessage().contains("不能退出"));
    }

    @Test
    @DisplayName("主动观战接口不会占用空座位")
    void watchNeverTakesSeat() {
        // 此时还空着 0 号以外的座位？——房主 + 3 人已经满了，改用一个新房间验证
        Room fresh = service.createRoom("solo", "独坐", "", null);
        service.watchRoom(fresh.getId(), "watcher", "围观群众", "");

        assertEquals(1, fresh.getPlayers().size());
        assertEquals(1, fresh.getSpectators().size());
        assertNotNull(fresh.getSpectators().get(0));
    }

    @Test
    @DisplayName("玩家在房间里时不会被降级成观战")
    void existingPlayerIsNotDowngraded() {
        service.joinRoom(room.getId(), SEATS[1], "玩家1", "");

        assertFalse(room.isSpectator(SEATS[1]));
        assertEquals(0, room.getSpectators().size());
    }
}
