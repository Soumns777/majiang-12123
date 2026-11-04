package com.nanling.mahjong.domain.rule;

import com.nanling.mahjong.domain.tile.Tiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 南陵麻将胡牌判定用例，对应《南陵麻将·胡牌成牌规则精解》第 3-7 页的六组牌型。
 */
class WinCheckerTest {

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

    private WinResult check(List<Integer> hand, List<Meld> melds, String wildcard) {
        return WinChecker.check(new WinRequest(hand, melds, config(wildcard), false));
    }

    /** 癞子已作废（有人打出过癞子）时的判定。 */
    private WinResult checkVoided(List<Integer> hand, List<Meld> melds, String wildcard) {
        return WinChecker.check(new WinRequest(hand, melds, config(wildcard), false, true));
    }

    @Test
    @DisplayName("用例①：万子 9 实牌 + 2 癞子 = 11 张，9支与11支同时成立")
    void case01_nineAndElevenSticks() {
        // 123万 456万 7万+2癞子(8万)=777万 将牌55万 234筒
        List<Integer> hand = tiles(
                "1W", "2W", "3W", "4W", "5W", "5W", "5W", "6W", "7W",
                "8W", "8W",
                "2T", "3T", "4T");
        WinResult result = check(hand, List.of(), "8W");

        assertTrue(result.isWin(), result.getReason());
        assertTrue(result.getFlavorLabels().contains("9支"), "应命中 9 支");
        assertTrue(result.getFlavorLabels().contains("11支"), "应命中 11 支");
    }

    @Test
    @DisplayName("用例②：癞子凑出刚好 9 张不算 9支，不可胡")
    void case02_jokerFilledNineIsInvalid() {
        // 万子实牌 8 张 + 1 张癞子 = 9 张，筒子 234 + 55
        List<Integer> hand = tiles(
                "1W", "2W", "3W", "4W", "5W", "6W", "7W", "7W", "8W",
                "2T", "3T", "4T", "5T", "5T");
        WinResult result = check(hand, List.of(), "8W");

        assertFalse(result.isWin());
        assertTrue(result.getReason().contains("调味剂"), result.getReason());
    }

    @Test
    @DisplayName("用例③：混一色（万 + 字牌）可胡")
    void case03_hunYiSe() {
        List<Integer> hand = tiles(
                "1W", "2W", "3W", "4W", "5W", "6W", "7W", "8W", "9W",
                "E", "E", "E",
                "C", "C");
        WinResult result = check(hand, List.of(), null);

        assertTrue(result.isWin(), result.getReason());
        assertTrue(result.getFlavorLabels().contains("混一色"));
        assertFalse(result.getFlavorLabels().contains("清一色"));
    }

    @Test
    @DisplayName("用例④：清一色 + 跑风（癞子作将头）")
    void case04_qingYiSeAndPaoFeng() {
        List<Integer> hand = tiles(
                "1B", "1B", "1B", "1B", "2B", "3B", "4B", "6B", "7B", "8B", "9B",
                "5B", "5B", "5B");
        WinResult result = check(hand, List.of(), "5B");

        assertTrue(result.isWin(), result.getReason());
        assertTrue(result.getFlavorLabels().contains("清一色"));
        assertTrue(result.getFlavorLabels().contains("跑风"), "癞子作将应触发跑风");
        assertTrue(result.isPairUsedJoker());
        assertTrue(result.getFlavorLabels().contains("9支"));
        assertTrue(result.getFlavorLabels().contains("11支"));
    }

    @Test
    @DisplayName("用例⑤：无癞子单钓，裸胡")
    void case05_noJokerSingleWait() {
        List<Integer> hand = tiles(
                "1W", "2W", "3W", "4W", "5W", "6W", "7W", "8W", "9W",
                "2T", "3T", "4T", "5T", "5T");
        WinResult result = check(hand, List.of(), null);

        assertTrue(result.isWin(), result.getReason());
        assertTrue(result.getFlavorLabels().contains("基础"));
        assertEquals(1, result.getFan(), "没有其他牌型分时只拿基础 1 分");
    }

    @Test
    @DisplayName("用例⑥：带杠 9支（杠牌计入花色张数）")
    void case06_kongNineSticks() {
        List<Integer> hand = tiles(
                "2W", "3W", "4W",
                "5W", "6W", "7W",
                "2T", "3T", "4T",
                "9T", "9T");
        List<Meld> melds = List.of(Meld.kongMing(Tiles.fromCode("1W"), 2));
        WinResult result = check(hand, melds, null);

        assertTrue(result.isWin(), result.getReason());
        assertTrue(result.getFlavorLabels().contains("9支"), "杠 4 张 + 顺子 6 张 = 10 张万子");
        assertFalse(result.getFlavorLabels().contains("11支"));
        assertTrue(result.getFlavorLabels().contains("杠"));
    }

    @Test
    @DisplayName("三色俱全时不满足缺一门")
    void rejectWhenAllThreeSuitsPresent() {
        List<Integer> hand = tiles(
                "1W", "2W", "3W",
                "4W", "5W", "6W",
                "7W", "8W", "9W",
                "1T", "2T", "3T",
                "5B", "5B");
        WinResult result = check(hand, List.of(), null);

        assertFalse(result.isWin());
        assertTrue(result.getReason().contains("缺一门"), result.getReason());
    }

    @Test
    @DisplayName("结构不成立时直接判负")
    void rejectBrokenStructure() {
        List<Integer> hand = tiles(
                "1W", "1W", "2W", "4W", "5W", "7W", "8W",
                "2T", "3T", "5T", "6T", "8T", "9T", "9T");
        WinResult result = check(hand, List.of(), null);

        assertFalse(result.isWin());
        assertTrue(result.getReason().contains("牌型不成立"), result.getReason());
    }

    @Test
    @DisplayName("癞子作刻子不算跑风")
    void jokerInTripletIsNotPaoFeng() {
        List<Integer> hand = tiles(
                "1W", "2W", "3W",
                "4W", "5W", "6W",
                "7W", "7W", "8W",
                "9W", "9W", "9W",
                "5T", "5T");
        WinResult result = check(hand, List.of(), "8W");

        assertTrue(result.isWin(), result.getReason());
        assertFalse(result.getFlavorLabels().contains("跑风"));
        assertTrue(result.getFlavorLabels().contains("9支"));
    }

    @Test
    @DisplayName("无癞子局允许裸胡（不要求调味剂）")
    void noWildcardGameAllowsBareWin() {
        RuleConfig config = config(null);
        List<Integer> hand = tiles(
                "1W", "2W", "3W",
                "4W", "5W", "6W",
                "2T", "3T", "4T",
                "6T", "7T", "8T",
                "9T", "9T");
        WinResult result = WinChecker.check(new WinRequest(hand, List.of(), config, true));

        assertTrue(result.isWin(), result.getReason());
        // 自摸本身不再单独加分（PDF 里没有自摸项），只拿基础 1 分
        assertEquals(1, result.getFan());
    }

    // ------------------------------------------------------------------
    // 自制规则：打出癞子，全场作废
    // ------------------------------------------------------------------

    @Test
    @DisplayName("癞子作废后不能再补顺子：原本靠癞子补 3万 的混一色牌型判负")
    void voidedJokerCannotFillChow() {
        // 万子 1,2,[癞子=5筒]，癞子补成 3万 才能成 123万
        List<Integer> hand = tiles(
                "1W", "2W",
                "4W", "5W", "6W",
                "7W", "8W", "9W",
                "E", "E", "E",
                "C", "C",
                "5T");

        WinResult normal = check(hand, List.of(), "5T");
        assertTrue(normal.isWin(), "未作废时应能靠癞子补成 123万：" + normal.getReason());
        assertTrue(normal.getFlavorLabels().contains("混一色"));

        WinResult voided = checkVoided(hand, List.of(), "5T");
        assertFalse(voided.isWin(), "作废后 5筒 只是普通筒子，1万2万 组不成面子");
        assertTrue(voided.getReason().contains("牌型不成立"), voided.getReason());
    }

    @Test
    @DisplayName("癞子作废后跑风失效：靠癞子作将的牌型判负")
    void voidedJokerBreaksPaoFengPair() {
        // 万 9 张 + 筒 2345，癞子(9条) 作 5筒 的对子才成立
        List<Integer> hand = tiles(
                "1W", "2W", "3W",
                "4W", "5W", "6W",
                "7W", "8W", "9W",
                "2T", "3T", "4T",
                "5T",
                "9B");

        WinResult normal = check(hand, List.of(), "9B");
        assertTrue(normal.isWin(), normal.getReason());
        assertTrue(normal.getFlavorLabels().contains("跑风"), "癞子作将应触发跑风");
        assertTrue(normal.isPairUsedJoker());

        WinResult voided = checkVoided(hand, List.of(), "9B");
        assertFalse(voided.isWin(), "作废后 9条 是实牌，既不能补将又破坏了缺一门");
    }

    @Test
    @DisplayName("癞子作废后按自身牌面参与牌型，且不再要求调味剂（可裸胡）")
    void voidedJokerCountsAsItsOwnTile() {
        // 癞子本身是 5筒，正好能和自己凑成将牌
        List<Integer> hand = tiles(
                "1W", "2W", "3W",
                "4W", "5W", "6W",
                "7W", "8W", "9W",
                "2T", "3T", "4T",
                "5T",
                "5T");

        WinResult voided = checkVoided(hand, List.of(), "5T");
        assertTrue(voided.isWin(), voided.getReason());
        assertFalse(voided.getFlavorLabels().contains("跑风"), "作废后不应再有跑风");
        assertFalse(voided.isPairUsedJoker());
        assertTrue(voided.getFlavorLabels().contains("基础"), "没有癞子可用，按裸胡计");
    }
}
