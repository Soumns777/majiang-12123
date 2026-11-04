import { Text, View } from '@tarojs/components'

import { parseTile } from '../../utils/tile'
import TilePattern from './pattern'
import './index.scss'

export interface TileProps {
  code: string
  size?: 'xxs' | 'xs' | 'sm' | 'md' | 'hand'
  selected?: boolean
  dimmed?: boolean
  /** 只显示牌背 */
  back?: boolean
  /** 本局癞子：金色牌面 + 「癞」字角标 */
  wildcard?: boolean
  /** 癞子已被作废：划线置灰 */
  voided?: boolean
  onClick?: (code: string) => void
}

/** 字牌配色：中红、發绿，其余墨色 */
const HONOR_CLASS: Record<string, string> = {
  C: 'is-red',
  F: 'is-green'
}

function WildBadge() {
  return (
    <View className="mj-tile__wild">
      <Text className="mj-tile__wild-text">癞</Text>
    </View>
  )
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

  const handleClick = () => onClick?.(code)

  // 筒子 / 条子：用图形牌面，不再写「五筒」这种文字
  if (!face.honor && (face.suit === 'T' || face.suit === 'B')) {
    return (
      <View className={className} onClick={handleClick}>
        <TilePattern suit={face.suit} rank={face.rank} />
        {wildcard && <WildBadge />}
      </View>
    )
  }

  // 万子：中文数字 + 萬
  if (!face.honor) {
    return (
      <View className={className} onClick={handleClick}>
        <Text className="mj-tile__rank mj-tile__rank--W">{face.rankLabel}</Text>
        <Text className="mj-tile__suit mj-tile__suit--W">萬</Text>
        {wildcard && <WildBadge />}
      </View>
    )
  }

  // 白板：真实牌面是空白的，只留一道内框
  if (code === 'P') {
    return (
      <View className={className} onClick={handleClick}>
        <View className="mj-tile__blank" />
        {wildcard && <WildBadge />}
      </View>
    )
  }

  return (
    <View className={className} onClick={handleClick}>
      <Text className={`mj-tile__honor ${HONOR_CLASS[code] ?? ''}`}>{face.rankLabel}</Text>
      {wildcard && <WildBadge />}
    </View>
  )
}
