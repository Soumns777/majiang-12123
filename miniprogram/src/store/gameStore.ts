import Taro from '@tarojs/taro'
import { create } from 'zustand'

import type { TableView } from '../types/protocol'

const PLAYER_ID_KEY = 'mj_player_id'
const NICKNAME_KEY = 'mj_nickname'

function randomNickname() {
  return `牌友${Math.floor(100 + Math.random() * 900)}`
}

interface GameStore {
  connected: boolean
  playerId: string
  nickname: string
  roomId: string
  table: TableView | null
  error: string
  setConnected: (connected: boolean) => void
  setProfile: (playerId: string, nickname: string) => void
  setNickname: (nickname: string) => void
  setTable: (table: TableView) => void
  setError: (message: string) => void
  clearError: () => void
  resetRoom: () => void
}

export const useGameStore = create<GameStore>((set) => ({
  connected: false,
  playerId: (Taro.getStorageSync(PLAYER_ID_KEY) as string) || '',
  nickname: (Taro.getStorageSync(NICKNAME_KEY) as string) || randomNickname(),
  roomId: '',
  table: null,
  error: '',

  setConnected: (connected) => set({ connected }),

  setProfile: (playerId, nickname) => {
    Taro.setStorageSync(PLAYER_ID_KEY, playerId)
    Taro.setStorageSync(NICKNAME_KEY, nickname)
    set({ playerId, nickname })
  },

  setNickname: (nickname) => {
    Taro.setStorageSync(NICKNAME_KEY, nickname)
    set({ nickname })
  },

  setTable: (table) => set({ table, roomId: table.roomId }),

  setError: (message) => set({ error: message }),

  clearError: () => set({ error: '' }),

  resetRoom: () => set({ roomId: '', table: null })
}))

/** 取自己的座位号，未入座返回 -1 */
export function mySeat(table: TableView | null, playerId: string): number {
  if (!table || !playerId) {
    return -1
  }
  const me = table.players.find((player) => player.playerId === playerId)
  return me ? me.seat : -1
}
