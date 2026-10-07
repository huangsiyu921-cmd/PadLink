# PadLink

把 Android 手机变成 PC 的虚拟手柄。手机端渲染手柄界面并采集触摸输入，经 **WiFi** 或 **USB（ADB）** 送到 PC，PC 端造出一个系统认得的真手柄（Xbox 360 / PlayStation 4）。

**动机**：家里的 WiFi 不稳，想走 USB 直连，但现成的同类软件基本只有 WiFi 模式。所以 **ADB（USB）是主线**，WiFi 是补充。

| 端 | 技术 |
| --- | --- |
| PC 端 | C# / .NET 10，WinForms，仅 Windows |
| 手机端 | Kotlin + Jetpack Compose（Material 3），手柄本体自绘 Canvas |
| 虚拟手柄 | ViGEmBus 驱动，可在 **Xbox 360 / PS4** 之间切换 |
| 传输 | WiFi = UDP 数据 + TCP 控制；ADB = TCP + `adb reverse` |

## 特性

- **ADB 模式全链路跑通**（真机验证过）：插上 USB → PC 自动 `adb reverse` → 手机自动连上 → PC 建出真手柄。
- **手柄类型可切换**：Xbox 360 / PS4（PC 端说了算，切换即重建虚拟手柄）。DS4 身份在 Steam 里认得出。
- **布局系统**：控件可拖动挪位、双指缩放、导出成文件、从文件导入；十字键有「8 方向 / 三角分键」两种样式，扳机有「滑动条 / 按钮」两种样式。
- **手机界面**：双摇杆 / 十字键 / ABXY / LT·RT / LB·RB / Back·Start / L3·R3 / Guide，Material 3 深色。
- **重力转向**：像赛车游戏那样左右倾斜手机来控制左摇杆。解算全在手机端做，发出去的仍是普通输入帧 —— 所以 Xbox 360 手柄也照样能用。
- **PC 界面**：深色 WinForms，托盘常驻，设置弹窗里启停服务、建 ADB 隧道、切换手柄类型；另有两个独立窗口：「日志」（程序运行日志）和「输出」（手柄输出：实时值 + 变化记录）。
- **两端协议互验**：共用 `protocol/testvectors/frames.json`，C# 与 Kotlin 各跑一遍。
- **fail-safe**：手机关掉或断线后 PC 端自动归零输入，不会卡住按键。

## 目录

```
protocol/               两端唯一契约 protocol.md + 测试向量
tools/                  向量生成器 / 假手机 / adb 隧道探针 / 卸载脚本 / 图标生成
src/pc/                 C# / .NET 10 解决方案
src/android/            Kotlin / Gradle 工程
docs/                   企划审查 / 技术路线 / 路线图 / 本地开发笔记
build.ps1               编译 PC 端并发布
```

`src/pc` 里的项目：

| 项目 | 作用 |
| --- | --- |
| `PadLink.Core` | 协议编解码、输入模型、后端抽象、fail-safe |
| `PadLink.Backends.ViGEm` | ViGEmBus 后端（Xbox 360 / DS4） |
| `PadLink.Server` | UDP/TCP 接收 + 会话管理 + `PadLinkHost`（CLI） |
| `PadLink.Gui` | WinForms 管理界面 |
| `PadLink.Core.Tests` | 41 个测试，含协议向量一致性 |

## 下载

[**Releases**](https://github.com/huangsiyu921-cmd/PadLink/releases) 里有预编译包：

| 文件 | 说明 |
| --- | --- |
| `PadLink-x.y.z.apk` | Android 端，装到手机即可 |
| `PadLink-x.y.z-win-x64.zip` | PC 端，解压后双击 `PadLink.Gui.exe`；不打包 .NET 运行时，依赖见下 |

不想下载就按下面从源码构建。

## 运行要求

- **PC 端**：Windows 10 / 11 x64
  - [.NET 10 **桌面**运行时（x64）](https://dotnet.microsoft.com/download/dotnet/10.0/runtime) —— 图形界面是 WinForms，要的是 `Microsoft.WindowsDesktop.App`，不是普通的 .NET Runtime
  - **ViGEmBus 驱动 v1.22.0** —— [下载](https://github.com/nefarius/ViGEmBus/releases)。造虚拟手柄的内核驱动，没有它建不出手柄；该仓库已于 2023-11-02 归档停更，v1.22.0 是最后版本，仍可用
  - **adb** —— 只有 USB 模式需要，把 [platform-tools](https://developer.android.com/tools/releases/platform-tools) 加进 `PATH`
- **手机端**：Android 8.0（API 26）以上。
- **从源码构建 Android**：JDK **17**（JDK 25 会破坏 Kotlin 编译器）+ Android SDK 35，SDK 路径写在 `src/android/local.properties`（不入库）。

## 怎么跑

### PC 端

```powershell
cd src\pc
dotnet test                                        # 协议 + fail-safe 测试
dotnet run --project PadLink.Gui                   # 图形界面
```

发布成可直接双击的目录（默认输出到本仓库上一级的 `app\`，用 `-Output` 改）：

```powershell
.\build.ps1
.\build.ps1 -Output dist                           # 输出到仓库内 dist\
```

命令行方式：

```powershell
cd src\pc
dotnet run --project PadLink.Server                # 起服务
dotnet run --project PadLink.Server -- --controller ds4    # 以 PS4 身份出现
dotnet run --project PadLink.Server -- --adb               # 起服务并自动建 adb reverse 隧道
dotnet run --project PadLink.Server -- --verify            # 自检：建手柄 → XInput 回读比对
dotnet run --project PadLink.Server -- --probe             # 只读 XInput，看系统里有没有输入
```

### 手机端

```powershell
cd src\android
.\gradlew.bat :core:test :app:testDebugUnitTest    # 协议向量 + 布局测试
.\gradlew.bat :app:assembleDebug                   # 产物在 app\build\outputs\apk\debug\
.\gradlew.bat :app:assembleRelease                 # 正式包（需要签名配置，见下）
```

在 Android Studio 里直接跑也行：打开 `src/android`。

**签名**：`src/android/keystore.properties`（不入库）里写 keystore 路径与密码，`app/build.gradle.kts` 会自动读它给 `release` 挂签名。没有这个文件时 release 退回 debug 签名 —— 能编译，但不能正式发布。

```properties
storeFile=D:/path/to/padlink.jks
storePassword=...
keyAlias=...
keyPassword=...
```

### 没有手机也能验证 PC 端

```powershell
python tools/fake_pad.py                  # 5 秒演示序列（UDP / WiFi 模式）
python tools/fake_pad.py --transport tcp  # 走 TCP（ADB 模式，会先握手再发帧）
python tools/fake_pad.py --mode discover  # 广播探测 PC
python tools/fake_pad.py --mode hold      # 持续按住 A（Ctrl+C 退出，可观察 fail-safe）
```

## 协议

`protocol/protocol.md` 是两端唯一的契约。核心设计：

- 输入用 **UDP + 固定节拍的全量状态快照**（不是增量事件），丢包无害。
- 一切输入用**物理语义**（向上为正、向右为正），与具体后端解耦；Y 轴取反之类的硬件怪癖归 PC 端 Mapper。
- 手柄类型由 **PC 端**决定，手机帧里的 `controller` 字段会被覆盖。

改协议必须走完这三步，缺一步就会两端不一致：

```powershell
python tools/gen_testvectors.py                    # 重新生成向量
cd src\pc;      dotnet test                        # C# 端必须绿
cd src\android; .\gradlew.bat :core:test           # Kotlin 端必须绿
```

**硬约束**：`adb forward / reverse` 只转发 TCP，不支持 UDP。所以 ADB 模式的数据通道必须是 TCP，且方向是 `adb reverse`（设备端口 → 主机端口），PC 做 server、手机做 client。

## 状态

已经能用的都真机验证过：ADB 全链路、手柄类型切换、PC 界面、手机界面、布局系统、重力转向、协议互验。

还没做：WiFi 模式的真机验证、震动回传（协议帧已定义，两端都没实现）、`adb pair` 无线调试引导、主题动态取色。重力转向目前只绑左摇杆的 X 轴，扳机 / 按键那种「过阈值当按下」的目标还没定怎么绑。

## 文档

1. [`docs/01-企划审查.md`](docs/01-企划审查.md) — 事实核查：ViGEmBus 已停更、ADB 路径取舍、许可约束
2. [`docs/02-技术路线.md`](docs/02-技术路线.md) — 架构、协议、后端选型的完整理由
3. [`docs/03-路线图与待决策.md`](docs/03-路线图与待决策.md) — 决策记录与 P0–P5 分期
4. [`docs/04-本地开发笔记.md`](docs/04-本地开发笔记.md) — 本机目录布局、踩过的坑

## 许可

[GNU General Public License v3.0](LICENSE)。

注意：生态里的同类项目（VIIPER、Joy2DroidX、Controlloid）都是 GPL-3.0。将来若要接入 VIIPER，必须走「独立进程 + 它的 TCP API」，不能链接它的库。
