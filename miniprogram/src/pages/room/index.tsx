import { Text, View } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { useEffect, useRef } from 'react'

import { socket } from '../../services/socket'
import { mySeat, useGameStore } from '../../store/gameStore'
import { navigateOnce, relaunchHome } from '../../utils/nav'
import './index.scss'

export default function Room() {
  const table = useGameStore((state) => state.table)
  const playerId = useGameStore((state) => state.playerId)
  const error = useGameStore((state) => state.error)
  const clearError = useGameStore((state) => state.clearError)

  const hadTable = useRef(false)

  const seatIndex = mySeat(table, playerId)
  const me = table?.players.find((player) => player.playerId === playerId)
  const seated = table?.players.filter((player) => player.playerId) ?? []
  const isOwner = table?.ownerId === playerId
  const allReady = seated.length === 4 && seated.every((player) => player.ready)
  /** 满员 / 已开局时用房间号进来的人会拿到观战视图 */
  const spectating = !!table?.spectator

  useEffect(() => {
    if (!error) {
      return
    }
    Taro.showToast({ title: error, icon: 'none' })
    clearError()
  }, [error, clearError])

  useEffect(() => {
    if (table) {
      hadTable.current = true
      return
    }
    // 只有「曾经进过房间」才回首页，避免首帧 table 还没到时误跳
    if (hadTable.current) {
      relaunchHome()
    }
  }, [table])

  useEffect(() => {
    if (table?.status === 'PLAYING' || table?.status === 'FINISHED') {
      navigateOnce('/pages/game/index', 'replace')
    }
  }, [table?.status])

  if (!table) {
    return <View className="room" />
  }

  const copyCode = () => {
    Taro.setClipboardData({ data: table.roomId })
  }

  const toggleReady = () => {
    socket.send('ROOM_READY', { ready: !me?.ready })
  }

  const startGame = () => {
    socket.send('GAME_START')
  }

  const voidWildcardOn = !!table.voidWildcardOnDiscard

  const toggleVoidWildcard = () => {
    if (!isOwner) {
      Taro.showToast({ title: '只有房主可以修改规则', icon: 'none' })
      return
    }
    socket.send('ROOM_RULE', { voidWildcardOnDiscard: !voidWildcardOn })
  }

  const leaveRoom = () => {
    socket.send('ROOM_LEAVE')
    Taro.navigateBack()
  }

  return (
    <View className="room">
      <View className="room__header">
        <Text className="room__label">房间号</Text>
        <View className="room__code" onClick={copyCode}>
          <Text className="room__code-text">{table.roomId}</Text>
          <Text className="room__copy">复制</Text>
        </View>
        <Text className="room__hint">
          {spectating ? '你正在观战，凑齐 4 人开局后会自动进入牌桌' : '把房间号发给好友，凑齐 4 人即可开局'}
        </Text>
      </View>

      {spectating && (
        <View className="room__watch">
          <Text className="room__watch-tag">观战中</Text>
          <Text className="room__watch-text">
            {table.spectators.length > 1
              ? `${table.spectators.length} 人正在围观，四家出牌都能看到`
              : '四家出牌都能看到，但看不到任何人的手牌'}
          </Text>
        </View>
      )}

      <View className="room__seats">
        {table.players.map((player) => {
          const empty = !player.playerId
          return (
            <View
              className={`room__seat ${player.seat === seatIndex ? 'is-me' : ''} ${
                player.ready ? 'is-ready' : ''
              } ${empty ? 'is-empty' : ''}`}
              key={player.seat}
            >
              <View className="room__avatar">
                {empty ? '空' : (player.nickname || '闲').slice(0, 1)}
              </View>
              <Text className="room__name">{empty ? '等待加入' : player.nickname}</Text>
              <View className="room__badges">
                {player.seat === 0 && <Text className="room__badge room__badge--dealer">庄</Text>}
                {player.playerId === table.ownerId && <Text className="room__badge">房主</Text>}
                {!empty && player.ready && <Text className="room__badge room__badge--ready">已准备</Text>}
              </View>
            </View>
          )
        })}
      </View>

      <View className="room__info">
        <View className="room__info-row">
          <Text className="room__info-key">局数</Text>
          <Text className="room__info-value">共 {table.maxRounds} 局</Text>
        </View>
        <View className="room__info-row">
          <Text className="room__info-key">癞子</Text>
          <Text className="room__info-value">开局翻宝，翻出牌的后一张为癞子</Text>
        </View>
        <View className="room__info-row">
          <Text className="room__info-key">胡牌</Text>
          <Text className="room__info-value">4 面子 + 1 将 · 缺一门 · 有癞子需命中调味剂</Text>
        </View>
      </View>

      <View className="room__rules">
        <View className="room__rule">
          <View className="room__rule-main">
            <View className="room__rule-head">
              <Text className="room__rule-name">打出癞子，全场作废</Text>
              <Text className="room__rule-tag">自制规则</Text>
            </View>
            <Text className="room__rule-desc">
              开启后，本局只要有人打出癞子，所有人的癞子都不再是万能牌，只能按原牌面参与胡牌
            </Text>
          </View>
          <View
            className={`room__switch ${voidWildcardOn ? 'is-on' : ''} ${isOwner ? '' : 'is-locked'}`}
            onClick={toggleVoidWildcard}
          >
            <View className="room__switch-dot" />
          </View>
        </View>
        <Text className="room__rule-tip">
          {isOwner ? '开局前可随时修改' : '仅房主可修改'}
        </Text>
      </View>

      <View className="room__actions">
        <View className="mj-btn mj-btn--ghost room__btn" onClick={leaveRoom}>
          {spectating ? '退出观战' : '退出'}
        </View>
        {!spectating && (
          <View className={`mj-btn room__btn ${me?.ready ? 'mj-btn--ghost' : ''}`} onClick={toggleReady}>
            {me?.ready ? '取消准备' : '准备'}
          </View>
        )}
        {isOwner && !spectating && (
          <View
            className={`mj-btn room__btn room__btn--primary ${
              allReady ? '' : 'mj-btn--disabled'
            }`}
            onClick={startGame}
          >
            开始
          </View>
        )}
      </View>
    </View>
  )
}
