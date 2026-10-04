# PadLink

把 Android 手机变成 PC 的虚拟手柄：手机端渲染并采集触摸输入，经 WiFi / ADB 送到 PC，PC 端模拟出系统认得的 DS4 / Xbox 手柄。

- **PC 端**：C# / .NET，仅 Windows
- **移动端**：Kotlin + Android Studio，Material 3（手柄本体自绘）
- **虚拟手柄后端**：ViGEmBus 起步，`IVirtualControllerBackend` 抽象预留 VIIPER
- **传输**：WiFi (UDP) / ADB (TCP + `adb reverse`，USB 数据通道)
- **状态**：立项梳理完成、关键决策已定（2026-10-04），下一步进入 P0「定契约」

## 目录

```
docs/
  01-企划审查.md      对原始企划的事实核查与逐条问题清单
  02-技术路线.md      架构、协议、选型建议
  03-路线图与待决策.md 分期计划 + 决策记录（已定 D1/D3/D4/D5/D6/D7）
research/             调研记录 / 参考项目源码（.gitignore 忽略）
src/                  代码（后续按选定技术栈填充）
```

## 三条核心结论（先看这个）

1. 原企划的技术基石 **ViGEmBus 已于 2023-11-02 归档停更**（商标纠纷），冻结在 v1.22.0。仍在工作；已决定先用它起步、抽象层预留 VIIPER 换装，见 `docs/01` §1。
2. 原企划的 **"ADB 路径 B"（手机连自己的 adbd + `input` 命令）在本项目需求下不成立**，见 `docs/01` §3。
3. 企划漏掉了一个可能改变整个方向的替代方案：**手机直接当蓝牙 HID 手柄**，PC 端零软件零驱动，见 `docs/02` §1。
