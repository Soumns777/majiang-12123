package com.nanling.mahjong.api.dto;

import com.nanling.mahjong.domain.game.ActionType;
import com.nanling.mahjong.domain.game.GamePhase;
import com.nanling.mahjong.domain.game.GameResult;
import com.nanling.mahjong.domain.rule.Meld;
import com.nanling.mahjong.domain.rule.RuleConfig;
import com.nanling.mahjong.domain.tile.Tiles;
import com.nanling.mahjong.engine.GameTable;
import com.nanling.mahjong.room.Player;
import com.nanling.mahjong.room.Room;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 领域对象 → 前端视图。
 *
 * <p>关键职责：按玩家视角裁剪信息，别人手牌只暴露张数。</p>
 */
@Component
public class ViewMapper {

    public TableView toView(Room room, String viewerId) {
        GameTable table = room.getTable();
        int viewerSeat = room.findPlayer(viewerId).map(Player::getSeat).orElse(-1);

        List<PlayerView> playerViews = new ArrayList<>(4);
        for (int seat = 0; seat < 4; seat++) {
            Player player = room.findBySeat(seat).orElse(null);
            if (player == null) {
                playerViews.add(emptySeat(seat));
                continue;
            }
            playerViews.add(toPlayerView(room, table, player, viewerSeat));
        }

        return new TableView(
                room.getId(),
                room.getOwnerId(),
                room.getStatus().name(),
                room.getRound(),
                room.getRuleConfig().getMaxRounds(),
                table == null ? GamePhase.WAITING.name() : table.getPhase().name(),
                table == null ? -1 : table.getCurrentSeat(),
                table == null ? 0 : table.getRemainingTiles(),
                wildcardCode(table),
                wildcardLabel(table),
                lastDiscardCode(table),
                table == null ? -1 : table.getLastDiscardSeat(),
                table == null ? 0 : table.getDeadlineAt(),
                System.currentTimeMillis(),
                playerViews,
                toResultView(table == null ? null : table.getResult())
        );
    }

    private PlayerView emptySeat(int seat) {
        return new PlayerView(seat, "", "等待加入", "", 0, false, false, false, false, 0,
                List.of(), List.of(), List.of(), List.of());
    }

    private PlayerView toPlayerView(Room room, GameTable table, Player player, int viewerSeat) {
        int seat = player.getSeat();
        boolean isSelf = seat == viewerSeat;

        List<String> hand = new ArrayList<>();
        List<MeldView> melds = new ArrayList<>();
        List<String> discards = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        int handCount = 0;
        boolean dealer = false;
        boolean current = false;

        if (table != null) {
            handCount = table.getHandCount(seat);
            dealer = table.getDealerSeat() == seat;
            current = table.getCurrentSeat() == seat;
            if (isSelf) {
                table.getHand(seat).forEach(tile -> hand.add(Tiles.code(tile)));
            }
            for (Meld meld : table.getMelds(seat)) {
                melds.add(toMeldView(meld, isSelf));
            }
            table.getDiscards(seat).forEach(tile -> discards.add(Tiles.code(tile)));
            if (isSelf) {
                for (ActionType action : availableActions(table, seat)) {
                    actions.add(action.name());
                }
            }
        } else {
            handCount = 0;
            dealer = seat == 0;
        }

        return new PlayerView(seat, player.getId(), player.getNickname(), player.getAvatar(),
                player.getScore(), player.isReady(), player.isOnline(), dealer, current,
                handCount, hand, melds, discards, actions);
    }

    private List<ActionType> availableActions(GameTable table, int seat) {
        if (table.getPhase() == GamePhase.PLAYER_TURN && table.getCurrentSeat() == seat) {
            List<ActionType> actions = new ArrayList<>(table.selfActions(seat));
            actions.add(ActionType.DISCARD);
            return actions;
        }
        if (table.getPhase() == GamePhase.WAITING_CLAIM && table.getPendingSeats().contains(seat)) {
            List<ActionType> actions = new ArrayList<>(table.claimsFor(seat));
            actions.add(ActionType.PASS);
            return actions;
        }
        return List.of();
    }

    private MeldView toMeldView(Meld meld, boolean isSelf) {
        boolean visible = meld.isExposed() || isSelf;
        List<String> tiles = new ArrayList<>();
        if (visible) {
            meld.getTiles().forEach(tile -> tiles.add(Tiles.code(tile)));
        }
        return new MeldView(meld.getType().name(), tiles, meld.getFromSeat(), visible);
    }

    private ResultView toResultView(GameResult result) {
        if (result == null) {
            return null;
        }
        List<String> hand = new ArrayList<>();
        result.winningHand().forEach(tile -> hand.add(Tiles.code(tile)));
        return new ResultView(result.drawGame(), result.winnerSeat(), result.loserSeat(),
                result.fan(), result.score(), result.flavors(), hand);
    }

    private String wildcardCode(GameTable table) {
        if (table == null) {
            return null;
        }
        RuleConfig config = table.getConfig();
        Integer tile = config.getWildcardTile();
        return tile == null ? null : Tiles.code(tile);
    }

    private String wildcardLabel(GameTable table) {
        if (table == null) {
            return "无";
        }
        return table.getConfig().getWildcardDisplay();
    }

    private String lastDiscardCode(GameTable table) {
        if (table == null || table.getLastDiscardTile() < 0) {
            return null;
        }
        return Tiles.code(table.getLastDiscardTile());
    }
}
