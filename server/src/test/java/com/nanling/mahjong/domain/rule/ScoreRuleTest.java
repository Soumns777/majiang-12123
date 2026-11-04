package com.nanling.mahjong.domain.rule;

import com.nanling.mahjong.domain.tile.Tiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 计分规则（对应《芜湖麻将-南陵》第七节「计分规则」）。
 *
 * <p>牌型分可叠加，唯一互斥的是清一色与混一色；单家应付 = 牌型分 × 底分。</p>
 */
class ScoreRuleTest {

    private RuleConfig config(String wildcardCode) {
        RuleConfig config = new RuleConfig();
        if (wildcardCode == null) {
            config.setWildcardEnabled(false);
            config.setWildcardMode(WildcardMode.NONE);
            config.setWildcardTile(null);
        } else {
            int tile = Tiles.fromCode(wildcardCode);
            config.setWildcardEnabled(true);
            config.setWildcardMode(WildcardMode.FIXED);
            config.setFixedWildcardTile(tile);
            config.setWildcardTile(tile);
        }
        return config;
    }

    private List<Integer> tiles(String... codes) {
        return Arrays.stream(codes).map(Tiles::fromCode).toList();
    }

    private WinResult check(List<Integer> hand, List<Meld> melds, String wildcard, int winningTile,
                            boolean kongKai, int baseScore) {
        RuleConfig config = config(wildcard);
        config.setBaseScore(baseScore);
        return WinChecker.check(new WinRequest(hand, melds, config, true, false, winningTile, kongKai));
    }

    @Test
    @DisplayName("9支 + 11支：1 基础 + 3 = 4 分，9支本身不加分")
    void nineAndElevenSticks() {
        List<Integer> hand = tiles(
                "1W", "2W", "3W", "4W", "5W", "5W", "5W", "6W", "7W",
                "8W", "8W",
                "2T", "3T", "4T");
        WinResult result = check(hand, List.of(), "8W", -1, false, 1);

        assertTrue(result.isWin(), result.getReason());
        assertTrue(result.getFlavorLabels().contains("9支"));
        assertTrue(result.getFlavorLabels().contains("11支"));
        assertTrue(result.getFlavorLabels().contains("跑风"), "将牌是两张癞子，也算跑风");
        assertEquals(5, result.getFan(), "基础 1 + 11支 3 + 跑风 1");
        assertEquals(5, result.getScore(), "底分 1 时单家付 5");
    }

    @Test
    @DisplayName("混一色：1 基础 + 2 = 3 分")
    void hunYiSe() {
        List<Integer> hand = tiles(
                "1W", "2W", "3W", "4W", "5W", "6W", "7W", "8W", "9W",
                "E", "E", "E",
                "C", "C");
        WinResult result = check(hand, List.of(), null, -1, false, 1);

        assertTrue(result.isWin(), result.getReason());
        assertEquals(3, result.getFan());
    }

    @Test
    @DisplayName("清一色 + 跑风 + 11支：1 + 4 + 1 + 3 = 9 分，且与混一色互斥")
    void qingYiSeStacksWithPaoFeng() {
        List<Integer> hand = tiles(
                "1B", "1B", "1B", "1B", "2B", "3B", "4B", "6B", "7B", "8B", "9B",
                "5B", "5B", "5B");
        WinResult result = check(hand, List.of(), "5B", -1, false, 1);

        assertTrue(result.isWin(), result.getReason());
        assertTrue(result.getFlavorLabels().contains("清一色"));
        assertTrue(result.getFlavorLabels().contains("跑风"));
        assertTrue(result.getFlavorLabels().contains("11支"));
        assertTrue(!result.getFlavorLabels().contains("混一色"), "清一色与混一色互斥");
        assertEquals(9, result.getFan());
    }

    @Test
    @DisplayName("单钓：胡的那张正好是将牌，多 +1 分")
    void danDiao() {
        List<Integer> hand = tiles(
                "1W", "2W", "3W", "4W", "5W", "6W", "7W", "8W", "9W",
                "2T", "3T", "4T", "5T", "5T");

        WinResult noFlag = check(hand, List.of(), null, -1, false, 1);
        assertEquals(1, noFlag.getFan(), "不传胡牌张时不算单钓");

        WinResult danDiao = check(hand, List.of(), null, Tiles.fromCode("5T"), false, 1);
        assertTrue(danDiao.getFlavorLabels().contains("单钓"));
        assertEquals(2, danDiao.getFan(), "基础 1 + 单钓 1");
    }

    @Test
    @DisplayName("杠与杠开：每杠 +1，杠开再 +2")
    void kongAndKongKai() {
        List<Integer> hand = tiles(
                "2W", "3W", "4W",
                "5W", "6W", "7W",
                "2T", "3T", "4T",
                "9T", "9T");
        List<Meld> melds = List.of(Meld.kongMing(Tiles.fromCode("1W"), 2));

        WinResult normal = check(hand, melds, null, -1, false, 1);
        assertEquals(2, normal.getFan(), "基础 1 + 杠 1");

        WinResult kongKai = check(hand, melds, null, -1, true, 1);
        assertEquals(4, kongKai.getFan(), "基础 1 + 杠 1 + 杠开 2");
        assertTrue(kongKai.getFlavorLabels().contains("杠开"));
    }

    @Test
    @DisplayName("底分是倍数：底分 3 时单家应付 = 牌型分 × 3")
    void baseScoreMultiplies() {
        List<Integer> hand = tiles(
                "1W", "2W", "3W", "4W", "5W", "6W", "7W", "8W", "9W",
                "E", "E", "E",
                "C", "C");
        WinResult result = check(hand, List.of(), null, -1, false, 3);

        assertEquals(3, result.getFan(), "牌型分不受底分影响");
        assertEquals(9, result.getScore(), "3 分 × 底分 3 = 9");
    }
}
