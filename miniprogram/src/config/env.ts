/**
 * 后端地址。
 *
 * - 微信开发者工具：勾选「详情 → 本地设置 → 不校验合法域名」，可直接连本机。
 * - 真机预览：把 127.0.0.1 改成电脑局域网 IP，并保证手机与电脑同一网段。
 * - 上线：改成 wss://你的域名/ws/game，并在小程序后台配置 socket 合法域名。
 */
const BACKEND_PORT = '8080'

/** 小程序端固定连本机 / 局域网后端 */
const MINI_PROGRAM_WS = `ws://127.0.0.1:${BACKEND_PORT}/ws/game`

/**
 * H5 端按当前页面地址推导：
 * - 开发（localhost:10086 或静态服务 5173）：连同主机的 8080 端口
 * - 生产（https，或 80/443 端口）：同域 /ws/game，由 Nginx 反代到后端
 */
function resolveH5WsUrl(): string {
  if (typeof window === 'undefined' || !window.location) {
    return MINI_PROGRAM_WS
  }
  const { protocol, hostname, host, port } = window.location
  const wsProtocol = protocol === 'https:' ? 'wss:' : 'ws:'
  const isProduction = protocol === 'https:' || port === '' || port === '80' || port === '443'
  if (isProduction) {
    return `${wsProtocol}//${host}/ws/game`
  }
  return `${wsProtocol}//${hostname || '127.0.0.1'}:${BACKEND_PORT}/ws/game`
}

export const WS_URL = process.env.TARO_ENV === 'h5' ? resolveH5WsUrl() : MINI_PROGRAM_WS

/** 心跳间隔（毫秒） */
export const HEARTBEAT_INTERVAL = 25000
