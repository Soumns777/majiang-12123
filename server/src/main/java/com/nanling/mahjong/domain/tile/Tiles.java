package com.nanling.mahjong.domain.tile;

/**
 * 牌编码工具。
 *
 * <p>内部统一用 int 表示一张牌，取值范围 0-33：</p>
 * <ul>
 *   <li>0-8   一万 ~ 九万</li>
 *   <li>9-17  一筒 ~ 九筒</li>
 *   <li>18-26 一条 ~ 九条</li>
 *   <li>27-33 东 南 西 北 中 发 白</li>
 * </ul>
 * <p>用 int 而不是对象，是为了让胡牌算法直接操作计数数组，避免频繁装箱。</p>
 */
public final class Tiles {

    public static final int KIND_COUNT = 34;
    public static final int WAN_BASE = 0;
    public static final int TONG_BASE = 9;
    public static final int TIAO_BASE = 18;
    public static final int HONOR_BASE = 27;

    /** 字牌：东 南 西 北 中 发 白 */
    public static final int EAST = 27;
    public static final int SOUTH = 28;
    public static final int WEST = 29;
    public static final int NORTH = 30;
    public static final int RED = 31;
    public static final int GREEN = 32;
    public static final int WHITE = 33;

    private static final String[] NUMERAL = {"一", "二", "三", "四", "五", "六", "七", "八", "九"};
    private static final String[] HONOR_NAME = {"东", "南", "西", "北", "中", "发", "白"};
    private static final String[] SUIT_CODE = {"W", "T", "B"};

    private Tiles() {
    }

    public static int of(Suit suit, int rank) {
        return switch (suit) {
            case WAN -> WAN_BASE + rank - 1;
            case TONG -> TONG_BASE + rank - 1;
            case TIAO -> TIAO_BASE + rank - 1;
            case HONOR -> HONOR_BASE + rank - 1;
        };
    }

    /** 1-9（数牌）或 1-7（字牌）。 */
    public static int rankOf(int tile) {
        check(tile);
        if (tile < 27) {
            return tile % 9 + 1;
        }
        return tile - HONOR_BASE + 1;
    }

    public static Suit suitOf(int tile) {
        check(tile);
        return Suit.ofTile(tile);
    }

    public static boolean isHonor(int tile) {
        return tile >= HONOR_BASE;
    }

    public static boolean isNumberTile(int tile) {
        return tile >= 0 && tile < 27;
    }

    /** 同一花色内是否连续，用于顺子判定。 */
    public static boolean isSequence(int a, int b, int c) {
        if (!isNumberTile(a) || !isNumberTile(b) || !isNumberTile(c)) {
            return false;
        }
        if (suitOf(a) != suitOf(b) || suitOf(a) != suitOf(c)) {
            return false;
        }
        int ra = rankOf(a);
        int rb = rankOf(b);
        int rc = rankOf(c);
        return rb == ra + 1 && rc == ra + 2;
    }

    /** 协议编码，例如 5W / 3T / 9B / E / C。 */
    public static String code(int tile) {
        check(tile);
        if (isHonor(tile)) {
            return switch (tile) {
                case EAST -> "E";
                case SOUTH -> "S";
                case WEST -> "W";
                case NORTH -> "N";
                case RED -> "C";
                case GREEN -> "F";
                default -> "P";
            };
        }
        Suit suit = suitOf(tile);
        int base = suit == Suit.WAN ? WAN_BASE : suit == Suit.TONG ? TONG_BASE : TIAO_BASE;
        return (tile - base + 1) + SUIT_CODE[suit.ordinal()];
    }

    public static int fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("牌编码不能为空");
        }
        String value = code.trim().toUpperCase();
        return switch (value) {
            case "E" -> EAST;
            case "S" -> SOUTH;
            case "W" -> WEST;
            case "N" -> NORTH;
            case "C" -> RED;
            case "F" -> GREEN;
            case "P" -> WHITE;
            default -> {
                if (value.length() != 2) {
                    throw new IllegalArgumentException("非法牌编码: " + code);
                }
                int rank = value.charAt(0) - '0';
                if (rank < 1 || rank > 9) {
                    throw new IllegalArgumentException("非法牌编码: " + code);
                }
                Suit suit = switch (value.charAt(1)) {
                    case 'W' -> Suit.WAN;
                    case 'T' -> Suit.TONG;
                    case 'B' -> Suit.TIAO;
                    default -> throw new IllegalArgumentException("非法牌编码: " + code);
                };
                yield of(suit, rank);
            }
        };
    }

    /** 中文展示名，例如 五万 / 东。 */
    public static String display(int tile) {
        check(tile);
        if (isHonor(tile)) {
            return HONOR_NAME[tile - HONOR_BASE];
        }
        Suit suit = suitOf(tile);
        return NUMERAL[rankOf(tile) - 1] + suit.getLabel();
    }

    /** 牌面数字（前端渲染用），字牌返回 0。 */
    public static int faceValue(int tile) {
        return isHonor(tile) ? 0 : rankOf(tile);
    }

    public static void check(int tile) {
        if (tile < 0 || tile >= KIND_COUNT) {
            throw new IllegalArgumentException("非法牌 id: " + tile);
        }
    }
}
