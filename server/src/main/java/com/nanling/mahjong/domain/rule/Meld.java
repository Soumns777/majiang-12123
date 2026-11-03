package com.nanling.mahjong.domain.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 已亮出/已固定的面子。
 */
public class Meld {

    private final MeldType type;
    private final List<Integer> tiles;
    /** 供牌者座位，-1 表示自己（暗杠、补杠） */
    private final int fromSeat;

    private Meld(MeldType type, List<Integer> tiles, int fromSeat) {
        this.type = type;
        this.tiles = Collections.unmodifiableList(new ArrayList<>(tiles));
        this.fromSeat = fromSeat;
    }

    public static Meld chow(int a, int b, int c, int fromSeat) {
        List<Integer> list = new ArrayList<>(List.of(a, b, c));
        Collections.sort(list);
        return new Meld(MeldType.CHOW, list, fromSeat);
    }

    public static Meld pong(int tile, int fromSeat) {
        return new Meld(MeldType.PONG, List.of(tile, tile, tile), fromSeat);
    }

    public static Meld kongMing(int tile, int fromSeat) {
        return new Meld(MeldType.KONG_MING, List.of(tile, tile, tile, tile), fromSeat);
    }

    public static Meld kongAn(int tile) {
        return new Meld(MeldType.KONG_AN, List.of(tile, tile, tile, tile), -1);
    }

    public static Meld kongBu(int tile) {
        return new Meld(MeldType.KONG_BU, List.of(tile, tile, tile, tile), -1);
    }

    public MeldType getType() {
        return type;
    }

    public List<Integer> getTiles() {
        return tiles;
    }

    public int getFromSeat() {
        return fromSeat;
    }

    /** 明牌（所有人可见）；暗杠只对自己可见。 */
    public boolean isExposed() {
        return type != MeldType.KONG_AN;
    }

    /** 面子基准牌：顺子取最小牌，刻子/杠取该牌。 */
    public int baseTile() {
        return tiles.get(0);
    }

    /** 该面子物理张数（杠为 4）。 */
    public int physicalSize() {
        return tiles.size();
    }

    public String display() {
        StringBuilder sb = new StringBuilder();
        for (int tile : tiles) {
            sb.append(com.nanling.mahjong.domain.tile.Tiles.display(tile));
        }
        return sb.toString();
    }
}
