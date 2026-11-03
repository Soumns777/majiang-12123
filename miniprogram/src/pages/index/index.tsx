import { Input, Text, View } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { useEffect, useRef, useState } from 'react'

import { socket } from '../../services/socket'
import { useGameStore } from '../../store/gameStore'
import './index.scss'

const RULES = ['4 面子 + 1 将', '缺一门', '9支 / 11支', '混一色', '跑风']

export default function Index() {
  const connected = useGameStore((state) => state.connected)
  const nickname = useGameStore((state) => state.nickname)
  const table = useGameStore((state) => state.table)
  const error = useGameStore((state) => state.error)
  const setNickname = useGameStore((state) => state.setNickname)
  const clearError = useGameStore((state) => state.clearError)

  const [roomCode, setRoomCode] = useState('')
  const [busy, setBusy] = useState(false)
  const navigated = useRef(false)

  useEffect(() => {
    if (!error) {
      return
    }
    Taro.showToast({ title: error, icon: 'none' })
    clearError()
  }, [error, clearError])

  useEffect(() => {
    if (!table?.roomId || navigated.current) {
      return
    }
    navigated.current = true
    setBusy(false)
    Taro.navigateTo({
      url: table.status === 'PLAYING' ? '/pages/game/index' : '/pages/room/index'
    })
    setTimeout(() => {
      navigated.current = false
    }, 1000)
  }, [table])

  const ensureConnected = () => {
    if (!connected) {
      Taro.showToast({ title: '正在连接服务器…', icon: 'none' })
      return false
    }
    return true
  }

  const createRoom = () => {
    if (!ensureConnected()) {
      return
    }
    setBusy(true)
    socket.send('ROOM_CREATE', { maxRounds: 8 })
  }

  const joinRoom = () => {
    if (!ensureConnected()) {
      return
    }
    const code = roomCode.trim()
    if (!/^\d{6}$/.test(code)) {
      Taro.showToast({ title: '请输入 6 位房间号', icon: 'none' })
      return
    }
    setBusy(true)
    socket.send('ROOM_JOIN', { roomId: code })
  }

  return (
    <View className="home">
      <View className="home__glow" />

      <View className="home__brand">
        <View className="home__logo">
          <Text className="home__logo-text">發</Text>
        </View>
        <Text className="home__title">南陵麻将</Text>
        <Text className="home__subtitle">四人连线 · 缺一门 · 有癞子</Text>
      </View>

      <View className="home__rules">
        {RULES.map((rule) => (
          <Text className="home__rule" key={rule}>
            {rule}
          </Text>
        ))}
      </View>

      <View className="home__panel">
        <View className="home__field">
          <Text className="home__label">昵称</Text>
          <Input
            className="home__input"
            value={nickname}
            maxlength={10}
            placeholder="给自己起个名字"
            onInput={(event) => setNickname(event.detail.value)}
          />
        </View>

        <View className="home__field">
          <Text className="home__label">房间号</Text>
          <Input
            className="home__input"
            type="number"
            value={roomCode}
            maxlength={6}
            placeholder="加入好友的房间"
            onInput={(event) => setRoomCode(event.detail.value)}
          />
        </View>

        <View className="home__actions">
          <View
            className={`mj-btn home__btn ${!connected || busy ? 'mj-btn--disabled' : ''}`}
            onClick={createRoom}
          >
            创建房间
          </View>
          <View
            className={`mj-btn mj-btn--ghost home__btn ${!connected || busy ? 'mj-btn--disabled' : ''}`}
            onClick={joinRoom}
          >
            加入房间
          </View>
        </View>
      </View>

      <View className="home__status">
        <View className={`home__dot ${connected ? 'is-online' : ''}`} />
        <Text className="home__status-text">
          {connected ? '已连接牌桌服务' : '连接中…'}
        </Text>
      </View>

      <Text className="home__footer">仅作休闲娱乐 · 不支持任何形式兑换</Text>
    </View>
  )
}
