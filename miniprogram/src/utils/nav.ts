import Taro from '@tarojs/taro'

/**
 * 页面跳转守卫。
 *
 * <p>问题背景：Taro 的页面栈不会卸载旧页面，`navigateTo` 之后上一页的实例仍然活着，
 * 它的 `useEffect` 也会继续跑。而 `ROOM_STATE` 每次广播都会生成新的 `table` 对象，
 * 于是隐藏页面里的「进入房间/牌桌」effect 会被反复触发，
 * 每操作一次就往栈里再 push 一个同样的页面 —— 表现为整页重建、界面闪烁。</p>
 *
 * <p>这里统一走 `navigateOnce`：目标页已经是当前页就不跳，
 * 同一次状态变化（400ms 内）也只跳一次。</p>
 */

let lastNavAt = 0

/** 当前栈顶页面路径，统一成 `/pages/xxx/index` 形式。 */
export function currentRoute(): string {
  if (process.env.TARO_ENV === 'h5') {
    const hash = typeof window === 'undefined' ? '' : window.location.hash
    return hash.replace(/^#/, '') || '/'
  }
  const pages = Taro.getCurrentPages()
  const top = pages[pages.length - 1]
  return top?.route ? `/${top.route}` : ''
}

/**
 * 只在「目标页不是当前页」时才跳转。
 *
 * @returns 是否真的执行了跳转
 */
export function navigateOnce(url: string, mode: 'push' | 'replace' = 'push'): boolean {
  const target = url.split('?')[0]
  if (currentRoute() === target) {
    return false
  }
  const now = Date.now()
  if (now - lastNavAt < 400) {
    return false
  }
  lastNavAt = now
  if (mode === 'replace') {
    Taro.redirectTo({ url })
  } else {
    Taro.navigateTo({ url })
  }
  return true
}

/** 回到首页（清空页面栈）。 */
export function relaunchHome(): void {
  Taro.reLaunch({ url: '/pages/index/index' })
}
