# 南陵麻将 · 四人连线

> 微信小程序（Taro + React）+ Java 后端的四人实时麻将对局。
> 当前分支：`feature-1.0.0`，已完成可跑通的对局框架，**暂未接入数据库**。

## 当前进度

| 能力 | 状态 |
| --- | --- |
| 牌库 136 张、洗牌、发牌、翻宝定癞子 | ✅ |
| 摸牌、出牌、碰、明杠 / 暗杠 / 补杠 | ✅ |
| 胡牌判定（4 面子 + 1 将 / 缺一门 / 9支·11支·混一色·跑风） | ✅ |
| 算番与结算（自摸 / 点炮） | ✅ |
| 四人房间：建房、加入、准备、开局、下一局 | ✅ |
| WebSocket 实时同步 + 断线重连 + 超时自动出牌 | ✅ |
| 小程序牌桌 UI（牌面、副露、弃牌、倒计时、结算弹窗） | ✅ |
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

用微信开发者工具「导入项目」，选择 **miniprogram 目录**（`project.config.json` 里 `miniprogramRoot` 已指向 `dist/`）。

要点：

- 开发者工具需勾选「详情 → 本地设置 → 不校验合法域名、web-view、TLS 版本以及 HTTPS 证书」。
- 真机预览时，把 `src/config/env.ts` 里的 `127.0.0.1` 改成电脑局域网 IP。
- 上线前把 `project.config.json` 的 `appid` 换成自己的小程序 AppID。

### 3. 四人联机冒烟（无需微信开发者工具）

```bash
# 后端已启动的前提下
cd server
node scripts/ws-smoke.mjs
# 指定地址：WS_URL=ws://192.168.1.10:8080/ws/game node scripts/ws-smoke.mjs
```

脚本会模拟 4 个玩家建房、准备、开局，并自动摸打到本局结束，输出番型与错误信息。

## 规则速览

胡牌按固定顺序判定三层，全过才算胡：

1. **基础牌型**：14 张 = 4 副面子 + 1 对将牌（结构不成，其余免谈）。
2. **缺一门**：万 / 筒 / 条必须少一门，字牌不计入花色。
3. **调味剂**：手牌含癞子时，必须命中 `9支` / `11支` / `混一色` / `跑风` 之一；
   无癞子手牌可裸胡。

易错点：

- 癞子把某一门凑到**刚好 9 张不算 9支**，必须「实牌 + 癞子」超过 9 张。
- 癞子**只有充当将头对子**才算跑风，作顺子或刻子不算。

完整规则与用例见 [docs/01-南陵麻将规则.md](docs/01-南陵麻将规则.md)。

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
