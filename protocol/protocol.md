# PadLink 协议 v1

两端唯一契约。**任何一端改动此文件，必须同时更新 `testvectors/` 与另一端的编解码测试。**

设计要点（与参考实现的差异）：

- Joy2DroidX 用 Socket.IO + 事件式增量（`{key, value}`），走 TCP/WebSocket，存在队头阻塞与重传抖动。**PadLink 用 UDP + 固定节拍的全量状态快照**——丢包无害，这是能上 UDP 的前提。
- 协议里的一切输入都用**物理语义**（向上为正、向右为正），**与具体后端（XInput / DS4）解耦**；Y 轴取反等硬件怪癖由 PC 端 Mapper 负责，不污染协议。

---

## 1. 通道与端口

两种传输模式共用同一套帧格式，区别只在传输层：

| 模式 | 建立方式 | 数据 | 控制 | 发现 |
|---|---|---|---|---|
| **WiFi** | 手机单播到 PC 的 IP | **UDP**（丢包无害） | TCP | UDP 广播 |
| **ADB** | `adb reverse` + 手机连 `127.0.0.1` | **TCP**（只能 TCP，见 §1.1） | 同一条 TCP | 无 |

### WiFi 模式

| 通道 | 传输 | 端口 | 方向 | 内容 |
|---|---|---|---|---|
| 发现 | UDP | 42313 | 手机 → 广播，PC → 单播 | `PADLINK?1` 探测 / PC 应答 |
| 数据 | UDP | 42313 | 手机 → PC 单播 | 输入帧（§5），固定 28 字节 |
| 反向 | UDP | 手机数据帧的源端口 | PC → 手机 | 震动帧（§6），固定 8 字节 |
| 控制 | TCP | 42312 | 双向 | 握手、能力协商、设备生命周期（§4，JSON Lines） |

- **发现与数据共用 42313**，PC 端单个 socket 处理，用前两个字节区分：文本 `PA`（发现）vs 二进制 `0x50 0x4C`（数据）。这样 Windows 防火墙只需放行一个 UDP 端口。
- 端口全部可通过 PC 端配置覆盖；实际使用值以 §4 握手的 `welcome` 为准。
- 手机端数据 socket 不主动关闭——**源端口必须稳定**，PC 用它（IP + 源端口）识别会话。

### 1.1 ADB 模式（USB 线）

**硬约束：`adb forward` / `adb reverse` 只转发 TCP，不支持 UDP。**
所以 ADB 模式不能沿用 WiFi 的 UDP 数据通道，必须走 TCP —— 这不是取舍，是 adb 的能力边界。

连接建立：

```
PC:   adb reverse tcp:42312 tcp:42312    # 语义：设备上的端口 → 主机上的端口
手机: TcpClient.Connect("127.0.0.1", 42312)
```

即 **PC 端做 TCP server（监听 42312），手机端做 TCP client（连自己的 `127.0.0.1`）**。方向别写反：是 `reverse`，不是 `forward`。

- 手机端不需要知道 PC 的 IP，也不需要监听端口；连 `127.0.0.1` 在 Android 上不需要任何额外权限。
- **会话识别**：一条 TCP 连接 = 一个会话，不再用 IP:端口做键。
- **不做 UDP 发现**；由 PC 端负责执行 `adb reverse`（需要 PC 上有可用的 `adb`）。
- 支持无线调试（Android 11+）：先 `adb pair`，再 `adb connect`，之后同样 `adb reverse`。

#### 帧切分（单连接多路复用）

一条 TCP 连接上混跑输入帧和控制消息，按**首字节**区分：

| 首字节 | 类型 | 解析 |
|---|---|---|
| `0x50 0x4C`（`PL`） | 二进制帧 | **长度由方向决定**：手机 → PC 是输入帧（28 字节）；PC → 手机是震动帧（8 字节） |
| `0x7B`（`{`） | 控制消息 | 读到 `\n` 为止的一行 JSON |

- TCP 是字节流，**必须缓冲并按上述规则切分**，不能假设一次 `read` 就是一条完整消息。
- 输入帧与震动帧的 magic 相同、第 4 字节又可能撞车（输入帧的 `attr` 低两位是 `player`，震动帧是 `type`），**靠方向区分是无歧义的**——每个方向上 magic 的含义唯一。
- 数据帧在 TCP 上**同样按固定节拍（60/120Hz）发送**，与 UDP 一致。TCP 不丢包，但仍要保活与 fail-safe。
- 必须设 `TCP_NODELAY`（见 §7）。

> **实测记录（2026-10-04）**：`adb reverse tcp:42312 tcp:42312` + 手机 `nc 127.0.0.1 42312` 已验证隧道成立（PC 收到 4 字节 `PING`）。`tools/adb_tcp_probe.py` 可复现。

## 2. 字节序与编码约定

- 一切多字节整数为**小端**（little-endian），与 Android `ByteBuffer` 默认序和 x86 一致。
- 浮点不进协议：轴一律用 `int16`，扳机用 `uint16`（0..32767），避免两端浮点格式化差异。

## 3. 发现

手机 → 广播 `255.255.255.255:42313`：

```
PADLINK?1
```

PC → 单播回手机源端口：

```json
{"type":"announce","v":1,"name":"DESKTOP-ABC","tcp_port":42312,"udp_port":42313,"auth":"none","players_max":4}
```

- `auth`: `none` | `pin`（见 §4.3）。
- 手机拿不到应答时，允许用户手输 IP（兜底路径，必须实现——某些 WiFi 会吞广播）。

## 4. 控制通道（TCP 42312，JSON Lines）

每行一个 JSON 对象，`\n` 结尾。UTF-8。连接建立后必须先 `hello`。

### 4.1 握手

```
→ {"v":1,"type":"hello","device":"Pixel 7","codecs":["binary","json"],"controller":"xbox360","players":1}
← {"v":1,"type":"welcome","session":17388001,"udp_port":42313,"codec":"binary","rate":60,"player":0,"controller":"xbox360"}
→ {"v":1,"type":"start"}
← {"v":1,"type":"started","player":0}
```

字段说明：

- `codecs`：手机支持的数据帧编码，PC 选一个回填 `codec`。`binary` 优先；`json` 仅用于调试。
- `rate`：**PC 指定的数据帧发送节拍（Hz）**，取值 60 或 120。手机必须遵守。
- `controller`：`xbox360` | `ds4`。PC 可以拒绝并回 `error`（例如目标后端不支持该类型）。
- `players`：请求的手柄数量（1..4）。PC 在 `welcome` 里回实际分配。

### 4.2 会话生命周期

```
← {"v":1,"type":"rumble_enable","enabled":true}     # 可选，PC 支持震动时
→ {"v":1,"type":"bye"}                              # 正常退出
```

PC 侧一旦收到数据帧中断超过 §7 的超时，**只归零输入、不销毁虚拟手柄**（销毁会让游戏重排 XInput 槽位，导致其他玩家的编号跳动）。

### 4.3 认证（可选，v0.1 可关）

`announce`/`welcome` 中 `auth":"pin"` 时：

```
← {"v":1,"type":"auth","challenge":"a3f9"}
→ {"v":1,"type":"auth","pin":"1234"}
← {"v":1,"type":"welcome",...}      # 通过
← {"v":1,"type":"error","code":"auth_failed"}
```

约束：`hello` 之后 5 秒内未通过认证即断开；同一 IP 连续 5 次失败后冷却 60 秒。

### 4.4 错误

```json
{"v":1,"type":"error","code":"controller_unsupported","message":"backend has no ds4"}
```

`code` 取值：`bad_request` / `auth_failed` / `controller_unsupported` / `no_slot` / `busy` / `internal`。

## 5. 数据帧（UDP，固定 28 字节）

| offset | 字节 | 类型 | 字段 | 说明 |
|---|---|---|---|---|
| 0 | 2 | — | `magic` | `0x50 0x4C`（`'P' 'L'`） |
| 2 | 1 | uint8 | `ver` | 1 |
| 3 | 1 | bits | `attr` | bit0-1 `player`(0-3)；bit2-4 `controller`(0=xbox360,1=ds4,2=dualsense 预留,3=switchpro 预留,4-7 保留)；bit5 `rumble_ack`；bit6-7 保留（必须 0） |
| 4 | 4 | uint32 | `seq` | 每帧 +1，自然回绕；用于丢包率与乱序统计 |
| 8 | 4 | uint32 | `ts` | 发送时刻（Android `SystemClock.uptimeMillis()` 低 32 位）；仅用于抖动测量，跨设备无绝对意义 |
| 12 | 2 | bitmask | `buttons` | 见 §5.2 |
| 14 | 1 | uint8 | `dpad` | 见 §5.3 |
| 15 | 1 | uint8 | `reserved` | 必须为 0 |
| 16 | 2 | int16 | `lx` | 左摇杆 X，右为正，-32767..32767 |
| 18 | 2 | int16 | `ly` | 左摇杆 Y，**上为正** |
| 20 | 2 | int16 | `rx` | 右摇杆 X，右为正 |
| 22 | 2 | int16 | `ry` | 右摇杆 Y，**上为正** |
| 24 | 2 | uint16 | `lt` | 左扳机，0..32767 表示 0.0..1.0 |
| 26 | 2 | uint16 | `rt` | 右扳机，0..32767 |

接收端必须校验：长度 == 28、magic 正确、`ver` 已知、`reserved` == 0。不合规静默丢弃（不回复、不断连）。

### 5.1 JSON 编码（仅调试）

同一帧的 JSON 形式，字段名与二进制一致，`attr` 拆开写：

```json
{"v":1,"player":0,"controller":"xbox360","seq":1,"ts":1000,
 "buttons":1,"dpad":0,"lx":16384,"ly":16384,"rx":0,"ry":0,"lt":0,"rt":0}
```

### 5.2 `buttons` 位掩码（16 位，位置固定）

| bit | 含义 | Xbox 360 | DS4 |
|---|---|---|---|
| 0 | A / ✕ | A | Cross |
| 1 | B / ○ | B | Circle |
| 2 | X / □ | X | Square |
| 3 | Y / △ | Y | Triangle |
| 4 | LB / L1 | LeftShoulder | ShoulderLeft |
| 5 | RB / R1 | RightShoulder | ShoulderRight |
| 6 | Back / Share | Back | Share |
| 7 | Start / Options | Start | Options |
| 8 | L3 | LeftThumb | ThumbLeft |
| 9 | R3 | RightThumb | ThumbRight |
| 10 | Guide / PS | Guide | PS（特殊位，不在 wButtons） |
| 11 | Touchpad 按下 | 忽略 | 支持 |
| 12-15 | 保留：发送端置 0，接收端忽略（便于扩展） | — | — |

**位置是"物理位置"语义**：`bit0` 永远是手柄**下方**那颗键，UI 上叫 A 还是 ✕ 由布局模板决定。这样切换手柄类型只换 UI 标签，不动协议。

> 参考：Joy2DroidX 用字符串 key（`"a-button"`）逐条发送。位掩码省字节、比较快，且两端语义单一，避免字符串拼写错误。

### 5.3 `dpad` 枚举

| 值 | 方向 | 值 | 方向 |
|---|---|---|---|
| 0 | 中立（松开） | 5 | 南 |
| 1 | 北 | 6 | 西南 |
| 2 | 东北 | 7 | 西 |
| 3 | 东 | 8 | 西北 |
| 4 | 东南 | | |

与 DS4 驱动枚举差一个偏移，映射在 Mapper 内处理：`ds4 = (dpad == 0) ? 8 : dpad - 1`。

### 5.4 轴语义与后端映射

协议一律"向上/向右为正"。各后端的差异**只在 Mapper 里**：

| 后端 | lx/rx | ly/ry | 扳机 |
|---|---|---|---|
| XInput (Xbox 360) | 直接用（int16 同域） | **也直接用** | 0..32767 线性缩放到 0..255 |
| DS4 (ViGEm) | `(v + 1) * 127.5` → 0..255 | **先取反再映射**（HID 惯例 Y 向下为正），⚠️ 待真机校准 | 同左，映射到 `bTriggerL/R` |

> **XInput 的 Y 轴不需要取反 —— 已实测（2026-10-04，`dotnet run -- --verify`）**：
> 协议 `LeftY = +1`（上推）→ XInput `sThumbLY = +32767`；`LeftY = -1` → `-32767`。两者同向。
>
> ⚠️ 别被参考实现误导：Joy2DroidX 对 XInput 写了 `-round(value * XUSB_THUMB_MAX)`，
> 那是**因为它的客户端发的是屏幕坐标（y 向下为正）**，不是 XInput 本身要求取反。
> 我们的协议统一用"上为正"，所以 XInput 直接传。

DS4 的 Y 轴符号尚未在真机校准（DS4 不走 XInput，没法用上面的回读手段验证），
映射代码里已标注，等接真机时用摇杆测试页确认一次。

## 6. 反向通道（震动）

PC → 手机，发到**手机数据帧的源 IP:源端口**。固定 8 字节：

| offset | 字节 | 字段 |
|---|---|---|
| 0 | 2 | `magic` `0x50 0x4C` |
| 2 | 1 | `ver` = 1 |
| 3 | 1 | `type` = `0x01`（rumble），0x02-0xFF 预留 |
| 4 | 1 | `player` |
| 5 | 1 | `left` 强度 0..255 |
| 6 | 1 | `right` 强度 0..255 |
| 7 | 1 | 保留，0 |

不重传、不确认；丢一帧下一帧覆盖。手机端在 §7 超时内没收到任何反向帧**不影响输入**（震动是可选增强）。

## 7. 时序与容错（实现必须遵守）

| 规则 | 值 | 理由 |
|---|---|---|
| 数据帧发送节拍 | **固定 60Hz 或 120Hz**（由 `welcome.rate` 决定） | 触摸事件不得直接触发发包；固定节拍才能保证抖动可预测 |
| 无输入变化时 | 降到 **10Hz** 继续发（`seq` 仍递增） | 保活 + 让 PC 端能区分"静默"与"断线" |
| PC 端 fail-safe | **300ms** 未收到合规数据帧 → 立即归零所有轴、清空 `buttons`/`dpad` | 否则 WiFi 一抖角色就一直往前跑 |
| 手机端 fail-safe | 1s 内既无 TCP 心跳也无反向帧 → UI 提示断开并清空本地按键状态 | 避免重连后残留按下状态 |
| TCP（控制通道） | 必须 `TCP_NODELAY`；3s 无任何流量则发 `{"type":"ping"}` | 关 Nagle，否则小包被攒 40ms |
| 数据帧每包 | 必须**全量**，不得做增量 | 丢包无害的前提 |
| 设备生命周期 | 超时只归零，**不销毁虚拟手柄** | 避免游戏重排槽位 |

## 8. 版本演进

- `ver` 在数据帧和控制通道里各有一份，**大版本必须相等**才能通信。
- 新增字段：优先放入 §5 的 `reserved` 或 `attr` 保留位；仍不够才升 `ver`。
- 未知 `ver`：PC 回 `{"type":"error","code":"bad_request"}` 并断开；手机显示"服务端太旧/太新"。

## 9. 测试向量

见 `testvectors/frames.json`。两端必须各自实现"编码 → 与向量比对"和"解码向量 → 结构比对"两组测试，**在写任何 UI 之前跑通**。
