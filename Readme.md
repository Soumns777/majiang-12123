# 南陵麻将 · 四人连线

> 微信小程序（Taro + React）+ Java 后端的四人实时麻将对局。
> 当前分支：`feature-1.0.0`，已完成可跑通的对局框架，**暂未接入数据库**。

## 当前进度

| 能力 | 状态 |
| --- | --- |
| 牌库 136 张、洗牌、发牌、翻宝定癞子 | ✅ |
| 摸牌、出牌、碰、明杠 / 暗杠 / 补杠 | ✅ |
| 胡牌判定（4 面子 + 1 将 / 缺一门 / 9支·11支·混一色·跑风） | ✅ |
| 算分与结算（按 PDF 的加法牌型分 × 底分，只能自摸、三家付） | ✅ |
| 欢乐豆：开局每人 20 豆，底分 1~10 可设，头家 ±1 | ✅ |
| 四人房间：建房、加入、准备、开局、下一局 | ✅ |
| 满员观战：用房间号进来只看不打，四家出牌可见、手牌全隐藏 | ✅ |
| 自制规则「打出癞子，全场作废」，房间内可开关 | ✅ |
| 机器人代打：房主一键补满空位，服务端逐个出牌（一张约 1.2s，不会一瞬间打完） | ✅ |
| 延时申请：冻结剩余时间 → 三家投票 → 全同意延长一个基础时长 | ✅ |
| WebSocket 实时同步 + 断线重连 + 超时自动出牌 | ✅ |
| 小程序牌桌 UI（真实牌面图形、副露、弃牌、倒计时、结算弹窗） | ✅ |
| 内网穿透试玩：单端口网关（静态页 + /api + /ws 同源），可挂任意隧道 | ✅ |
| 数据库持久化 | ⏳ 等服务器到位 |
| 微信登录、战绩、回放、AI | ⏳ 后续版本 |

## 技术栈

- 小程序：Taro 4.0.9 + React 18 + TypeScript + Zustand + SCSS
- 后端：Java 17 + Spring Boot 3.2.5 + 原生 WebSocket + Maven
- 存储：内存（`RoomRepository` 抽象，随时可换 JPA/MyBatis）

## 目录结构

```
.
├── miniprogram/        # 小程序（Taro + React）
├── server/             # 后端（Spring Boot）
│   ├── scripts/        # 四人联机冒烟脚本
│   └── src/main/java/com/nanling/mahjong/
├── docs/
│   ├── 01-南陵麻将规则.md
│   ├── 02-架构设计.md
│   └── 03-通信协议.md
└── Readme.md
```

## 快速开始

### 1. 启动后端

需要 JDK 17+ 与 Maven 3.9+。

```bash
cd server
mvn test            # 16 个用例：10 个胡牌规则 + 6 个对局状态机
mvn spring-boot:run # 默认 http://localhost:8080
```

健康检查：`curl http://localhost:8080/api/health`

### 2. 启动小程序

```bash
cd miniprogram
npm install
npm run dev:weapp
```

用微信开发者工具「导入项目」，选择 **miniprogram 目录**（`project.config.json` 里 `miniprogramRoot` 已指向 `dist/weapp/`）。

要点：

- 开发者工具需勾选「详情 → 本地设置 → 不校验合法域名、web-view、TLS 版本以及 HTTPS 证书」。
- 真机预览时，把 `src/config/env.ts` 里的 `127.0.0.1` 改成电脑局域网 IP。
- 上线前把 `project.config.json` 的 `appid` 换成自己的小程序 AppID。

### 3. 打包 H5（浏览器版）

同一套代码可以直接编译成网页版：

```bash
cd miniprogram
npm run build:h5          # 产物：dist/h5
python -m http.server 5173 -d dist/h5
# 浏览器打开 http://localhost:5173
```

开发时也可以热更新：`npm run dev:h5`（默认 10086 端口）。

说明：

- 小程序产物在 `dist/weapp`，H5 在 `dist/h5`，两者互不覆盖。
- H5 的 WebSocket 地址自动推导：本地开发连「当前主机的 8080」；https 部署时连「同域 `/ws/game`」，由 Nginx 反代到后端。
- 必须用 HTTP 服务打开，直接双击 `index.html`（`file://`）会因绝对路径加载不到资源。
- 微信登录、分享、订阅消息等 JSAPI 在 H5 不可用；当前版本没有用到这些能力，功能与小程序一致。
- 上线时后端需要允许跨域（`WebMvcConfig` 已放开 `/api/**`，WS 用 `setAllowedOriginPatterns("*")`，生产建议收窄到自己的域名）。

#### H5 尺寸适配（重要）

Taro 编译 H5 时会把 `rpx` 转成 `rem`（`1rem = 40rpx`），但**运行期不会设置根字号**，浏览器默认的 16px 会让 `750rpx` 只渲染成 300px，整页缩水约 23%。
因此 `src/index.html` 里显式把根字号绑到视口宽度：

```css
html { font-size: calc(100vw / 18.75); }        /* 1rem = 40rpx = 视口宽/750*40 */
@media (min-width: 640px) { html { font-size: calc(640px / 18.75); } }  /* 宽屏封顶 */
```

这样 `750rpx` 恰好铺满一屏，与小程序的 `rpx` 表现一致；宽屏（桌面 / 平板横屏）封顶在 640px，不会等比放大到失真。
修改 `designWidth` 或 `deviceRatio` 时，`18.75` 这个除数需要同步调整（`designWidth / 40`）。

### 4. 四人联机冒烟（无需微信开发者工具）

```bash
# 后端已启动的前提下
cd server
node scripts/ws-smoke.mjs
# 指定地址：WS_URL=ws://192.168.1.10:8080/ws/game node scripts/ws-smoke.mjs
```

脚本会模拟 4 个玩家建房、准备、开局，并自动摸打到本局结束，输出番型与错误信息。

### 5. 用手机 / 给朋友试玩（单端口网关 + 内网穿透）

H5 里的 WS 地址是**同源推导**的（`wss://<当前域名>/ws/game`），所以静态页和 WebSocket 必须在同一个端口上。
`server/scripts/serve-h5.mjs` 就是干这个的：一个端口同时提供静态文件、`/api/**` 反向代理和 `/ws/**` 升级转发。

```bash
node server/scripts/serve-h5.mjs --port 5173 --backend http://127.0.0.1:8080

# 然后把任意隧道指向 5173 即可（免注册、免费）
ssh -R 80:127.0.0.1:5173 nokey@localhost.run      # 输出 https://xxxx.lhr.life
# 或：cloudflared tunnel --url http://127.0.0.1:5173
```

拿到公网地址后，手机或朋友直接打开即可：建房 → 补满机器人自己练手，或把房间号发给朋友一起打。
（`ngrok` 需要 authtoken，且部分网络环境连不上 `connect.ngrok-agent.com`，所以默认用 localhost.run / cloudflared。）

## 规则速览

胡牌按固定顺序判定三层，全过才算胡：

1. **基础牌型**：14 张 = 4 副面子 + 1 对将牌（结构不成，其余免谈）。
2. **缺一门**：万 / 筒 / 条必须少一门，字牌不计入花色。
3. **调味剂**：手牌含癞子时，必须命中 `9支` / `11支` / `混一色` / `跑风` 之一；
   无癞子手牌可裸胡。

易错点：

- 癞子把某一门凑到**刚好 9 张不算 9支**，必须「实牌 + 癞子」超过 9 张。
- 癞子**只有充当将头对子**才算跑风，作顺子或刻子不算。
- **只能自摸**：南陵麻将不允许胡别人打出的牌，没有点炮、没有一炮多响。

### 计分与欢乐豆

| 项目 | 分值 |
| --- | --- |
| 基础（开牌） | +1 |
| 9支 | 0（本身不计分，是「开牌 +1」的由来） |
| 11支 | +3 |
| 混一色 / 清一色 | +2 / +4（互斥） |
| 跑风 / 单钓 | +1 / +1 |
| 杠 / 杠开 | +1 每个 / +2 |

- 单家应付 = 牌型分 × **底分**（房主可设 1~10，默认 1）
- 每人开局 **20 豆**；自摸三家付：赢家 `+3 × 单家应付`，其余各 `-单家应付`
- 头家 ±1：头家胡多 +1，头家输多扣 1（PDF 原文「其他玩家不受影响」）
- 结算弹窗会逐项列出「胡了什么」：基础 / 混一色 / 11支 / 跑风 …，再给算式和四家增减

完整规则与用例见 [docs/01-南陵麻将规则.md](docs/01-南陵麻将规则.md)。

## 牌面实现

筒、条不再是「五筒 / 六条」这种文字，而是按市面常见牌面的点位画出来的图形：

- 牌面在 `components/Tile/pattern.tsx` 里按 **100 × 144 的虚拟画布**绝对定位，
  所有尺寸用百分比换算，因此同一套点位在 `md / sm / xs / hand` 四种牌尺寸下都成立。
- 用绝对定位的 `View` 而不是内联 `<svg>`：微信小程序的 WXML 渲染不了 `<svg>`，
  只有 H5 支持，用 div 才能保证两端表现一致。
- 筒子 = 深蓝圆环 + 红心（1 筒为大圆环）；条子 = 绿色竹节（1 条单独画幺鸡）；
  万子 = 中文数字 + 萬；白板留空只画内框。
- 四种牌尺寸的 **高宽比统一为 1.44**（`124/86`、`81/56`、`55/38`、`72/50`），
  和虚拟画布一致，否则圆点会被拉成椭圆。
- 癞子牌：牌面描金 + 右上角「癞」字角标；本局癞子作废后改为灰色划线。

## 数据库接入

当前所有房间状态在内存中，进程重启即丢失。持久化入口只有一个接口：

```java
public interface RoomRepository { ... }
```

服务器到位后：

1. 建表：`mj_user` / `mj_room` / `mj_room_player` / `mj_round`；
2. 新增 `JpaRoomRepository`，用 `@ConditionalOnProperty(mahjong.storage.mode=jdbc)` 切换；
3. 把 `PlayerProfile` 换成微信 `code2session` 登录；
4. 对局中的高频状态仍放内存，只在开局 / 结算 / 房间变更时落库。

详见 [docs/02-架构设计.md](docs/02-架构设计.md)。

## 合规说明

- 本项目仅作休闲娱乐，**不接入充值、提现、现金结算**，不支持任何形式的积分兑换。
- 上线前请遵守《微信小程序平台运营规范》与当地法律法规。
- 牌面、音效等素材需自行确认版权。

## 提交规范

`feat` 新功能 / `fix` 修复 / `refactor` 重构 / `docs` 文档 / `style` 样式 / `test` 测试 / `chore` 杂项

## License

MIT
