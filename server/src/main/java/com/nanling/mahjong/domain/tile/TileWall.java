package com.nanling.mahjong.domain.tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 牌墙：136 张（34 种 × 4 张），洗牌后可从头摸牌、从尾摸牌（杠后补牌）。
 */
public class TileWall {

    private final List<Integer> tiles;
    private int head;
    private int tail;

    private TileWall(List<Integer> tiles) {
        this.tiles = tiles;
        this.head = 0;
        this.tail = tiles.size() - 1;
    }

    public static TileWall standard(long seed) {
        List<Integer> list = new ArrayList<>(136);
        for (int kind = 0; kind < Tiles.KIND_COUNT; kind++) {
            for (int copy = 0; copy < 4; copy++) {
                list.add(kind);
            }
        }
        Collections.shuffle(list, new Random(seed));
        return new TileWall(list);
    }

    /** 用于单元测试：指定牌序。 */
    public static TileWall of(List<Integer> orderedTiles) {
        return new TileWall(new ArrayList<>(orderedTiles));
    }

    public boolean isEmpty() {
        return head > tail;
    }

    public int remaining() {
        return Math.max(0, tail - head + 1);
    }

    public int draw() {
        if (isEmpty()) {
            throw new IllegalStateException("牌墙已空");
        }
        return tiles.get(head++);
    }

    /** 杠后从牌尾补牌。 */
    public int drawFromTail() {
        if (isEmpty()) {
            throw new IllegalStateException("牌墙已空");
        }
        return tiles.get(tail--);
    }

    /** 翻宝：取牌尾一张作为指示牌并移出牌墙。 */
    public int flip() {
        return drawFromTail();
    }

    /** 翻宝规则：同花色顺延一张，9 之后回到 1；字牌按东南西北中发白循环。 */
    public static int nextTile(int tile) {
        Tiles.check(tile);
        if (Tiles.isHonor(tile)) {
            int offset = tile - Tiles.HONOR_BASE;
            return Tiles.HONOR_BASE + (offset + 1) % 7;
        }
        Suit suit = Tiles.suitOf(tile);
        int rank = Tiles.rankOf(tile);
        int nextRank = rank % 9 + 1;
        return Tiles.of(suit, nextRank);
    }
}
