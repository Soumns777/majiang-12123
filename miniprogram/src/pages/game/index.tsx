import { ScrollView, Text, View } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { useEffect, useMemo, useRef, useState } from 'react'

import PlayerSeat from '../../components/PlayerSeat'
import Tile from '../../components/Tile'
import { socket } from '../../services/socket'
import { mySeat, useGameStore } from '../../store/gameStore'
import type { ActionType } from '../../types/protocol'
import { ACTION_LABEL } from '../../utils/tile'
import './index.scss'

interface PickerState {
  action: ActionType
  tiles: string[]
}

const ACTION_PRIORITY: Record<string, number> = {
  HU: 0,
  KONG_AN: 1,
  KONG_BU: 2,
  KONG_MING: 3,
  PONG: 4,
  PASS: 9
}

export default function Game() {
  const table = useGameStore((state) => state.table)
  const playerId = useGameStore((state) => state.playerId)
  const error = useGameStore((state) => state.error)
  const clearError = useGameStore((state) => state.clearError)

  const [selected, setSelected] = useState<string | null>(null)
  const [picker, setPicker] = useState<PickerState | null>(null)
  const [now, setNow] = useState(Date.now())
  const offsetRef = useRef(0)

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])

  useEffect(() => {
    if (table) {
      offsetRef.current = table.serverTime - Date.now()
    }
  }, [table])

  useEffect(() => {
    if (!error) {
      return
    }
    Taro.showToast({ title: error, icon: 'none' })
    clearError()
  }, [error, clearError])

  useEffect(() => {
    if (!table) {
      Taro.redirectTo({ url: '/pages/index/index' })
    }
  }, [table])

  const seatIndex = mySeat(table, playerId)
  const me = table?.players.find((player) => player.playerId === playerId)

  const others = useMemo(() => {
    if (!table || seatIndex < 0) {
      return []
    }
    return [1, 2, 3].map((offset) => table.players[(seatIndex + offset) % 4])
  }, [table, seatIndex])

  const handKey = me?.hand.join(',') ?? ''
  const meldKey = me?.melds.map((meld) => meld.tiles[0]).join(',') ?? ''

  const anKongTiles = useMemo(() => {
    if (!me) {
      return []
    }
    const counts: Record<string, number> = {}
    me.hand.forEach((tile) => {
      counts[tile] = (counts[tile] ?? 0) + 1
    })
    return Object.keys(counts).filter((tile) => counts[tile] === 4)
  }, [handKey])

  const buKongTiles = useMemo(() => {
    if (!me) {
      return []
    }
    return me.melds
      .filter((meld) => meld.type === 'PONG')
      .map((meld) => meld.tiles[0])
      .filter((tile) => me.hand.includes(tile))
  }, [handKey, meldKey])

  if (!table || !me || seatIndex < 0) {
    return <View className="game" />
  }

  const isMyTurn = table.phase === 'PLAYER_TURN' && table.currentSeat === seatIndex
  const isClaiming = table.phase === 'WAITING_CLAIM'
  const actions = me.actions ?? []
  const remain =
    table.phase === 'ENDED'
      ? 0
      : Math.max(0, Math.ceil((table.deadlineAt - (now + offsetRef.current)) / 1000))

  const actionButtons = actions
    .filter((action) => action !== 'DISCARD')
    .sort((a, b) => (ACTION_PRIORITY[a] ?? 5) - (ACTION_PRIORITY[b] ?? 5))

  const currentPlayer = table.players.find((player) => player.seat === table.currentSeat)
  const result = table.result
  const winner = result ? table.players.find((player) => player.seat === result.winnerSeat) : null
  const loser = result && result.loserSeat >= 0
    ? table.players.find((player) => player.seat === result.loserSeat)
    : null
  const isOwner = table.ownerId === playerId
  const finished = table.status === 'FINISHED'

  const sendAction = (action: ActionType, tile?: string) => {
    socket.send('GAME_ACTION', { action, tile })
    setSelected(null)
    setPicker(null)
  }

  const onTileClick = (code: string) => {
    if (!isMyTurn) {
      return
    }
    setSelected((prev) => (prev === code ? null : code))
  }

  const handleAction = (action: ActionType) => {
    if (action === 'KONG_AN') {
      if (anKongTiles.length === 1) {
        sendAction('KONG_AN', anKongTiles[0])
      } else {
        setPicker({ action, tiles: anKongTiles })
      }
      return
    }
    if (action === 'KONG_BU') {
      if (buKongTiles.length === 1) {
        sendAction('KONG_BU', buKongTiles[0])
      } else {
        setPicker({ action, tiles: buKongTiles })
      }
      return
    }
    sendAction(action)
  }

  const backHome = () => {
    socket.send('ROOM_LEAVE')
    Taro.reLaunch({ url: '/pages/index/index' })
  }

  return (
    <View className="game">
      <View className="game__bar">
        <View className="game__bar-item">
          <Text className="game__bar-key">房间</Text>
          <Text className="game__bar-value">{table.roomId}</Text>
        </View>
        <View className="game__bar-item">
          <Text className="game__bar-key">局数</Text>
          <Text className="game__bar-value">
            {table.round}/{table.maxRounds}
          </Text>
        </View>
        <View className="game__bar-item">
          <Text className="game__bar-key">剩余</Text>
          <Text className="game__bar-value">{table.remainingTiles}</Text>
        </View>
        <View className="game__bar-item">
          <Text className="game__bar-key">癞子</Text>
          <Text className="game__bar-value game__bar-value--gold">{table.wildcardLabel}</Text>
        </View>
      </View>

      <View className="game__arena">
        {others[1] && (
          <View className="game__seat game__seat--top">
            <PlayerSeat
              player={others[1]}
              position="top"
              isTurn={table.currentSeat === others[1].seat && table.phase !== 'ENDED'}
              remainSeconds={remain}
            />
          </View>
        )}
        {others[2] && (
          <View className="game__seat game__seat--left">
            <PlayerSeat
              player={others[2]}
              position="left"
              isTurn={table.currentSeat === others[2].seat && table.phase !== 'ENDED'}
              remainSeconds={remain}
            />
          </View>
        )}
        {others[0] && (
          <View className="game__seat game__seat--right">
            <PlayerSeat
              player={others[0]}
              position="right"
              isTurn={table.currentSeat === others[0].seat && table.phase !== 'ENDED'}
              remainSeconds={remain}
            />
          </View>
        )}

        <View className="game__center">
          <View className="game__last">
            {table.lastDiscardTile ? (
              <Tile code={table.lastDiscardTile} size="md" />
            ) : (
              <View className="game__last-empty">—</View>
            )}
            <Text className="game__last-label">最新打出</Text>
          </View>

          <View className="game__turn">
            {table.phase === 'ENDED'
              ? '本局结束'
              : isMyTurn
                ? '轮到你出牌'
                : isClaiming
                  ? '等待其他玩家响应'
                  : `等待 ${currentPlayer?.nickname ?? ''} 出牌`}
          </View>
          {table.phase !== 'ENDED' && (
            <Text className="game__countdown">{remain}s</Text>
          )}
        </View>
      </View>

      <View className="game__mine">
        {me.discards.length > 0 && (
          <ScrollView className="game__my-discards" scrollX>
            {me.discards.map((tile, index) => (
              <Tile code={tile} size="xs" key={`${tile}-${index}`} />
            ))}
          </ScrollView>
        )}

        {me.melds.length > 0 && (
          <View className="game__my-melds">
            {me.melds.map((meld, index) => (
              <View className="game__meld" key={`${meld.type}-${index}`}>
                {meld.tiles.map((tile, tileIndex) => (
                  <Tile code={tile} size="xs" key={`${tile}-${tileIndex}`} />
                ))}
              </View>
            ))}
          </View>
        )}

        <View className="game__hand">
          {me.hand.map((tile, index) => (
            <Tile
              code={tile}
              size="hand"
              selected={selected === tile}
              onClick={onTileClick}
              key={`${tile}-${index}`}
            />
          ))}
        </View>

        <View className="game__actions">
          {actionButtons.map((action) => (
            <View
              className={`game__action ${action === 'HU' ? 'game__action--hu' : ''}`}
              onClick={() => handleAction(action)}
              key={action}
            >
              {ACTION_LABEL[action] ?? action}
            </View>
          ))}
          {isMyTurn && (
            <View
              className={`game__action game__action--primary ${selected ? '' : 'is-disabled'}`}
              onClick={() => selected && sendAction('DISCARD', selected)}
            >
              出牌
            </View>
          )}
        </View>
      </View>

      {picker && (
        <View className="game__mask" onClick={() => setPicker(null)}>
          <View className="game__picker" onClick={(event) => event.stopPropagation()}>
            <Text className="game__picker-title">
              选择要{ACTION_LABEL[picker.action]}的牌
            </Text>
            <View className="game__picker-tiles">
              {picker.tiles.map((tile) => (
                <Tile
                  code={tile}
                  size="sm"
                  key={tile}
                  onClick={() => sendAction(picker.action, tile)}
                />
              ))}
            </View>
          </View>
        </View>
      )}

      {result && (
        <View className="game__mask">
          <View className="game__result">
            <Text className="game__result-title">
              {result.drawGame ? '荒庄' : result.loserSeat >= 0 ? '点炮胡' : '自摸胡'}
            </Text>

            {!result.drawGame && (
              <>
                <Text className="game__result-winner">{winner?.nickname ?? ''}</Text>
                <View className="game__result-flavors">
                  {result.flavors.map((flavor) => (
                    <Text className="game__flavor" key={flavor}>
                      {flavor}
                    </Text>
                  ))}
                </View>
                <View className="game__result-hand">
                  {result.winningHand.map((tile, index) => (
                    <Tile code={tile} size="xs" key={`${tile}-${index}`} />
                  ))}
                </View>
                <View className="game__result-stats">
                  <Text className="game__result-stat">{result.fan} 番</Text>
                  <Text className="game__result-stat">
                    {result.loserSeat >= 0
                      ? `${loser?.nickname ?? ''} 付 ${result.score} 分`
                      : `每家付 ${result.score} 分`}
                  </Text>
                </View>
              </>
            )}

            {finished ? (
              <>
                <View className="game__result-scores">
                  {table.players.map((player) => (
                    <View className="game__score-row" key={player.seat}>
                      <Text className="game__score-name">{player.nickname}</Text>
                      <Text
                        className={`game__score-value ${
                          player.score >= 0 ? 'is-plus' : 'is-minus'
                        }`}
                      >
                        {player.score > 0 ? `+${player.score}` : player.score}
                      </Text>
                    </View>
                  ))}
                </View>
                <View className="mj-btn game__result-btn" onClick={backHome}>
                  返回首页
                </View>
              </>
            ) : isOwner ? (
              <View
                className="mj-btn game__result-btn"
                onClick={() => socket.send('NEXT_ROUND')}
              >
                开始下一局
              </View>
            ) : (
              <Text className="game__result-wait">等待房主开始下一局…</Text>
            )}
          </View>
        </View>
      )}
    </View>
  )
}
