#!/usr/bin/env node
/**
 * 四人联机冒烟测试：4 个客户端自动建房、准备、开局、摸打碰胡，直到一局结束。
 *
 * 用法：
 *   node scripts/ws-smoke.mjs
 *   WS_URL=ws://192.168.1.10:8080/ws/game node scripts/ws-smoke.mjs
 */

const WS_URL = process.env.WS_URL || 'ws://127.0.0.1:8080/ws/game'
const NAMES = ['东家', '南家', '西家', '北家']

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

function createClient(name) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(WS_URL)
    const client = { name, ws, playerId: '', table: null, errors: [], lastSig: '' }

    ws.addEventListener('open', () => resolve(client))
    ws.addEventListener('error', (event) => reject(new Error(`${name} 连接失败: ${event.message ?? ''}`)))
    ws.addEventListener('message', (event) => {
      let msg
      try {
        msg = JSON.parse(event.data)
      } catch {
        return
      }
      if (msg.type === 'HELLO_ACK') {
        client.playerId = msg.data.playerId
      } else if (msg.type === 'ROOM_STATE') {
        client.table = msg.data
      } else if (msg.type === 'ERROR') {
        client.errors.push(msg.data)
      }
    })
  })
}

function send(client, type, data = {}) {
  client.ws.send(JSON.stringify({ type, data }))
}

async function waitFor(check, label, timeout = 8000) {
  const start = Date.now()
  while (Date.now() - start < timeout) {
    if (check()) {
      return
    }
    await sleep(50)
  }
  throw new Error(`等待超时: ${label}`)
}

function me(client) {
  if (!client.table) {
    return null
  }
  return client.table.players.find((player) => player.playerId === client.playerId) || null
}

async function main() {
  console.log(`连接 ${WS_URL}`)
  const clients = []
  for (const name of NAMES) {
    clients.push(await createClient(name))
  }
  clients.forEach((client) => send(client, 'HELLO', { nickname: client.name }))
  await waitFor(() => clients.every((client) => client.playerId), 'HELLO_ACK')
  console.log('4 位玩家已登录')

  send(clients[0], 'ROOM_CREATE', { maxRounds: 8 })
  await waitFor(() => clients[0].table, '创建房间')
  const roomId = clients[0].table.roomId
  console.log(`房间创建成功: ${roomId}`)

  for (let i = 1; i < 4; i++) {
    send(clients[i], 'ROOM_JOIN', { roomId })
  }
  await waitFor(() => clients.every((client) => client.table?.roomId === roomId), '加入房间')
  console.log('4 人已入座')

  clients.forEach((client) => send(client, 'ROOM_READY', { ready: true }))
  await waitFor(
    () => clients.every((client) => me(client)?.ready),
    '全部准备'
  )
  console.log('全部准备完毕')

  send(clients[0], 'GAME_START')
  await waitFor(() => clients.every((client) => client.table?.status === 'PLAYING'), '开始对局')
  console.log('对局开始，癞子: ' + clients[0].table.wildcardLabel)

  let loops = 0
  const deadline = Date.now() + 90000
  while (loops < 2000 && Date.now() < deadline) {
    loops += 1
    for (const client of clients) {
      const table = client.table
      const self = me(client)
      if (!table || !self || table.phase === 'ENDED' || table.status === 'FINISHED') {
        continue
      }
      const signature = [table.phase, table.currentSeat, self.hand.join(''), self.actions.join('')].join('|')
      if (signature === client.lastSig) {
        continue
      }

      if (table.phase === 'PLAYER_TURN' && table.currentSeat === self.seat) {
        client.lastSig = signature
        if (self.actions.includes('HU')) {
          send(client, 'GAME_ACTION', { action: 'HU' })
          continue
        }
        const tile = self.hand[Math.floor(Math.random() * self.hand.length)]
        send(client, 'GAME_ACTION', { action: 'DISCARD', tile })
      } else if (table.phase === 'WAITING_CLAIM' && self.actions.length > 0) {
        client.lastSig = signature
        if (self.actions.includes('HU')) {
          send(client, 'GAME_ACTION', { action: 'HU' })
        } else if (self.actions.includes('PONG') && Math.random() < 0.35) {
          send(client, 'GAME_ACTION', { action: 'PONG' })
        } else {
          send(client, 'GAME_ACTION', { action: 'PASS' })
        }
      }
    }

    if (clients.every((client) => client.table?.phase === 'ENDED')) {
      break
    }
    await sleep(100)
  }

  const finished = clients[0].table
  console.log(`\n对局阶段: ${finished.phase}，剩余牌: ${finished.remainingTiles}`)
  if (finished.result) {
    const result = finished.result
    console.log(
      result.drawGame
        ? '结果: 荒庄'
        : `结果: 座位 ${result.winnerSeat} 胡牌，${result.fan} 番，${result.flavors.join(' / ')}`
    )
  } else {
    console.log('未产生结果，请检查对局循环')
    console.log(
      '诊断:',
      JSON.stringify(
        {
          phase: finished.phase,
          currentSeat: finished.currentSeat,
          players: clients.map((client) => {
            const self = me(client)
            return {
              name: client.name,
              seat: self?.seat,
              handCount: self?.handCount,
              actions: self?.actions,
              synced: client.table?.serverTime ? 'yes' : 'no'
            }
          })
        },
        null,
        2
      )
    )
  }

  const errors = clients.flatMap((client) => client.errors.map((error) => `${client.name}: ${error.message}`))
  console.log(`错误消息: ${errors.length === 0 ? '无' : errors.slice(0, 5).join('; ')}`)

  clients.forEach((client) => client.ws.close())
}

main().catch((error) => {
  console.error('冒烟测试失败:', error.message)
  process.exit(1)
})
