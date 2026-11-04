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
 *
 * <p>配色按市面常见牌面：筒是「彩环 + 白圈 + 实心点」，一筒为整幅团花；
 * 条是竹节，五条中心、七条首张、九条为红色，其余绿色。</p>
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

type Tone = 'blue' | 'green' | 'red'

interface Dot {
  cx: number
  cy: number
  r: number
  tone: Tone
}

/**
 * 筒子点位与配色。
 *
 * 参考常见牌面：二筒蓝上绿下、三筒蓝红绿斜排、四筒蓝绿对角、
 * 五筒四角蓝中心红、六筒红绿相间、七筒绿三红四、八筒全蓝、九筒红绿相间。
 */
const DOTS: Record<number, Dot[]> = {
  1: [{ cx: 50, cy: 72, r: 21, tone: 'blue' }],
  2: [
    { cx: 50, cy: 44, r: 15, tone: 'blue' },
    { cx: 50, cy: 100, r: 15, tone: 'green' }
  ],
  3: [
    { cx: 28, cy: 32, r: 12, tone: 'blue' },
    { cx: 50, cy: 72, r: 12, tone: 'red' },
    { cx: 72, cy: 112, r: 12, tone: 'green' }
  ],
  4: [
    { cx: 32, cy: 42, r: 12, tone: 'blue' },
    { cx: 68, cy: 42, r: 12, tone: 'green' },
    { cx: 32, cy: 102, r: 12, tone: 'green' },
    { cx: 68, cy: 102, r: 12, tone: 'blue' }
  ],
  5: [
    { cx: 29, cy: 36, r: 11, tone: 'blue' },
    { cx: 71, cy: 36, r: 11, tone: 'blue' },
    { cx: 50, cy: 72, r: 11, tone: 'red' },
    { cx: 29, cy: 108, r: 11, tone: 'blue' },
    { cx: 71, cy: 108, r: 11, tone: 'blue' }
  ],
  6: [
    { cx: 32, cy: 28, r: 11, tone: 'red' },
    { cx: 68, cy: 28, r: 11, tone: 'red' },
    { cx: 32, cy: 72, r: 11, tone: 'green' },
    { cx: 68, cy: 72, r: 11, tone: 'green' },
    { cx: 32, cy: 116, r: 11, tone: 'red' },
    { cx: 68, cy: 116, r: 11, tone: 'red' }
  ],
  7: [
    { cx: 50, cy: 20, r: 9, tone: 'green' },
    { cx: 28, cy: 50, r: 9, tone: 'green' },
    { cx: 72, cy: 50, r: 9, tone: 'green' },
    { cx: 28, cy: 88, r: 9, tone: 'red' },
    { cx: 72, cy: 88, r: 9, tone: 'red' },
    { cx: 28, cy: 124, r: 9, tone: 'red' },
    { cx: 72, cy: 124, r: 9, tone: 'red' }
  ],
  8: [
    { cx: 32, cy: 22, r: 9, tone: 'blue' },
    { cx: 68, cy: 22, r: 9, tone: 'blue' },
    { cx: 32, cy: 55, r: 9, tone: 'blue' },
    { cx: 68, cy: 55, r: 9, tone: 'blue' },
    { cx: 32, cy: 89, r: 9, tone: 'blue' },
    { cx: 68, cy: 89, r: 9, tone: 'blue' },
    { cx: 32, cy: 122, r: 9, tone: 'blue' },
    { cx: 68, cy: 122, r: 9, tone: 'blue' }
  ],
  9: [
    { cx: 28, cy: 26, r: 8.5, tone: 'red' },
    { cx: 50, cy: 26, r: 8.5, tone: 'green' },
    { cx: 72, cy: 26, r: 8.5, tone: 'red' },
    { cx: 28, cy: 72, r: 8.5, tone: 'green' },
    { cx: 50, cy: 72, r: 8.5, tone: 'red' },
    { cx: 72, cy: 72, r: 8.5, tone: 'green' },
    { cx: 28, cy: 118, r: 8.5, tone: 'red' },
    { cx: 50, cy: 118, r: 8.5, tone: 'green' },
    { cx: 72, cy: 118, r: 8.5, tone: 'red' }
  ]
}

interface Stick {
  cx: number
  cy: number
  w: number
  h: number
  tone: Tone
}

/**
 * 条子（竹节）点位。一条单独画鸟，从二条开始排。
 *
 * 常见牌面：五条中间一根红、七条顶上一根红、九条红绿相间。
 */
const STICKS: Record<number, Stick[]> = {
  2: [
    { cx: 33, cy: 72, w: 15, h: 78, tone: 'green' },
    { cx: 67, cy: 72, w: 15, h: 78, tone: 'green' }
  ],
  3: [
    { cx: 50, cy: 36, w: 14, h: 52, tone: 'green' },
    { cx: 32, cy: 106, w: 14, h: 52, tone: 'green' },
    { cx: 68, cy: 106, w: 14, h: 52, tone: 'green' }
  ],
  4: [
    { cx: 32, cy: 44, w: 15, h: 54, tone: 'green' },
    { cx: 68, cy: 44, w: 15, h: 54, tone: 'green' },
    { cx: 32, cy: 100, w: 15, h: 54, tone: 'green' },
    { cx: 68, cy: 100, w: 15, h: 54, tone: 'green' }
  ],
  5: [
    { cx: 27, cy: 36, w: 14, h: 46, tone: 'green' },
    { cx: 73, cy: 36, w: 14, h: 46, tone: 'green' },
    { cx: 50, cy: 72, w: 14, h: 46, tone: 'red' },
    { cx: 27, cy: 108, w: 14, h: 46, tone: 'green' },
    { cx: 73, cy: 108, w: 14, h: 46, tone: 'green' }
  ],
  6: [
    { cx: 32, cy: 28, w: 14, h: 44, tone: 'green' },
    { cx: 68, cy: 28, w: 14, h: 44, tone: 'green' },
    { cx: 32, cy: 72, w: 14, h: 44, tone: 'green' },
    { cx: 68, cy: 72, w: 14, h: 44, tone: 'green' },
    { cx: 32, cy: 116, w: 14, h: 44, tone: 'green' },
    { cx: 68, cy: 116, w: 14, h: 44, tone: 'green' }
  ],
  7: [
    { cx: 50, cy: 20, w: 13, h: 36, tone: 'red' },
    { cx: 25, cy: 62, w: 13, h: 38, tone: 'green' },
    { cx: 50, cy: 62, w: 13, h: 38, tone: 'green' },
    { cx: 75, cy: 62, w: 13, h: 38, tone: 'green' },
    { cx: 25, cy: 112, w: 13, h: 38, tone: 'green' },
    { cx: 50, cy: 112, w: 13, h: 38, tone: 'green' },
    { cx: 75, cy: 112, w: 13, h: 38, tone: 'green' }
  ],
  8: [
    { cx: 32, cy: 22, w: 13, h: 32, tone: 'green' },
    { cx: 68, cy: 22, w: 13, h: 32, tone: 'green' },
    { cx: 32, cy: 56, w: 13, h: 32, tone: 'green' },
    { cx: 68, cy: 56, w: 13, h: 32, tone: 'green' },
    { cx: 32, cy: 90, w: 13, h: 32, tone: 'green' },
    { cx: 68, cy: 90, w: 13, h: 32, tone: 'green' },
    { cx: 32, cy: 124, w: 13, h: 32, tone: 'green' },
    { cx: 68, cy: 124, w: 13, h: 32, tone: 'green' }
  ],
  9: [
    { cx: 26, cy: 26, w: 12, h: 38, tone: 'red' },
    { cx: 50, cy: 26, w: 12, h: 38, tone: 'green' },
    { cx: 74, cy: 26, w: 12, h: 38, tone: 'red' },
    { cx: 26, cy: 72, w: 12, h: 38, tone: 'green' },
    { cx: 50, cy: 72, w: 12, h: 38, tone: 'red' },
    { cx: 74, cy: 72, w: 12, h: 38, tone: 'green' },
    { cx: 26, cy: 118, w: 12, h: 38, tone: 'red' },
    { cx: 50, cy: 118, w: 12, h: 38, tone: 'green' },
    { cx: 74, cy: 118, w: 12, h: 38, tone: 'red' }
  ]
}

/** 一条：幺鸡（绿身红喙红爪，白圈黑眼） */
function Bird() {
  return (
    <View className="mj-face">
      <View className="mj-bird__tail" style={rect(4, 52, 34, 12)} />
      <View className="mj-bird__tail" style={rect(8, 72, 30, 11)} />
      <View className="mj-bird__body" style={rect(22, 40, 48, 48)} />
      <View className="mj-bird__wing" style={rect(32, 52, 28, 24)} />
      <View className="mj-bird__head" style={rect(54, 20, 28, 28)} />
      <View className="mj-bird__beak" style={rect(80, 31, 14, 9)} />
      <View className="mj-bird__eye" style={rect(64, 29, 8, 8)} />
      <View className="mj-bird__foot" style={rect(36, 88, 5, 24)} />
      <View className="mj-bird__foot" style={rect(50, 88, 5, 24)} />
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
        {dots.map((dot, index) => (
          <View
            key={index}
            className={`mj-dot mj-dot--${dot.tone} ${rank === 1 ? 'mj-dot--big' : ''}`}
            style={circle(dot.cx, dot.cy, dot.r)}
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
        {sticks.map((stick, index) => (
          <View
            key={index}
            className={`mj-stick mj-stick--${stick.tone}`}
            style={rect(stick.cx - stick.w / 2, stick.cy - stick.h / 2, stick.w, stick.h)}
          />
        ))}
      </View>
    )
  }

  return null
}
