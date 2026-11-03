import Taro from '@tarojs/taro'
import { HEARTBEAT_INTERVAL } from '../config/env'

type MessageHandler = (type: string, data: any) => void
type StatusHandler = (connected: boolean) => void

/**
 * WebSocket 客户端封装：自动重连 + 心跳 + 断线期间的消息暂存。
 */
class GameSocket {
  private task: any = null
  private url = ''
  private handlers = new Set<MessageHandler>()
  private statusHandlers = new Set<StatusHandler>()
  private outbox: string[] = []
  private connected = false
  private manualClose = false
  private retry = 0
  private heartbeatTimer: any = null
  private reconnectTimer: any = null

  connect(url: string) {
    if (this.task && this.connected && this.url === url) {
      return
    }
    this.url = url
    this.manualClose = false
    this.open()
  }

  private open() {
    this.clearTimers()
    let result: any
    try {
      result = Taro.connectSocket({ url: this.url })
    } catch (error) {
      this.scheduleReconnect()
      return
    }

    // 微信端返回 SocketTask，H5 端返回 Promise<SocketTask>，两种都要兼容
    if (result && typeof result.then === 'function') {
      result
        .then((task: any) => this.bindTask(task))
        .catch(() => this.scheduleReconnect())
      return
    }
    this.bindTask(result)
  }

  private bindTask(task: any) {
    if (!task) {
      this.scheduleReconnect()
      return
    }
    this.task = task

    this.task.onOpen(() => {
      this.connected = true
      this.retry = 0
      this.notifyStatus(true)
      this.flush()
      this.startHeartbeat()
    })

    this.task.onMessage((res: any) => {
      let payload: any
      try {
        payload = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
      } catch (error) {
        return
      }
      if (!payload || !payload.type) {
        return
      }
      this.handlers.forEach((handler) => handler(payload.type, payload.data ?? {}))
    })

    this.task.onClose(() => {
      this.connected = false
      this.notifyStatus(false)
      this.scheduleReconnect()
    })

    this.task.onError(() => {
      this.connected = false
      this.notifyStatus(false)
      this.scheduleReconnect()
    })
  }

  send(type: string, data?: Record<string, any>) {
    const message = JSON.stringify({ type, data: data ?? {} })
    if (this.connected && this.task) {
      this.task.send({ data: message })
      return
    }
    if (this.outbox.length < 20) {
      this.outbox.push(message)
    }
  }

  on(handler: MessageHandler) {
    this.handlers.add(handler)
    return () => this.handlers.delete(handler)
  }

  onStatus(handler: StatusHandler) {
    this.statusHandlers.add(handler)
    return () => this.statusHandlers.delete(handler)
  }

  isConnected() {
    return this.connected
  }

  close() {
    this.manualClose = true
    this.clearTimers()
    if (this.task) {
      this.task.close({})
      this.task = null
    }
    this.connected = false
    this.notifyStatus(false)
  }

  private flush() {
    const pending = this.outbox.splice(0, this.outbox.length)
    pending.forEach((message) => this.task?.send({ data: message }))
  }

  private startHeartbeat() {
    this.heartbeatTimer = setInterval(() => {
      if (this.connected) {
        this.send('PING')
      }
    }, HEARTBEAT_INTERVAL)
  }

  private scheduleReconnect() {
    if (this.manualClose || this.reconnectTimer) {
      return
    }
    const delay = Math.min(1000 * 2 ** this.retry, 10000)
    this.retry += 1
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      this.open()
    }, delay)
  }

  private clearTimers() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }

  private notifyStatus(connected: boolean) {
    this.statusHandlers.forEach((handler) => handler(connected))
  }
}

export const socket = new GameSocket()
