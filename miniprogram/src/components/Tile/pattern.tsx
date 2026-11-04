import { View } from '@tarojs/components'

/**
 * 麻将牌面图形（筒 / 条）。
 *
 * <p>为什么不用内联 SVG：微信小程序的 WXML 不支持 `<svg>`，只有 H5 能渲染，
 * 两端会不一致。这里改成「绝对定位的 div」按虚拟画布排版，
 * 小程序和 H5 表现完全一致。</p>
 *
 * <p>虚拟画布取 100 × 144（与牌面 1 : 1.44 的比例对齐）。
 * 百分比高度是相对容器高度的，所以横向尺寸除以 W、纵向尺寸除以 H，
 * 这样正方形元素在任意尺寸的牌上都不会被拉成椭圆。</p>
 */

const W = 100
const H = 144

const leftOf = (v: number) => `${((v / W) * 100).toFixed(2)}%`
const topOf = (v: number) => `${((v / H) * 100).toFixed(2)}%`
const widthOf = (v: number) => `${((v / W) * 100).toFixed(2)}%`
const heightOf = (v: number) => `${((v / H) * 100).toFixed(2)}%`

const rect = (x: number, y: number, w: number, h: number) => ({
  left: leftOf(x),
  top: topOf(y),
  width: widthOf(w),
  height: heightOf(h)
})

const circle = (cx: number, cy: number, r: number) => rect(cx - r, cy - r, r * 2, r * 2)

/** 筒子：圆心 x / y 与半径，按市面上常见的点位排布 */
const DOTS: Record<number, [number, number, number][]> = {
  1: [[50, 72, 17]],
  2: [
    [50, 44, 13],
    [50, 100, 13]
  ],
  3: [
    [28, 32, 11],
    [50, 72, 11],
    [72, 112, 11]
  ],
  4: [
    [32, 42, 11],
    [68, 42, 11],
    [32, 102, 11],
    [68, 102, 11]
  ],
  5: [
    [30, 38, 10],
    [70, 38, 10],
    [50, 72, 10],
    [30, 106, 10],
    [70, 106, 10]
  ],
  6: [
    [32, 30, 10],
    [68, 30, 10],
    [32, 72, 10],
    [68, 72, 10],
    [32, 114, 10],
    [68, 114, 10]
  ],
  7: [
    [50, 22, 8.5],
    [28, 52, 8.5],
    [72, 52, 8.5],
    [28, 92, 8.5],
    [72, 92, 8.5],
    [28, 126, 8.5],
    [72, 126, 8.5]
  ],
  8: [
    [32, 24, 8.5],
    [68, 24, 8.5],
    [32, 56, 8.5],
    [68, 56, 8.5],
    [32, 88, 8.5],
    [68, 88, 8.5],
    [32, 120, 8.5],
    [68, 120, 8.5]
  ],
  9: [
    [28, 26, 8],
    [50, 26, 8],
    [72, 26, 8],
    [28, 72, 8],
    [50, 72, 8],
    [72, 72, 8],
    [28, 118, 8],
    [50, 118, 8],
    [72, 118, 8]
  ]
}

/** 条子：竹节的中心点与宽高（1 条单独画鸟） */
const STICKS: Record<number, [number, number, number, number][]> = {
  2: [
    [33, 72, 13, 74],
    [67, 72, 13, 74]
  ],
  3: [
    [50, 34, 12, 48],
    [32, 108, 12, 48],
    [68, 108, 12, 48]
  ],
  4: [
    [32, 42, 13, 50],
    [68, 42, 13, 50],
    [32, 102, 13, 50],
    [68, 102, 13, 50]
  ],
  5: [
    [28, 34, 12, 42],
    [72, 34, 12, 42],
    [50, 72, 12, 42],
    [28, 110, 12, 42],
    [72, 110, 12, 42]
  ],
  6: [
    [32, 28, 12, 40],
    [68, 28, 12, 40],
    [32, 72, 12, 40],
    [68, 72, 12, 40],
    [32, 116, 12, 40],
    [68, 116, 12, 40]
  ],
  7: [
    [50, 20, 11, 34],
    [26, 62, 11, 34],
    [50, 62, 11, 34],
    [74, 62, 11, 34],
    [26, 112, 11, 34],
    [50, 112, 11, 34],
    [74, 112, 11, 34]
  ],
  8: [
    [32, 20, 12, 32],
    [68, 20, 12, 32],
    [32, 55, 12, 32],
    [68, 55, 12, 32],
    [32, 90, 12, 32],
    [68, 90, 12, 32],
    [32, 125, 12, 32],
    [68, 125, 12, 32]
  ],
  9: [
    [26, 26, 11, 38],
    [50, 26, 11, 38],
    [74, 26, 11, 38],
    [26, 72, 11, 38],
    [50, 72, 11, 38],
    [74, 72, 11, 38],
    [26, 118, 11, 38],
    [50, 118, 11, 38],
    [74, 118, 11, 38]
  ]
}

/** 1 条：幺鸡（简化的鸟形，绿身红喙） */
function Bird() {
  return (
    <View className="mj-face">
      <View className="mj-bird__tail" style={rect(6, 56, 30, 11)} />
      <View className="mj-bird__tail" style={rect(10, 74, 26, 10)} />
      <View className="mj-bird__body" style={rect(24, 44, 46, 44)} />
      <View className="mj-bird__wing" style={rect(34, 56, 26, 20)} />
      <View className="mj-bird__head" style={rect(56, 24, 26, 26)} />
      <View className="mj-bird__beak" style={rect(80, 34, 12, 9)} />
      <View className="mj-bird__eye" style={rect(66, 33, 5, 5)} />
      <View className="mj-bird__foot" style={rect(38, 92, 4, 20)} />
      <View className="mj-bird__foot" style={rect(50, 92, 4, 20)} />
    </View>
  )
}

export interface TilePatternProps {
  /** 花色代码：T=筒 B=条 */
  suit: string
  /** 点数 1-9 */
  rank: number
}

export default function TilePattern({ suit, rank }: TilePatternProps) {
  if (suit === 'T') {
    const dots = DOTS[rank] ?? []
    return (
      <View className="mj-face">
        {dots.map(([cx, cy, r], index) => (
          <View
            key={index}
            className={`mj-dot ${rank === 1 ? 'mj-dot--big' : ''}`}
            style={circle(cx, cy, r)}
          />
        ))}
      </View>
    )
  }

  if (suit === 'B') {
    if (rank === 1) {
      return <Bird />
    }
    const sticks = STICKS[rank] ?? []
    return (
      <View className="mj-face">
        {sticks.map(([cx, cy, w, h], index) => (
          <View key={index} className="mj-stick" style={rect(cx - w / 2, cy - h / 2, w, h)} />
        ))}
      </View>
    )
  }

  return null
}
