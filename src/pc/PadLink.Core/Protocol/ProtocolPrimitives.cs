namespace PadLink.Core.Protocol;

/// <summary>协议 <c>attr</c> 字段 bit2-4 的取值，见 protocol.md §5。</summary>
public enum ControllerType : byte
{
    Xbox360 = 0,
    Ds4 = 1,

    // 预留：协议已分配编号，后端未实现。
    DualSense = 2,
    SwitchPro = 3,
}

/// <summary>协议 <c>buttons</c> 位掩码。<b>物理位置语义</b>——bit0 永远是手柄下方那颗键，
/// UI 上显示 A 还是 ✕ 由布局模板决定。见 protocol.md §5.2。</summary>
[Flags]
public enum GamepadButtons : ushort
{
    None = 0,
    A = 1 << 0,
    B = 1 << 1,
    X = 1 << 2,
    Y = 1 << 3,
    LeftShoulder = 1 << 4,
    RightShoulder = 1 << 5,
    Back = 1 << 6,
    Start = 1 << 7,
    LeftThumb = 1 << 8,
    RightThumb = 1 << 9,
    Guide = 1 << 10,
    Touchpad = 1 << 11,

    All = 0x0FFF,
}

/// <summary>协议 <c>dpad</c> 枚举，见 protocol.md §5.3。</summary>
public enum DPad : byte
{
    Neutral = 0,
    North = 1,
    NorthEast = 2,
    East = 3,
    SouthEast = 4,
    South = 5,
    SouthWest = 6,
    West = 7,
    NorthWest = 8,
}

/// <summary>解码失败的原因。用于日志与测试断言，不跨网络传输。</summary>
public enum ProtocolError
{
    None = 0,
    BadSize,
    BadMagic,
    UnsupportedVersion,
    ReservedNotZero,
    UnknownController,
    UnknownMessageType,
}

public static class ControllerTypeWire
{
    public static string ToWireName(this ControllerType type) => type switch
    {
        ControllerType.Xbox360 => "xbox360",
        ControllerType.Ds4 => "ds4",
        ControllerType.DualSense => "dualsense",
        ControllerType.SwitchPro => "switchpro",
        _ => throw new ArgumentOutOfRangeException(nameof(type), type, "未知手柄类型"),
    };

    public static bool TryParse(string? name, out ControllerType type)
    {
        switch (name)
        {
            case "xbox360": type = ControllerType.Xbox360; return true;
            case "ds4": type = ControllerType.Ds4; return true;
            case "dualsense": type = ControllerType.DualSense; return true;
            case "switchpro": type = ControllerType.SwitchPro; return true;
            default: type = ControllerType.Xbox360; return false;
        }
    }
}
