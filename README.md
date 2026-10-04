# PadLink

把 Android 手机变成 PC 的虚拟手柄：手机端渲染并采集触摸输入，经 WiFi / ADB 送到 PC，PC 端模拟出系统认得的 Xbox 360 / DS4 手柄。

- **PC 端**：C# / .NET 10，仅 Windows
- **移动端**：Kotlin + Android Studio，Material 3（手柄本体自绘 Canvas）
- **虚拟手柄后端**：ViGEmBus 起步，`IVirtualControllerBackend` 抽象预留 VIIPER
- **传输**：WiFi (UDP) / ADB (TCP + `adb reverse`)
- **状态**：P0「定契约」完成；`adb reverse` 通道已实测可用；下一步 P2（**ADB 优先于 WiFi**）

## 目录

```
protocol/
  protocol.md              两端唯一契约（端口、握手、数据帧、时序、容错）
  testvectors/frames.json  协议测试向量，由 tools/gen_testvectors.py 生成，两端共用
tools/
  gen_testvectors.py       向量生成器，同时是协议的 Python 参考实现
  fake_pad.py              "假手机"：按协议发包，没有 Android 端也能验证 PC 端
src/pc/                    C# / .NET 10 解决方案
  PadLink.Core/              协议编解码、输入模型、后端抽象、fail-safe
  PadLink.Backends.ViGEm/    ViGEmBus 后端（Xbox 360 / DS4 真实虚拟手柄）
  PadLink.Server/            UDP 接收 + 会话管理 + PadLinkHost（CLI）
  PadLink.Gui/               WinForms 管理界面（启动/停止、连接情况、日志）
  PadLink.Core.Tests/        41 个测试，含协议向量一致性
src/android/               Kotlin / Gradle 工程
  core/                      纯 JVM 模块：协议实现 + 11 个向量测试
  app/                       Android 应用（Compose，当前是自绘摇杆骨架）
docs/                      企划审查 / 技术路线 / 路线图
research/                  参考项目源码（已被 .gitignore 忽略）
```

## 怎么跑

### PC 端

```powershell
cd src\pc
dotnet test                                                  # 41 个协议测试
dotnet run --project PadLink.Gui                             # 图形界面（推荐：启动/停止 + 连接情况 + 日志）
dotnet run --project PadLink.Server                          # 命令行起服务（默认 vigem 后端，会创建真手柄）
dotnet run --project PadLink.Server -- --backend console     # 只在控制台打印，不碰驱动
dotnet run --project PadLink.Server -- --adb                 # 起服务并自动建 adb reverse 隧道（ADB 模式）
dotnet run --project PadLink.Server -- --verify              # 自检：建手柄 → XInput 回读比对（12 项）
dotnet run --project PadLink.Server -- --probe               # 只读 XInput，看系统里有没有输入
```

另开一个终端：

```powershell
python tools/fake_pad.py                 # 跑 5 秒演示序列（UDP / WiFi 模式）
python tools/fake_pad.py --transport tcp # 走 TCP（ADB 模式，会先握手再发帧）
python tools/fake_pad.py --mode discover # 广播探测 PC
python tools/fake_pad.py --mode hold     # 持续按住 A（Ctrl+C 退出，可观察 fail-safe）
```

服务端会打印解出的按键 / 摇杆 / 扳机 / 十字键，停发 300ms 后自动归零。
**注意**：当前后端是 `ConsoleBackend`，只在控制台打印——要让游戏真的收到输入，还需要接入 ViGEmBus 后端（P1 未完成项）。

### 协议改动流程

`protocol/protocol.md` 是唯一契约。改协议必须走完这三步：

```powershell
python tools/gen_testvectors.py              # 重新生成向量
cd src\pc;      dotnet test                  # C# 端必须绿
cd src\android; .\gradlew.bat :core:test     # Kotlin 端必须绿
```

Android 端构建需要 **JDK 17**（JDK 25 会破坏 Kotlin 编译器）；SDK 路径写在 `src/android/local.properties`（不入库）。

## 三份必读文档

1. `docs/01-企划审查.md` — 原始企划的事实核查（ViGEmBus 已停更、ADB 路径 B 不成立、许可约束）
2. `docs/02-技术路线.md` — 架构、协议、后端选型的完整理由
3. `docs/03-路线图与待决策.md` — 决策记录 + P0–P5 分期与验收标准

## 四条核心结论

1. 原企划的技术基石 **ViGEmBus 已于 2023-11-02 归档停更**（商标纠纷），冻结在 v1.22.0。仍在工作；已决定先用它起步、在抽象层预留 VIIPER。
2. **ADB 是这个项目的立项理由**（"屋里网不好，想用 ADB 但没有"，而现有软件 EMotion 只有 WiFi），且 `adb forward/reverse` **只转发 TCP、不支持 UDP** —— 所以 ADB 模式的数据通道必须是 TCP。隧道已实测可用（`tools/adb_tcp_probe.py`）。
3. 原企划的 **"ADB 路径 B"（手机连自己的 adbd + `input` 命令）在本项目里不成立**——`input` 造不出摇杆轴和扳机。ADB 的正确定位是"手机 ↔ PC 的 USB 数据传输通道"，方向是 `adb reverse`。
4. **VIIPER、Joy2DroidX、Controlloid 都是 GPL-3.0**。接入 VIIPER 必须走"独立进程 + TCP API"，不能链接它的库，否则 PadLink 也得 GPL。
