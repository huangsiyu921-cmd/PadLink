using PadLink.Core;
using PadLink.Core.Protocol;

namespace PadLink.Server.Backends;

/// <summary>
/// 调试后端：不创建真实虚拟手柄，只把收到的状态打印出来。
/// 用于在装上 ViGEmBus 之前验证「手机 → 网络 → PC → 映射」这条链路。
/// </summary>
public sealed class ConsoleBackend : IVirtualControllerBackend
{
    public string Name => "console";

    public bool Supports(ControllerType type) => true;

    public IVirtualController Connect(byte player, ControllerType type) => new ConsoleController(player, type);

    public void Dispose()
    {
    }

    private sealed class ConsoleController(byte player, ControllerType type) : IVirtualController
    {
        private GamepadState _last = GamepadState.Neutral;

        public byte Player => player;

        public ControllerType Type => type;

        // 调试后端不产生震动回传；真实后端（ViGEm/VIIPER）会触发它。
#pragma warning disable CS0067
        public event EventHandler<RumbleEventArgs>? Rumble;
#pragma warning restore CS0067

        public void Submit(in GamepadState state)
        {
            if (state.Equals(_last)) return;
            _last = state;
            Console.WriteLine($"  [P{player}] {Describe(state)}");
        }

        public void Dispose() => Console.WriteLine($"  [P{player}] 手柄已销毁（{type.ToWireName()}）");

        private static string Describe(in GamepadState s)
        {
            var parts = new List<string>(8);

            if (s.Buttons != GamepadButtons.None) parts.Add($"keys={s.Buttons}");
            if (s.DPad != DPad.Neutral) parts.Add($"dpad={s.DPad}");
            if (s.LeftX != 0f || s.LeftY != 0f) parts.Add($"L=({s.LeftX,5:F2},{s.LeftY,5:F2})");
            if (s.RightX != 0f || s.RightY != 0f) parts.Add($"R=({s.RightX,5:F2},{s.RightY,5:F2})");
            if (s.LeftTrigger != 0f) parts.Add($"LT={s.LeftTrigger:F2}");
            if (s.RightTrigger != 0f) parts.Add($"RT={s.RightTrigger:F2}");

            return parts.Count == 0 ? "(归零)" : string.Join("  ", parts);
        }
    }
}
