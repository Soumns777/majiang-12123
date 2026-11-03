import { PropsWithChildren } from 'react'
import { useLaunch } from '@tarojs/taro'

import './app.scss'
import { WS_URL } from './config/env'
import { socket } from './services/socket'
import { useGameStore } from './store/gameStore'

function App({ children }: PropsWithChildren<any>) {
  useLaunch(() => {
    socket.onStatus((connected) => {
      useGameStore.getState().setConnected(connected)
      if (connected) {
        const { playerId, nickname } = useGameStore.getState()
        socket.send('HELLO', {
          playerId: playerId || undefined,
          nickname,
          avatar: ''
        })
      }
    })

    socket.on((type, data) => {
      const store = useGameStore.getState()
      switch (type) {
        case 'HELLO_ACK':
          store.setProfile(data.playerId, data.nickname)
          break
        case 'ROOM_STATE':
          store.setTable(data)
          break
        case 'ERROR':
          store.setError(data.message || '操作失败')
          break
        case 'LEFT':
          store.resetRoom()
          break
        default:
          break
      }
    })

    socket.connect(WS_URL)
  })

  return children
}

export default App
