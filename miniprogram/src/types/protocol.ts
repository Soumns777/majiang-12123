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
  ready: boolean
  online: boolean
  dealer: boolean
  current: boolean
  handCount: number
  hand: string[]
  melds: MeldView[]
  discards: string[]
  actions: ActionType[]
}

export interface ResultView {
  drawGame: boolean
  winnerSeat: number
  loserSeat: number
  fan: number
  score: number
  flavors: string[]
  winningHand: string[]
}

export interface TableView {
  roomId: string
  ownerId: string
  status: RoomStatus
  round: number
  maxRounds: number
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
