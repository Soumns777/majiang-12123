import { Text, View } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { useEffect, useMemo, useRef, useState } from 'react'

import PlayerSeat from '../../components/PlayerSeat'
import Tile from '../../components/Tile'
import { socket } from '../../services/socket'
import { mySeat, useGameStore } from '../../store/gameStore'
import type { ActionType } from '../../types/protocol'
import { ACTION_LABEL } from '../../utils/tile'
import { relaunchHome } from '../../utils/nav'
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

  const [selectedIndex, setSelectedIndex] = useState<number | null>(null)
  const [picker, setPicker] = useState<PickerState | null>(null)
  const [extendSent, setExtendSent] = useState(false)
  const [now, setNow] = useState(Date.now())
  const offsetRef = useRef(0)
  const hadTable = useRef(false)

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
    if (table) {
      hadTable.current = true
      return
    }
    if (hadTable.current) {
      relaunchHome()
    }
  }, [table])

  const seatIndex = mySeat(table, playerId)
  const me = table?.players.find((player) => player.playerId === playerId)

  const others = useMemo(() => {
    if (!table) {
      return []
    }
    // 观战者没有自己的座位，就固定以 0 号位为「下家」，把 4 个人都摆出来
    const base = seatIndex >= 0 ? seatIndex : 0
    return [1, 2, 3].map((offset) => table.players[(base + offset) % 4])
  }, [table, seatIndex])

  /** 观战时补上「自己那一侧」的座位，否则 4 家里会少画一家 */
  const bottomPlayer = table && seatIndex < 0 ? table.players[0] : undefined

  const handKey = me?.hand.join(',') ?? ''
  const meldKey = me?.melds.map((meld) => meld.tiles[0]).join(',') ?? ''

  // 手牌一变（摸牌/出牌）就清掉选中态，避免索引错位指向别的牌
  useEffect(() => {
    setSelectedIndex(null)
  }, [handKey])

  // 延时申请一旦结束（通过/被拒/超时）就解锁，下次还能再申请
  const extendSeat = table?.extend?.seat
  useEffect(() => {
    if (extendSeat === undefined) {
      setExtendSent(false)
    }
  }, [extendSeat])

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

  if (!table) {
    return <View className="game" />
  }

  // 观战者没有座位（me 为空），但依然要能看到整张牌桌
  const isMyTurn = !!me && table.phase === 'PLAYER_TURN' && table.currentSeat === seatIndex
  const wildcardCode = table.wildcard
  const wildcardVoided = !!table.wildcardVoided
  // 癞子角标：未作废时所有该牌都标金；作废后改标划线置灰
  const isWild = (tile: string) => !!wildcardCode && !wildcardVoided && tile === wildcardCode
  const isVoided = (tile: string) => !!wildcardCode && wildcardVoided && tile === wildcardCode
  const isClaiming = table.phase === 'WAITING_CLAIM'
  const actions = me?.actions ?? []
  const remain =
    table.phase === 'ENDED'
      ? 0
      : Math.max(0, Math.ceil((table.deadlineAt - (now + offsetRef.current)) / 1000))

  // 延时：只有「轮到自己」或「等着自己响应」时才能申请，一局同时只有一个申请
  const extend = table.extend
  const isExtendRequester = !!extend && extend.seat === seatIndex
  const extendApproved = !!extend && seatIndex >= 0 && extend.approvals.includes(seatIndex)
  const canAskExtend =
    !extend &&
    !extendSent &&
    table.phase !== 'ENDED' &&
    (isMyTurn || (isClaiming && actions.includes('PASS')))

  const actionButtons = actions
    .filter((action) => action !== 'DISCARD')
    .sort((a, b) => (ACTION_PRIORITY[a] ?? 5) - (ACTION_PRIORITY[b] ?? 5))

  const currentPlayer = table.players.find((player) => player.seat === table.currentSeat)
  const result = table.result
  const winner = result ? table.players.find((player) => player.seat === result.winnerSeat) : null
  const dealer = table.players.find((player) => player.dealer)
  const extendPlayer = extend ? table.players.find((player) => player.seat === extend.seat) : null
  const isOwner = table.ownerId === playerId
  const finished = table.status === 'FINISHED'

  const sendAction = (action: ActionType, tile?: string) => {
    socket.send('GAME_ACTION', { action, tile })
    setSelectedIndex(null)
    setPicker(null)
  }

  // 按索引记录选中，避免手上有两张相同牌时一起高亮
  const onTileClick = (index: number) => {
    if (!isMyTurn) {
      return
    }
    setSelectedIndex((prev) => (prev === index ? null : index))
  }

  const selectedTile =
    me && selectedIndex !== null ? me.hand[selectedIndex] ?? null : null

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

  /** 申请延时：其他三家全同意才生效，延长一个基础思考时间 */
  const askExtend = () => {
    if (!canAskExtend) {
      return
    }
    setExtendSent(true)
    socket.send('GAME_EXTEND', {})
  }

  const voteExtend = (agree: boolean) => {
    socket.send('GAME_EXTEND', { agree })
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
          <Text className="game__bar-key">底分</Text>
          <Text className="game__bar-value game__bar-value--gold">{table.baseScore}</Text>
        </View>
        <View className="game__bar-item">
          <Text className="game__bar-key">剩余</Text>
          <Text className="game__bar-value">{table.remainingTiles}</Text>
        </View>
        <View className="game__bar-item">
          <Text className="game__bar-key">癞子</Text>
          <Text
            className={`game__bar-value ${
              wildcardVoided ? 'game__bar-value--voided' : 'game__bar-value--gold'
            }`}
          >
            {table.wildcardLabel}
          </Text>
          {wildcardVoided && <Text className="game__bar-flag">作废</Text>}
        </View>
      </View>

      <View className="game__arena">
        {bottomPlayer && (
          <View className="game__seat game__seat--bottom">
            <PlayerSeat
              player={bottomPlayer}
              position="bottom"
              isTurn={table.currentSeat === bottomPlayer.seat && table.phase !== 'ENDED'}
              remainSeconds={remain}
              wildcardCode={wildcardCode}
              wildcardVoided={wildcardVoided}
            />
          </View>
        )}
        {others[1] && (
          <View className="game__seat game__seat--top">
            <PlayerSeat
              player={others[1]}
              position="top"
              isTurn={table.currentSeat === others[1].seat && table.phase !== 'ENDED'}
              remainSeconds={remain}
              wildcardCode={wildcardCode}
              wildcardVoided={wildcardVoided}
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
              wildcardCode={wildcardCode}
              wildcardVoided={wildcardVoided}
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
              wildcardCode={wildcardCode}
              wildcardVoided={wildcardVoided}
            />
          </View>
        )}

        <View className="game__center">
          <View className="game__last">
            {table.lastDiscardTile ? (
              <Tile
                code={table.lastDiscardTile}
                size="md"
                wildcard={isWild(table.lastDiscardTile)}
                voided={isVoided(table.lastDiscardTile)}
              />
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

          {wildcardVoided && (
            <View className="game__void">
              <Text className="game__void-text">有人打出癞子 · 本局癞子已作废</Text>
            </View>
          )}
        </View>
      </View>

      {!me ? (
        <View className="game__watch-bar">
          <Text className="game__watch-tag">观战中</Text>
          <Text className="game__watch-text">
            {table.spectators.length > 1
              ? `${table.spectators.length} 人围观 · 看不到任何人的手牌`
              : '看不到任何人的手牌，只能看四家出牌'}
          </Text>
          <View className="game__watch-exit" onClick={backHome}>
            退出
          </View>
        </View>
      ) : (
      <View className="game__mine">
        {me.discards.length > 0 && (
          <View className="game__my-discards">
            {me.discards.map((tile, index) => (
              <Tile
                code={tile}
                size="xxs"
                key={`${tile}-${index}`}
                wildcard={isWild(tile)}
                voided={isVoided(tile)}
              />
            ))}
          </View>
        )}

        {me.melds.length > 0 && (
          <View className="game__my-melds">
            {me.melds.map((meld, index) => (
              <View className="game__meld" key={`${meld.type}-${index}`}>
                {meld.tiles.map((tile, tileIndex) => (
                  <Tile
                    code={tile}
                    size="xs"
                    key={`${tile}-${tileIndex}`}
                    wildcard={isWild(tile)}
                    voided={isVoided(tile)}
                  />
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
              selected={selectedIndex === index}
              wildcard={isWild(tile)}
              voided={isVoided(tile)}
              onClick={() => onTileClick(index)}
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
              className={`game__action game__action--primary ${selectedTile ? '' : 'is-disabled'}`}
              onClick={() => selectedTile && sendAction('DISCARD', selectedTile)}
            >
              出牌
            </View>
          )}
          {canAskExtend && (
            <View className="game__action game__action--ghost" onClick={askExtend}>
              延时
            </View>
          )}
        </View>
      </View>
      )}

      {extend && (
        <View className="game__extend">
          <View className="game__extend-head">
            <Text className="game__extend-title">
              {isExtendRequester ? '已申请延时' : `${extendPlayer?.nickname ?? ''} 申请延时`}
            </Text>
            <Text className="game__extend-count">
              {extend.approvals.length}/{extend.need} 同意 · 剩 {remain}s
            </Text>
          </View>

          <View className="game__extend-dots">
            {table.players.map((player) => {
              const requester = player.seat === extend.seat
              const agreed = extend.approvals.includes(player.seat)
              return (
                <View
                  className={`game__extend-dot ${requester ? 'is-ask' : ''} ${
                    agreed ? 'is-yes' : ''
                  }`}
                  key={player.seat}
                >
                  <Text className="game__extend-name">
                    {(player.nickname || '闲').slice(0, 2)}
                  </Text>
                  <Text className="game__extend-state">
                    {requester ? '申请' : agreed ? '同意' : '待定'}
                  </Text>
                </View>
              )
            })}
          </View>

          {isExtendRequester || seatIndex < 0 ? (
            <Text className="game__extend-wait">
              {seatIndex < 0
                ? '观战中，不参与投票'
                : '等待其他三家同意，全同意即延长一个基础时长'}
            </Text>
          ) : extendApproved ? (
            <Text className="game__extend-wait">你已同意，等待其他玩家…</Text>
          ) : (
            <View className="game__extend-actions">
              <View
                className="game__extend-btn game__extend-btn--no"
                onClick={() => voteExtend(false)}
              >
                不同意
              </View>
              <View
                className="game__extend-btn game__extend-btn--yes"
                onClick={() => voteExtend(true)}
              >
                同意
              </View>
            </View>
          )}
        </View>
      )}

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
                  wildcard={isWild(tile)}
                  voided={isVoided(tile)}
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
              {result.drawGame ? '荒庄' : '自摸胡'}
            </Text>

            {!result.drawGame && (
              <>
                <Text className="game__result-winner">{winner?.nickname ?? ''}</Text>

                {/* 胡了什么：逐项列出牌型分，再给出 牌型分 × 底分 的算式 */}
                <View className="game__breakdown">
                  {result.breakdown.map((item, index) => (
                    <View className="game__bd-row" key={`${item.label}-${index}`}>
                      <Text className="game__bd-label">{item.label}</Text>
                      <Text className={`game__bd-fan ${item.fan > 0 ? '' : 'is-zero'}`}>
                        {item.fan > 0 ? `+${item.fan}` : '0'}
                      </Text>
                    </View>
                  ))}
                  <View className="game__bd-row game__bd-row--total">
                    <Text className="game__bd-label">牌型分</Text>
                    <Text className="game__bd-fan">{result.fan}</Text>
                  </View>
                </View>

                <View className="game__result-hand">
                  {result.winningHand.map((tile, index) => (
                    <Tile code={tile} size="xs" key={`${tile}-${index}`} />
                  ))}
                </View>

                <Text className="game__result-formula">
                  {result.fan} 分 × 底分 {result.baseScore} = 单家 {result.score} 豆 · 自摸三家各付
                </Text>
                {dealer && dealer.seat !== result.winnerSeat && (
                  <Text className="game__result-note">
                    头家 {dealer.nickname} 额外多扣 1 豆（按规则头家 ±1）
                  </Text>
                )}

                <View className="game__beans">
                  {table.players.map((player) => {
                    const delta =
                      result.deltas.find((item) => item.seat === player.seat)?.beans ?? 0
                    return (
                      <View className="game__bean-row" key={player.seat}>
                        <Text className="game__bean-name">{player.nickname}</Text>
                        <Text
                          className={`game__bean-delta ${delta >= 0 ? 'is-plus' : 'is-minus'}`}
                        >
                          {delta > 0 ? `+${delta}` : delta}
                        </Text>
                        <Text className="game__bean-left">{player.beans} 豆</Text>
                      </View>
                    )
                  })}
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
