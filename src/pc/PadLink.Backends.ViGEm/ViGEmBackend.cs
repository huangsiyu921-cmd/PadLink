using Nefarius.ViGEm.Client;
using PadLink.Core;
using PadLink.Core.Protocol;

namespace PadLink.Backends.ViGEm;

/// <summary>
/// ViGEmBus 后端：调用 Windows 内核驱动创建<b>系统认得的真实手柄</b>。
/// 需要已安装 ViGEmBus 驱动（管理员装一次）；本机已装。
/// </summary>
public sealed class ViGEmBackend : IVirtualControllerBackend
{
    private readonly ViGEmClient _client = new();

    public string Name => "vigem";

    public bool Supports(ControllerType type) => type is ControllerType.Xbox360 or ControllerType.Ds4;

    public IVirtualController Connect(byte player, ControllerType type) => type switch
    {
        ControllerType.Xbox360 => new Xbox360Device(_client, player),
        ControllerType.Ds4 => new DualShock4Device(_client, player),
        _ => throw new VirtualControllerException($"ViGEm 后端不支持 {type.ToWireName()}"),
    };

    public void Dispose() => _client.Dispose();
}

/// <summary>协议语义 → 各后端硬件语义的换算。怪癖集中在这里，不污染协议。</summary>
internal static class Mapping
{
    /// <summary>协议轴（-1..1，上/右为正）→ XInput 轴（int16，上/右为正，同向）。</summary>
    public static short ToXInputAxis(float value) => (short)Math.Clamp(value * 32767f, -32768f, 32767f);

    /// <summary>协议轴 → DS4 轴（0..255，中值 127/128）。</summary>
    /// <remarks>
    /// DS4 的 Y 轴方向与 XInput 相反（HID 惯例：向下为正），所以 Y 传进来之前要先取反，
    /// 见 <see cref="ToDs4AxisNegated"/>。⚠️ 这条是推断，必须在真机上用摇杆测试页校准。
    /// </remarks>
    public static byte ToDs4Axis(float value) => (byte)Math.Clamp(MathF.Round((value + 1f) * 127.5f), 0f, 255f);

    /// <summary>Y 轴专用：先取反再映射。</summary>
    public static byte ToDs4AxisNegated(float value) => ToDs4Axis(-value);

    /// <summary>协议扳机（0..1）→ 0..255。</summary>
    public static byte ToByte(float value) => (byte)Math.Clamp(MathF.Round(value * 255f), 0f, 255f);

    /// <summary>协议十字键（8 方向枚举）→ XInput 的四个独立方向键。</summary>
    public static (bool Up, bool Down, bool Left, bool Right) SplitDPad(DPad dpad) => dpad switch
    {
        DPad.North => (true, false, false, false),
        DPad.NorthEast => (true, false, false, true),
        DPad.East => (false, false, false, true),
        DPad.SouthEast => (false, true, false, true),
        DPad.South => (false, true, false, false),
        DPad.SouthWest => (false, true, true, false),
        DPad.West => (false, false, true, false),
        DPad.NorthWest => (true, false, true, false),
        _ => (false, false, false, false),
    };
}
