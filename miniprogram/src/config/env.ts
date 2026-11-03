/**
 * 后端地址。
 *
 * - 微信开发者工具：勾选「详情 → 本地设置 → 不校验合法域名」，可直接连本机。
 * - 真机预览：把 127.0.0.1 改成电脑局域网 IP，并保证手机与电脑同一网段。
 * - 上线：改成 wss://你的域名/ws/game，并在小程序后台配置 socket 合法域名。
 */
const DEV_WS_HOST = '127.0.0.1:8080'

export const WS_URL = `ws://${DEV_WS_HOST}/ws/game`

/** 心跳间隔（毫秒） */
export const HEARTBEAT_INTERVAL = 25000
