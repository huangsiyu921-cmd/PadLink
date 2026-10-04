using PadLink.Core.Protocol;

namespace PadLink.Core;

/// <summary>
/// 后端无关的手柄状态。轴向为 -1..1 的物理语义（上/右为正），扳机 0..1。
/// 后端各自负责把它翻译成硬件的怪癖（XInput 的 Y 轴取反、DS4 的 0..255 映射等）。
/// </summary>
public readonly record struct GamepadState(
    GamepadButtons Buttons,
    DPad DPad,
    float LeftX,
    float LeftY,
    float RightX,
    float RightY,
    float LeftTrigger,
    float RightTrigger)
{
    /// <summary>全部松开。fail-safe 用它。</summary>
    public static readonly GamepadState Neutral =
        new(GamepadButtons.None, DPad.Neutral, 0f, 0f, 0f, 0f, 0f, 0f);

    public static GamepadState FromFrame(in InputFrame frame) => new(
        frame.Buttons,
        frame.DPad,
        Axis(frame.LeftX),
        Axis(frame.LeftY),
        Axis(frame.RightX),
        Axis(frame.RightY),
        Trigger(frame.LeftTrigger),
        Trigger(frame.RightTrigger));

    private static float Axis(short raw) => Math.Clamp(raw / 32767f, -1f, 1f);

    private static float Trigger(ushort raw) => Math.Clamp(raw / 32767f, 0f, 1f);
}
