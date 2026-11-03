package com.nanling.mahjong.engine;

import com.nanling.mahjong.common.BusinessException;
import com.nanling.mahjong.domain.game.ActionType;
import com.nanling.mahjong.domain.game.GamePhase;
import com.nanling.mahjong.domain.game.GameResult;
import com.nanling.mahjong.domain.rule.Meld;
import com.nanling.mahjong.domain.rule.MeldType;
import com.nanling.mahjong.domain.rule.RuleConfig;
import com.nanling.mahjong.domain.rule.WinChecker;
import com.nanling.mahjong.domain.rule.WinRequest;
import com.nanling.mahjong.domain.rule.WinResult;
import com.nanling.mahjong.domain.tile.Suit;
import com.nanling.mahjong.domain.tile.TileWall;
import com.nanling.mahjong.domain.tile.Tiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单桌对局状态机。
 *
 * <p>线程模型：所有公开方法由 RoomService 在同一把房间锁内调用，内部再做一次同步保护，
 * 保证“摸牌 → 出牌 → 响应 → 结算”的原子性。</p>
 */
public class GameTable {

    private final RuleConfig config;
    private final long seed;
    private final int dealerSeat;

    private final List<Integer>[] hands;
    private final List<Meld>[] melds;
    private final List<Integer>[] discards;
    private final TileWall wall;

    private GamePhase phase = GamePhase.WAITING;
    private int currentSeat;
    private int lastDiscardTile = -1;
    private int lastDiscardSeat = -1;
    private int lastDrawnTile = -1;

    private final Set<Integer> pendingSeats = new LinkedHashSet<>();
    private final Map<Integer, ActionType> pendingClaims = new LinkedHashMap<>();

    private GameResult result;
    private long deadlineAt;

    @SuppressWarnings("unchecked")
    private GameTable(RuleConfig config, long seed, int dealerSeat) {
        this.config = config.copy();
        this.seed = seed;
        this.dealerSeat = dealerSeat;
        this.hands = new List[4];
        this.melds = new List[4];
        this.discards = new List[4];
        for (int i = 0; i < 4; i++) {
            hands[i] = new ArrayList<>();
            melds[i] = new ArrayList<>();
            discards[i] = new ArrayList<>();
        }
        this.wall = TileWall.standard(seed);
    }

    public static GameTable start(RuleConfig ruleConfig, long seed, int dealerSeat) {
        GameTable table = new GameTable(ruleConfig, seed, dealerSeat);
        table.deal();
        return table;
    }

    private void deal() {
        if (config.isWildcardEnabled()) {
            switch (config.getWildcardMode()) {
                case FLIP_NEXT -> {
                    int indicator = wall.flip();
                    config.setWildcardTile(TileWall.nextTile(indicator));
                }
                case FIXED -> config.setWildcardTile(config.getFixedWildcardTile());
                case NONE -> config.setWildcardTile(null);
            }
        } else {
            config.setWildcardTile(null);
        }

        for (int round = 0; round < 13; round++) {
            for (int seat = 0; seat < 4; seat++) {
                hands[seat].add(wall.draw());
            }
        }
        int extra = wall.draw();
        hands[dealerSeat].add(extra);
        for (int seat = 0; seat < 4; seat++) {
            Collections.sort(hands[seat]);
        }

        phase = GamePhase.PLAYER_TURN;
        currentSeat = dealerSeat;
        lastDrawnTile = extra;
        deadlineAt = System.currentTimeMillis() + config.getTurnTimeoutSeconds() * 1000L;
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    public RuleConfig getConfig() {
        return config;
    }

    public long getSeed() {
        return seed;
    }

    public int getDealerSeat() {
        return dealerSeat;
    }

    public GamePhase getPhase() {
        return phase;
    }

    public int getCurrentSeat() {
        return currentSeat;
    }

    public int getLastDiscardTile() {
        return lastDiscardTile;
    }

    public int getLastDiscardSeat() {
        return lastDiscardSeat;
    }

    public int getLastDrawnTile() {
        return lastDrawnTile;
    }

    public int getRemainingTiles() {
        return wall.remaining();
    }

    public Integer getWildcardTile() {
        return config.getWildcardTile();
    }

    public GameResult getResult() {
        return result;
    }

    public long getDeadlineAt() {
        return deadlineAt;
    }

    public List<Integer> getHand(int seat) {
        return Collections.unmodifiableList(hands[seat]);
    }

    public int getHandCount(int seat) {
        return hands[seat].size();
    }

    public List<Meld> getMelds(int seat) {
        return Collections.unmodifiableList(melds[seat]);
    }

    public List<Integer> getDiscards(int seat) {
        return Collections.unmodifiableList(discards[seat]);
    }

    public Set<Integer> getPendingSeats() {
        return Collections.unmodifiableSet(pendingSeats);
    }

    public Map<Integer, ActionType> getPendingClaims() {
        return Collections.unmodifiableMap(pendingClaims);
    }

    /** 当前打出的牌，某位玩家可以做的响应。 */
    public List<ActionType> claimsFor(int seat) {
        if (phase != GamePhase.WAITING_CLAIM || lastDiscardTile < 0 || seat == lastDiscardSeat) {
            return List.of();
        }
        return claimsForTile(seat, lastDiscardTile);
    }

    /** 轮到某位玩家时，可以做的自身操作（胡 / 暗杠 / 补杠）。 */
    public List<ActionType> selfActions(int seat) {
        if (phase != GamePhase.PLAYER_TURN || seat != currentSeat) {
            return List.of();
        }
        List<ActionType> actions = new ArrayList<>();
        if (WinChecker.check(new WinRequest(hands[seat], melds[seat], config, true)).isWin()) {
            actions.add(ActionType.HU);
        }
        if (hasConcealedKong(seat)) {
            actions.add(ActionType.KONG_AN);
        }
        if (hasAddedKong(seat)) {
            actions.add(ActionType.KONG_BU);
        }
        return actions;
    }

    public boolean isTurn(int seat) {
        return phase == GamePhase.PLAYER_TURN && seat == currentSeat;
    }

    // ------------------------------------------------------------------
    // 出牌 / 响应
    // ------------------------------------------------------------------

    public synchronized void discard(int seat, int tile) {
        if (phase != GamePhase.PLAYER_TURN) {
            throw new BusinessException("当前不是出牌阶段");
        }
        if (seat != currentSeat) {
            throw new BusinessException("还没轮到你出牌");
        }
        int index = hands[seat].indexOf(tile);
        if (index < 0) {
            throw new BusinessException("手牌中没有这张牌");
        }
        hands[seat].remove(index);
        discards[seat].add(tile);
        lastDiscardTile = tile;
        lastDiscardSeat = seat;
        lastDrawnTile = -1;
        pendingSeats.clear();
        pendingClaims.clear();

        for (int other = 0; other < 4; other++) {
            if (other != seat && !claimsForTile(other, tile).isEmpty()) {
                pendingSeats.add(other);
            }
        }
        if (pendingSeats.isEmpty()) {
            advanceToNext(seat);
            return;
        }
        phase = GamePhase.WAITING_CLAIM;
        deadlineAt = System.currentTimeMillis() + config.getActionTimeoutSeconds() * 1000L;
    }

    public synchronized void claim(int seat, ActionType type) {
        if (phase != GamePhase.WAITING_CLAIM) {
            throw new BusinessException("当前不是碰杠胡响应阶段");
        }
        if (!pendingSeats.contains(seat)) {
            throw new BusinessException("你当前没有可执行的响应");
        }
        if (type == ActionType.PASS) {
            pendingClaims.put(seat, ActionType.PASS);
        } else {
            if (!claimsForTile(seat, lastDiscardTile).contains(type)) {
                throw new BusinessException("不能执行该操作");
            }
            pendingClaims.put(seat, type);
        }

        if (type == ActionType.HU) {
            resolveClaims();
            return;
        }
        if (pendingClaims.size() >= pendingSeats.size()) {
            resolveClaims();
        }
    }

    public synchronized void pass(int seat) {
        claim(seat, ActionType.PASS);
    }

    // ------------------------------------------------------------------
    // 自身操作
    // ------------------------------------------------------------------

    public synchronized void selfKongAn(int seat, int tile) {
        requireTurn(seat);
        if (Collections.frequency(hands[seat], tile) < 4) {
            throw new BusinessException("手上不足四张，不能暗杠");
        }
        removeTiles(seat, tile, 4);
        melds[seat].add(Meld.kongAn(tile));
        supplementaryDraw(seat);
    }

    public synchronized void selfKongBu(int seat, int tile) {
        requireTurn(seat);
        int meldIndex = indexOfPong(seat, tile);
        if (meldIndex < 0) {
            throw new BusinessException("没有可补杠的碰");
        }
        if (!hands[seat].contains(tile)) {
            throw new BusinessException("手上没有这张牌，不能补杠");
        }
        removeTiles(seat, tile, 1);
        melds[seat].set(meldIndex, Meld.kongBu(tile));
        supplementaryDraw(seat);
    }

    public synchronized void selfHu(int seat) {
        requireTurn(seat);
        WinResult winResult = WinChecker.check(new WinRequest(hands[seat], melds[seat], config, true));
        if (!winResult.isWin()) {
            throw new BusinessException("当前不能胡：" + winResult.getReason());
        }
        finish(seat, -1, winResult);
    }

    // ------------------------------------------------------------------
    // 超时处理
    // ------------------------------------------------------------------

    /** 超时：响应阶段全部视为过；出牌阶段自动打出最后摸到的牌。 */
    public synchronized void onTimeout() {
        if (phase == GamePhase.WAITING_CLAIM) {
            for (int seat : pendingSeats) {
                pendingClaims.putIfAbsent(seat, ActionType.PASS);
            }
            resolveClaims();
            return;
        }
        if (phase == GamePhase.PLAYER_TURN) {
            List<Integer> hand = hands[currentSeat];
            if (hand.isEmpty()) {
                return;
            }
            int tile = lastDrawnTile >= 0 && hand.contains(lastDrawnTile)
                    ? lastDrawnTile
                    : hand.get(hand.size() - 1);
            discard(currentSeat, tile);
        }
    }

    // ------------------------------------------------------------------
    // 内部流程
    // ------------------------------------------------------------------

    private void resolveClaims() {
        for (int seat : seatsAfter(lastDiscardSeat)) {
            if (pendingClaims.get(seat) == ActionType.HU) {
                doHu(seat, lastDiscardTile, false, lastDiscardSeat);
                return;
            }
        }
        for (int seat : seatsAfter(lastDiscardSeat)) {
            if (pendingClaims.get(seat) == ActionType.KONG_MING) {
                doKongMing(seat, lastDiscardTile);
                return;
            }
        }
        for (int seat : seatsAfter(lastDiscardSeat)) {
            if (pendingClaims.get(seat) == ActionType.PONG) {
                doPong(seat, lastDiscardTile);
                return;
            }
        }
        int discarder = lastDiscardSeat;
        clearPending();
        advanceToNext(discarder);
    }

    private void doPong(int seat, int tile) {
        removeTiles(seat, tile, 2);
        melds[seat].add(Meld.pong(tile, lastDiscardSeat));
        consumeLastDiscard();
        clearPending();
        currentSeat = seat;
        lastDrawnTile = -1;
        phase = GamePhase.PLAYER_TURN;
        deadlineAt = System.currentTimeMillis() + config.getTurnTimeoutSeconds() * 1000L;
    }

    private void doKongMing(int seat, int tile) {
        removeTiles(seat, tile, 3);
        melds[seat].add(Meld.kongMing(tile, lastDiscardSeat));
        consumeLastDiscard();
        clearPending();
        currentSeat = seat;
        lastDrawnTile = -1;
        supplementaryDraw(seat);
    }

    private void doHu(int seat, int tile, boolean selfDraw, int loserSeat) {
        List<Integer> copy = new ArrayList<>(hands[seat]);
        copy.add(tile);
        WinResult winResult = WinChecker.check(new WinRequest(copy, melds[seat], config, selfDraw));
        if (!winResult.isWin()) {
            throw new BusinessException("胡牌判定失败：" + winResult.getReason());
        }
        finish(seat, loserSeat, winResult);
    }

    private void finish(int winnerSeat, int loserSeat, WinResult winResult) {
        this.result = new GameResult(false, winnerSeat, loserSeat, winResult.getFan(), winResult.getScore(),
                winResult.getFlavorLabels(), winResult.getWinningHand());
        this.phase = GamePhase.ENDED;
        this.pendingSeats.clear();
        this.pendingClaims.clear();
    }

    private void supplementaryDraw(int seat) {
        if (wall.isEmpty()) {
            endDraw();
            return;
        }
        int tile = wall.drawFromTail();
        hands[seat].add(tile);
        Collections.sort(hands[seat]);
        lastDrawnTile = tile;
        phase = GamePhase.PLAYER_TURN;
        deadlineAt = System.currentTimeMillis() + config.getTurnTimeoutSeconds() * 1000L;
    }

    private void advanceToNext(int seat) {
        int next = (seat + 1) % 4;
        if (wall.isEmpty()) {
            endDraw();
            return;
        }
        int tile = wall.draw();
        hands[next].add(tile);
        Collections.sort(hands[next]);
        currentSeat = next;
        lastDrawnTile = tile;
        lastDiscardTile = -1;
        lastDiscardSeat = -1;
        phase = GamePhase.PLAYER_TURN;
        deadlineAt = System.currentTimeMillis() + config.getTurnTimeoutSeconds() * 1000L;
    }

    private void endDraw() {
        this.result = GameResult.draw();
        this.phase = GamePhase.ENDED;
        clearPending();
    }

    private void clearPending() {
        pendingSeats.clear();
        pendingClaims.clear();
    }

    private void consumeLastDiscard() {
        if (lastDiscardSeat >= 0) {
            List<Integer> pile = discards[lastDiscardSeat];
            if (!pile.isEmpty()) {
                pile.remove(pile.size() - 1);
            }
        }
        lastDiscardTile = -1;
        lastDiscardSeat = -1;
    }

    private List<ActionType> claimsForTile(int seat, int tile) {
        List<ActionType> actions = new ArrayList<>();
        List<Integer> copy = new ArrayList<>(hands[seat]);
        copy.add(tile);
        if (WinChecker.check(new WinRequest(copy, melds[seat], config, false)).isWin()) {
            actions.add(ActionType.HU);
        }
        int count = Collections.frequency(hands[seat], tile);
        if (count >= 3) {
            actions.add(ActionType.KONG_MING);
        }
        if (count >= 2) {
            actions.add(ActionType.PONG);
        }
        return actions;
    }

    private boolean hasConcealedKong(int seat) {
        int[] counts = new int[Tiles.KIND_COUNT];
        for (int tile : hands[seat]) {
            counts[tile]++;
        }
        for (int count : counts) {
            if (count == 4) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAddedKong(int seat) {
        for (Meld meld : melds[seat]) {
            if (meld.getType() == MeldType.PONG && hands[seat].contains(meld.baseTile())) {
                return true;
            }
        }
        return false;
    }

    private int indexOfPong(int seat, int tile) {
        List<Meld> list = melds[seat];
        for (int i = 0; i < list.size(); i++) {
            Meld meld = list.get(i);
            if (meld.getType() == MeldType.PONG && meld.baseTile() == tile) {
                return i;
            }
        }
        return -1;
    }

    private void removeTiles(int seat, int tile, int count) {
        for (int i = 0; i < count; i++) {
            int index = hands[seat].indexOf(tile);
            if (index < 0) {
                throw new BusinessException("手牌数量不足");
            }
            hands[seat].remove(index);
        }
    }

    private void requireTurn(int seat) {
        if (phase != GamePhase.PLAYER_TURN) {
            throw new BusinessException("当前不是你的操作阶段");
        }
        if (seat != currentSeat) {
            throw new BusinessException("还没轮到你");
        }
    }

    private List<Integer> seatsAfter(int seat) {
        List<Integer> order = new ArrayList<>(3);
        for (int i = 1; i <= 3; i++) {
            order.add((seat + i) % 4);
        }
        return order;
    }

    /** 用于调试/测试：手牌中文展示。 */
    public String describeHand(int seat) {
        StringBuilder sb = new StringBuilder();
        for (int tile : hands[seat]) {
            sb.append(Tiles.display(tile)).append(' ');
        }
        return sb.toString().trim();
    }

    /** 某门花色的张数（含癞子解析前的实牌），用于前端提示。 */
    public int countSuit(int seat, Suit suit) {
        int count = 0;
        for (int tile : hands[seat]) {
            if (Tiles.suitOf(tile) == suit && !config.isWildcard(tile)) {
                count++;
            }
        }
        return count;
    }
}
