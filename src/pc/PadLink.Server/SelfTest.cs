using System.Runtime.InteropServices;
using PadLink.Backends.ViGEm;
using PadLink.Core;
using PadLink.Core.Protocol;

namespace PadLink.Server;

/// <summary>
/// 自检：创建一个真实的虚拟手柄，提交已知状态，再用 XInput 把它<b>读回来</b>比对。
/// <para>
/// 为什么需要它：人眼看 <c>joy.cpl</c> 无法进 CI，也一眼看不出"Y 轴上下颠倒"这类错误。
/// 这个回读把"映射对不对"变成可断言的数字。
/// </para>
/// <para>用法：<c>dotnet run --project PadLink.Server -- --verify</c></para>
/// </summary>
internal static class SelfTest
{
    private const ushort DpadUp = 0x0001;
    private const ushort DpadRight = 0x0008;
    private const ushort Back = 0x0020;
    private const ushort LeftShoulder = 0x0100;
    private const ushort ButtonA = 0x1000;
    private const ushort ButtonB = 0x2000;

    public static int Run()
    {
        using var backend = new ViGEmBackend();

        IVirtualController pad;
        try
        {
            pad = backend.Connect(0, ControllerType.Xbox360);
        }
        catch (Exception ex)
        {
            Console.WriteLine($"✗ 无法创建虚拟手柄：{ex.Message}");
            Console.WriteLine("  需要已安装 ViGEmBus 驱动。");
            return 1;
        }

        using (pad)
        {
            // 不能假定 slot 0：机器上可能有别的虚拟手柄（例如 EMotion）占着槽位，
            // 驱动回报的 UserIndex 也可能滞后。用一个独特的标记值找出"哪个槽位在响应我们"。
            var slot = LocateSlot(pad, 5000);
            if (slot < 0)
            {
                Console.WriteLine("✗ 5 秒内没找到响应本程序的 XInput 槽位。各槽位当前状态：");
                for (var s = 0; s < 4; s++)
                    Console.WriteLine(TryReadSlot(s, out var g)
                        ? $"    槽位 {s}: wButtons=0x{g.wButtons:X4}  LX={g.sThumbLX} LY={g.sThumbLY}"
                        : $"    槽位 {s}: 无设备");
                return 1;
            }

            Console.WriteLine($"PadLink 自检：虚拟手柄 → XInput 回读（槽位 {slot}）\n");

            if (!WaitUntilReadable(pad, slot, 3000))
            {
                TryReadSlot(slot, out var stuck);
                Console.WriteLine($"✗ 槽位 {slot} 在 3 秒内没进入可读的全零状态。");
                Console.WriteLine($"  当前读到：wButtons=0x{stuck.wButtons:X4}  "
                                  + $"LX={stuck.sThumbLX} LY={stuck.sThumbLY}  LT={stuck.bLeftTrigger}");
                return 1;
            }

            var failures = 0;

            failures += Check("松开全部按键 → 全零", pad, slot, GamepadState.Neutral, g =>
                g.wButtons != 0 ? $"wButtons=0x{g.wButtons:X4}"
                : g.bLeftTrigger != 0 || g.bRightTrigger != 0 ? "扳机非零"
                : g.sThumbLX != 0 || g.sThumbLY != 0 || g.sThumbRX != 0 || g.sThumbRY != 0 ? "摇杆非零"
                : null);

            failures += Check("A 键按下", pad, slot, GamepadState.Neutral with { Buttons = GamepadButtons.A }, g =>
                (g.wButtons & ButtonA) == 0 ? $"wButtons=0x{g.wButtons:X4}，A 位未置起" : null);

            failures += Check("LB + B 同时按下", pad, slot,
                GamepadState.Neutral with { Buttons = GamepadButtons.LeftShoulder | GamepadButtons.B }, g =>
                (g.wButtons & LeftShoulder) == 0 || (g.wButtons & ButtonB) == 0
                    ? $"wButtons=0x{g.wButtons:X4}，期望 0x{(LeftShoulder | ButtonB):X4}" : null);

            failures += Check("Back + Start + L3", pad, slot,
                GamepadState.Neutral with
                {
                    Buttons = GamepadButtons.Back | GamepadButtons.Start | GamepadButtons.LeftThumb,
                }, g =>
                (g.wButtons & (Back | 0x0010 | 0x0040)) != (Back | 0x0010 | 0x0040)
                    ? $"wButtons=0x{g.wButtons:X4}" : null);

            failures += Check("左摇杆推到最右 → sThumbLX ≈ +32767", pad, slot,
                GamepadState.Neutral with { LeftX = 1f }, g =>
                g.sThumbLX < 32000 ? $"sThumbLX={g.sThumbLX}" : null);

            // 这一项专门盯"上下颠倒"：协议 Y 上为正，XInput 也应该读到大正值。
            failures += Check("左摇杆推到最上 → sThumbLY ≈ +32767", pad, slot,
                GamepadState.Neutral with { LeftY = 1f }, g =>
                g.sThumbLY < 32000 ? $"sThumbLY={g.sThumbLY}（若为负值说明 Y 轴取反了）" : null);

            failures += Check("左摇杆最下 → sThumbLY ≈ -32767", pad, slot,
                GamepadState.Neutral with { LeftY = -1f }, g =>
                g.sThumbLY > -32000 ? $"sThumbLY={g.sThumbLY}" : null);

            failures += Check("右摇杆推到最右 → sThumbRX ≈ +32767", pad, slot,
                GamepadState.Neutral with { RightX = 1f }, g =>
                g.sThumbRX < 32000 ? $"sThumbRX={g.sThumbRX}" : null);

            failures += Check("左扳机踩满 → bLeftTrigger ≈ 255", pad, slot,
                GamepadState.Neutral with { LeftTrigger = 1f }, g =>
                g.bLeftTrigger < 250 ? $"bLeftTrigger={g.bLeftTrigger}" : null);

            failures += Check("双扳机半程 → 两者都在 100..155", pad, slot,
                GamepadState.Neutral with { LeftTrigger = 0.5f, RightTrigger = 0.5f }, g =>
                g.bLeftTrigger is < 100 or > 155 || g.bRightTrigger is < 100 or > 155
                    ? $"LT={g.bLeftTrigger} RT={g.bRightTrigger}" : null);

            failures += Check("十字键北 + 东 → DpadUp|DpadRight", pad, slot,
                GamepadState.Neutral with { DPad = DPad.NorthEast }, g =>
                (g.wButtons & (DpadUp | DpadRight)) != (DpadUp | DpadRight)
                    ? $"wButtons=0x{g.wButtons:X4}，期望含 0x{(DpadUp | DpadRight):X4}" : null);

            failures += Check("回到归零态", pad, slot, GamepadState.Neutral, g =>
                g.wButtons != 0 || g.bLeftTrigger != 0 || g.sThumbLX != 0
                    ? $"wButtons=0x{g.wButtons:X4}, LT={g.bLeftTrigger}, LX={g.sThumbLX}" : null);

            Console.WriteLine(failures == 0
                ? "\n全部通过：虚拟手柄在 XInput 里读回的值与协议语义一致。"
                : $"\n{failures} 项失败。");
            return failures == 0 ? 0 : 1;
        }
    }

    /// <summary>
    /// 只读 XInput 槽位并打印有输入的设备。
    /// 用来验证完整链路：外部数据 → 服务端 → 虚拟手柄 → <b>系统里可见</b>。
    /// </summary>
    public static int Probe(int seconds)
    {
        Console.WriteLine($"读取 XInput 四个槽位 {seconds} 秒（只打印有非零输入的）…\n");
        var deadline = Environment.TickCount64 + seconds * 1000;
        var printed = 0;

        while (Environment.TickCount64 < deadline)
        {
            for (var slot = 0; slot < 4; slot++)
            {
                if (!TryReadSlot(slot, out var g) || IsAllZero(g)) continue;
                Console.WriteLine($"  槽位 {slot}: 按键=0x{g.wButtons:X4}  "
                                  + $"LX={g.sThumbLX,6} LY={g.sThumbLY,6} RX={g.sThumbRX,6} RY={g.sThumbRY,6}  "
                                  + $"LT={g.bLeftTrigger,3} RT={g.bRightTrigger,3}");
                printed++;
            }

            Thread.Sleep(200);
        }

        Console.WriteLine(printed == 0
            ? "（这段时间在 XInput 里没读到任何非零输入）"
            : $"\n共读到 {printed} 条非零输入。");
        return printed == 0 ? 1 : 0;
    }

    /// <summary>
    /// 找出真正响应本程序的 XInput 槽位。
    /// 做法：反复把左摇杆推到"右下角"这个独特位置，然后扫 4 个槽位，谁跟着动就是我们的。
    /// 比读 ViGEm 的 UserIndex 可靠——那个值要等驱动异步回报，且机器上可能有别的虚拟手柄。
    /// </summary>
    private static int LocateSlot(IVirtualController pad, int timeoutMs)
    {
        var deadline = Environment.TickCount64 + timeoutMs;
        while (Environment.TickCount64 < deadline)
        {
            pad.Submit(GamepadState.Neutral with { LeftX = 1f, LeftY = -1f });
            Thread.Sleep(60);

            for (var candidate = 0; candidate < 4; candidate++)
            {
                if (!TryReadSlot(candidate, out var gamepad)) continue;
                if (gamepad.sThumbLX > 30000 && gamepad.sThumbLY < -30000) return candidate;
            }
        }

        return -1;
    }

    /// <summary>反复提交归零态，直到 XInput 也读回全零——设备枚举完成才算就绪。</summary>
    private static bool WaitUntilReadable(IVirtualController pad, int slot, int timeoutMs)
    {
        var deadline = Environment.TickCount64 + timeoutMs;
        while (Environment.TickCount64 < deadline)
        {
            pad.Submit(GamepadState.Neutral);
            Thread.Sleep(50);
            if (TryReadSlot(slot, out var gamepad) && IsAllZero(gamepad)) return true;
        }

        return false;
    }

    private static bool IsAllZero(XInputGamepad g) =>
        g.wButtons == 0 && g.bLeftTrigger == 0 && g.bRightTrigger == 0 &&
        g.sThumbLX == 0 && g.sThumbLY == 0 && g.sThumbRX == 0 && g.sThumbRY == 0;

    private static int Check(string label, IVirtualController pad, int slot, GamepadState state,
        Func<XInputGamepad, string?> verify)
    {
        pad.Submit(state);
        Thread.Sleep(80);                       // 等驱动把报告送到 XInput

        if (!TryReadSlot(slot, out var gamepad))
        {
            Console.WriteLine($"  ✗ {label}：读不到槽位 {slot}");
            return 1;
        }

        var problem = verify(gamepad);
        Console.WriteLine(problem is null ? $"  ✓ {label}" : $"  ✗ {label}：{problem}");
        return problem is null ? 0 : 1;
    }

    private static bool TryReadSlot(int slot, out XInputGamepad gamepad)
    {
        var state = new XInputState();
        var result = XInputGetState((uint)slot, ref state);
        gamepad = state.Gamepad;
        return result == 0;                     // ERROR_SUCCESS
    }

    // ---------------------------------------------------------------- XInput P/Invoke

    [StructLayout(LayoutKind.Sequential)]
    private struct XInputGamepad
    {
        public ushort wButtons;
        public byte bLeftTrigger;
        public byte bRightTrigger;
        public short sThumbLX;
        public short sThumbLY;
        public short sThumbRX;
        public short sThumbRY;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct XInputState
    {
        public uint dwPacketNumber;
        public XInputGamepad Gamepad;
    }

    [DllImport("xinput1_4.dll", EntryPoint = "XInputGetState")]
    private static extern uint XInputGetState(uint dwUserIndex, ref XInputState pState);
}
