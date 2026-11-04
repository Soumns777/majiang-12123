/**
 * 与后端 docs/03-通信协议.md 保持一致。
 */

export type SuitCode = 'W' | 'T' | 'B'

export type ActionType =
  | 'DISCARD'
  | 'PONG'
  | 'KONG_MING'
  | 'KONG_AN'
  | 'KONG_BU'
  | 'HU'
  | 'PASS'

export type GamePhase = 'WAITING' | 'PLAYER_TURN' | 'WAITING_CLAIM' | 'ENDED'

export type RoomStatus = 'WAITING' | 'PLAYING' | 'FINISHED'

export interface MeldView {
  type: 'CHOW' | 'PONG' | 'KONG_MING' | 'KONG_AN' | 'KONG_BU'
  tiles: string[]
  fromSeat: number
  exposed: boolean
}

export interface PlayerView {
  seat: number
  playerId: string
  nickname: string
  avatar: string
  score: number
  /** 欢乐豆余额，开局默认 20 */
  beans: number
  ready: boolean
  online: boolean
  /** 机器人玩家：由服务端逐个代打 */
  bot: boolean
  dealer: boolean
  current: boolean
  handCount: number
  hand: string[]
  melds: MeldView[]
  discards: string[]
  actions: ActionType[]
}

export interface FlavorItem {
  label: string
  fan: number
}

/** 某一座位本局欢乐豆增减 */
export interface SeatBeans {
  seat: number
  beans: number
}

export interface ResultView {
  drawGame: boolean
  winnerSeat: number
  loserSeat: number
  /** 牌型分合计 */
  fan: number
  /** 单家应付欢乐豆 = 牌型分 × 底分 */
  score: number
  baseScore: number
  flavors: string[]
  /** 牌型分明细，用于说明「胡了什么」 */
  breakdown: FlavorItem[]
  /** 四家欢乐豆增减 */
  deltas: SeatBeans[]
  winningHand: string[]
}

export interface ExtendView {
  /** 申请人座位 */
  seat: number
  /** 已同意的座位 */
  approvals: number[]
  /** 需要几家同意 */
  need: number
}

export interface TableView {
  roomId: string
  ownerId: string
  status: RoomStatus
  round: number
  maxRounds: number
  /** 底分：单家应付欢乐豆 = 牌型分 × 底分 */
  baseScore: number
  phase: GamePhase
  currentSeat: number
  remainingTiles: number
  wildcard?: string
  wildcardLabel: string
  /** 房间是否开启了自制规则「打出癞子，全场作废」 */
  voidWildcardOnDiscard: boolean
  /** 本局癞子是否已经作废（有人打出过癞子） */
  wildcardVoided: boolean
  lastDiscardTile?: string
  lastDiscardSeat: number
  deadlineAt: number
  serverTime: number
  /** 当前连接者是不是观战者：是则所有人手牌都为空 */
  spectator: boolean
  /** 观战者昵称列表 */
  spectators: string[]
  /** 正在投票的延时申请，没有则缺省 */
  extend?: ExtendView
  players: PlayerView[]
  result?: ResultView
}

export interface HelloAck {
  playerId: string
  nickname: string
  avatar: string
}

export interface ErrorPayload {
  code: number
  message: string
}

export interface CreateRoomOptions {
  baseScore?: number
  maxRounds?: number
  wildcardMode?: 'NONE' | 'FLIP_NEXT' | 'FIXED'
  wildcardTile?: string
  requireFlavorForJokerHand?: boolean
  voidWildcardOnDiscard?: boolean
}
