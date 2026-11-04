#!/usr/bin/env node
/**
 * H5 单端口网关：静态文件 + WebSocket/API 反向代理。
 *
 * <p>为什么需要它：H5 产物在 https 下会把 WebSocket 地址推导成同域 `/ws/game`
 * （见 miniprogram/src/config/env.ts）。而 `python -m http.server` 只能发静态文件，
 * 没有升级 WebSocket 的能力。内网穿透通常只给一个公网端口，
 * 所以必须有一个进程同时扛住「静态资源」和「WS 升级」。</p>
 *
 * <p>用法（在仓库根目录）：node server/scripts/serve-h5.mjs --port 5173</p>
 */

import { createServer, request as httpRequest } from 'node:http'
import { createReadStream, existsSync, statSync } from 'node:fs'
import { connect } from 'node:net'
import { dirname, extname, join, normalize, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createGzip } from 'node:zlib'

const args = process.argv.slice(2)
const readArg = (name, fallback) => {
  const index = args.indexOf(`--${name}`)
  return index >= 0 && args[index + 1] ? args[index + 1] : fallback
}

const HERE = dirname(fileURLToPath(import.meta.url))
const PORT = Number(readArg('port', 5173))
const BACKEND = new URL(readArg('backend', 'http://127.0.0.1:8080'))
const ROOT = resolve(readArg('root', join(HERE, '../../miniprogram/dist/h5')))
const BACKEND_PORT = Number(BACKEND.port || 80)

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.map': 'application/json; charset=utf-8'
}

/** 值得压缩的文本类型；gzip 后 app.js 从 226KB 降到 ~65KB，穿透下首屏快 3 倍 */
const COMPRESSIBLE = new Set(['.html', '.js', '.css', '.json', '.svg', '.map', '.txt'])
/** 小于这个体积就不折腾了，压缩反而更慢 */
const GZIP_MIN_BYTES = 1024

function serveFile(req, res, filePath) {
  const ext = extname(filePath).toLowerCase()
  const type = MIME[ext] ?? 'application/octet-stream'
  const size = statSync(filePath).size
  const acceptsGzip = /\bgzip\b/.test(req.headers['accept-encoding'] ?? '')
  const useGzip = acceptsGzip && COMPRESSIBLE.has(ext) && size >= GZIP_MIN_BYTES

  const headers = {
    'Content-Type': type,
    'Cache-Control': 'no-cache',
    'Access-Control-Allow-Origin': '*',
    Vary: 'Accept-Encoding'
  }
  if (useGzip) {
    headers['Content-Encoding'] = 'gzip'
  } else {
    headers['Content-Length'] = size
  }
  res.writeHead(200, headers)

  const stream = createReadStream(filePath)
  if (!useGzip) {
    stream.pipe(res)
    return
  }
  const gzip = createGzip({ level: 6 })
  stream.pipe(gzip).pipe(res)
  stream.on('error', () => gzip.destroy())
}

const server = createServer((req, res) => {
  const url = new URL(req.url, `http://${req.headers.host ?? 'localhost'}`)

  // 后端只读接口走代理，方便线上排查
  if (url.pathname.startsWith('/api/')) {
    const upstream = httpRequest(
      {
        host: BACKEND.hostname,
        port: BACKEND_PORT,
        path: url.pathname + url.search,
        method: req.method,
        headers: { ...req.headers, host: BACKEND.host }
      },
      (upstreamRes) => {
        res.writeHead(upstreamRes.statusCode ?? 502, upstreamRes.headers)
        upstreamRes.pipe(res)
      }
    )
    upstream.on('error', () => {
      if (!res.headersSent) {
        res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' })
      }
      res.end('backend unavailable')
    })
    req.pipe(upstream)
    return
  }

  let pathname = decodeURIComponent(url.pathname)
  if (pathname === '/' || pathname === '') {
    pathname = '/index.html'
  }
  // 防目录穿越
  const filePath = join(ROOT, normalize(pathname).replace(/^(\.\.[/\\])+/, ''))
  if (filePath.startsWith(ROOT) && existsSync(filePath) && statSync(filePath).isFile()) {
    serveFile(req, res, filePath)
    return
  }
  // H5 用 hash 路由：无扩展名的路径回 index.html；带扩展名的（js/css/图）确实不存在就 404，
  // 别把 index.html 当成 js 返回，否则浏览器只会报一句莫名其妙的语法错误
  if (extname(pathname)) {
    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' })
    res.end('404')
    return
  }
  const fallback = join(ROOT, 'index.html')
  if (existsSync(fallback)) {
    serveFile(req, res, fallback)
    return
  }
  res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' })
  res.end('404')
})

/** WebSocket 升级：把原始 TCP 连接直接对接给后端 */
server.on('upgrade', (req, socket, head) => {
  const url = new URL(req.url, `http://${req.headers.host ?? 'localhost'}`)
  if (!url.pathname.startsWith('/ws/')) {
    socket.destroy()
    return
  }
  const upstream = connect(BACKEND_PORT, BACKEND.hostname, () => {
    const lines = [`GET ${url.pathname}${url.search} HTTP/1.1`]
    for (let i = 0; i < req.rawHeaders.length; i += 2) {
      lines.push(`${req.rawHeaders[i]}: ${req.rawHeaders[i + 1]}`)
    }
    upstream.write(`${lines.join('\r\n')}\r\n\r\n`)
    if (head && head.length) {
      upstream.write(head)
    }
    upstream.pipe(socket)
    socket.pipe(upstream)
  })

  upstream.on('error', () => socket.destroy())
  socket.on('error', () => upstream.destroy())
})

server.listen(PORT, '0.0.0.0', () => {
  console.log(`H5 网关已启动: http://127.0.0.1:${PORT}`)
  console.log(`  静态目录: ${ROOT}`)
  console.log(`  后端代理: ${BACKEND.origin}  (/api/** 与 /ws/**)`)
})
