import { Text, View } from '@tarojs/components'

import type { PlayerView } from '../../types/protocol'
import Tile from '../Tile'
import './index.scss'

interface PlayerSeatProps {
  player: PlayerView
  position: 'top' | 'left' | 'right' | 'bottom'
  isTurn: boolean
  remainSeconds?: number
  /** 本局癞子牌码，用于给副露/弃牌打角标 */
  wildcardCode?: string
  /** 癞子是否已作废 */
  wildcardVoided?: boolean
}

export default function PlayerSeat({
  player,
  position,
  isTurn,
  remainSeconds,
  wildcardCode,
  wildcardVoided
}: PlayerSeatProps) {
  const initial = (player.nickname || '闲').slice(0, 1)
  const isWild = (tile: string) => !!wildcardCode && !wildcardVoided && tile === wildcardCode
  const isVoided = (tile: string) => !!wildcardCode && wildcardVoided && tile === wildcardCode

  return (
    <View className={`seat seat--${position} ${isTurn ? 'is-turn' : ''}`}>
      <View className="seat__head">
        <View className="seat__avatar">{initial}</View>
        <View className="seat__meta">
          <View className="seat__name">
            <Text className="seat__name-text">{player.nickname || '空位'}</Text>
            {player.dealer && <Text className="seat__tag">庄</Text>}
            {!player.online && player.playerId && <Text className="seat__tag seat__tag--off">离线</Text>}
          </View>
          <Text className="seat__score">{player.score > 0 ? `+${player.score}` : player.score}</Text>
        </View>
      </View>

      {isTurn && remainSeconds !== undefined && (
        <View className="seat__timer">{remainSeconds}s</View>
      )}

      <View className="seat__count">手牌 {player.handCount} 张</View>

      {player.melds.length > 0 && (
        <View className="seat__melds">
          {player.melds.map((meld, index) => (
            <View className="seat__meld" key={`${meld.type}-${index}`}>
              {meld.tiles.length > 0 ? (
                meld.tiles.map((tile, tileIndex) => (
                  <Tile
                    code={tile}
                    size="xs"
                    key={`${tile}-${tileIndex}`}
                    wildcard={isWild(tile)}
                    voided={isVoided(tile)}
                  />
                ))
              ) : (
                <Tile code="" size="xs" back />
              )}
            </View>
          ))}
        </View>
      )}

      {player.discards.length > 0 && (
        <View className="seat__discards">
          {player.discards.map((tile, index) => (
            <Tile
              code={tile}
              size="xs"
              key={`${tile}-${index}`}
              wildcard={isWild(tile)}
              voided={isVoided(tile)}
            />
          ))}
        </View>
      )}
    </View>
  )
}
