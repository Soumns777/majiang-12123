const NUMERALS = ['一', '二', '三', '四', '五', '六', '七', '八', '九']

const HONORS: Record<string, string> = {
  E: '东',
  S: '南',
  W: '西',
  N: '北',
  C: '中',
  F: '發',
  P: '白'
}

const SUIT_LABEL: Record<string, string> = {
  W: '万',
  T: '筒',
  B: '条'
}

export interface TileFace {
  /** 是否字牌 */
  honor: boolean
  /** 数字牌 1-9，字牌为 0 */
  rank: number
  /** 数字牌的中文数字，字牌为字名 */
  rankLabel: string
  /** 花色字：万/筒/条，字牌为空 */
  suitLabel: string
  /** 花色代码 W/T/B/H */
  suit: string
}

export function parseTile(code: string): TileFace {
  if (!code) {
    return { honor: true, rank: 0, rankLabel: '?', suitLabel: '', suit: 'H' }
  }
  if (code.length === 1) {
    return {
      honor: true,
      rank: 0,
      rankLabel: HONORS[code] ?? '?',
      suitLabel: '',
      suit: 'H'
    }
  }
  const rank = Number(code[0])
  const suit = code[1]
  return {
    honor: false,
    rank,
    rankLabel: NUMERALS[rank - 1] ?? '?',
    suitLabel: SUIT_LABEL[suit] ?? '',
    suit
  }
}

/** 牌面中文名，用于结算/提示文案 */
export function tileName(code: string): string {
  const face = parseTile(code)
  return face.honor ? face.rankLabel : `${face.rankLabel}${face.suitLabel}`
}

export function isKong(type: string): boolean {
  return type === 'KONG_MING' || type === 'KONG_AN' || type === 'KONG_BU'
}

export const ACTION_LABEL: Record<string, string> = {
  DISCARD: '出牌',
  PONG: '碰',
  KONG_MING: '杠',
  KONG_AN: '暗杠',
  KONG_BU: '补杠',
  HU: '胡',
  PASS: '过'
}
