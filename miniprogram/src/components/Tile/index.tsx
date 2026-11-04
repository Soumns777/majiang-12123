import { Text, View } from '@tarojs/components'

import { parseTile } from '../../utils/tile'
import './index.scss'

export interface TileProps {
  code: string
  size?: 'xs' | 'sm' | 'md' | 'hand'
  selected?: boolean
  dimmed?: boolean
  /** 只显示牌背 */
  back?: boolean
  /** 本局癞子：加金色角标 */
  wildcard?: boolean
  /** 癞子已被作废：牌面加删除线 */
  voided?: boolean
  onClick?: (code: string) => void
}

const HONOR_CLASS: Record<string, string> = {
  C: 'is-red',
  F: 'is-green',
  P: 'is-blue'
}

export default function Tile({
  code,
  size = 'md',
  selected,
  dimmed,
  back,
  wildcard,
  voided,
  onClick
}: TileProps) {
  if (back) {
    return <View className={`mj-tile mj-tile--${size} mj-tile--back`} />
  }

  const face = parseTile(code)
  const className = [
    'mj-tile',
    `mj-tile--${size}`,
    selected ? 'is-selected' : '',
    dimmed ? 'is-dimmed' : '',
    wildcard ? 'is-wildcard' : '',
    voided ? 'is-voided' : ''
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <View className={className} onClick={() => onClick?.(code)}>
      {face.honor ? (
        <Text className={`mj-tile__honor ${HONOR_CLASS[code] ?? ''}`}>{face.rankLabel}</Text>
      ) : (
        <>
          <Text className={`mj-tile__rank mj-tile__rank--${face.suit}`}>{face.rankLabel}</Text>
          <Text className={`mj-tile__suit mj-tile__suit--${face.suit}`}>{face.suitLabel}</Text>
        </>
      )}
    </View>
  )
}
