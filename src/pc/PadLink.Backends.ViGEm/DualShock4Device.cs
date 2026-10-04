using Nefarius.ViGEm.Client;
using Nefarius.ViGEm.Client.Targets;
using Nefarius.ViGEm.Client.Targets.DualShock4;
using PadLink.Core;
using PadLink.Core.Protocol;

namespace PadLink.Backends.ViGEm;

/// <summary>
/// DualShock 4 虚拟手柄。价值主要是让游戏显示 PlayStation 按键提示——
/// 绝大多数游戏只认 XInput，所以 Xbox 360 才是默认输出（见 docs/01 §2.8）。
/// </summary>
internal sealed class DualShock4Device : IVirtualController
{
    /// <summary>
    /// 协议十字键（北=1 … 西北=8）→ DS4 的方向对象。
    /// DS4 的枚举是「北=0 … 西北=7，无=8」，且这些成员是<b>类</b>不是枚举值，只能用查表。
    /// </summary>
    private static readonly DualShock4DPadDirection[] DPadTable =
    [
        DualShock4DPadDirection.North,
        DualShock4DPadDirection.Northeast,
        DualShock4DPadDirection.East,
        DualShock4DPadDirection.Southeast,
        DualShock4DPadDirection.South,
        DualShock4DPadDirection.Southwest,
        DualShock4DPadDirection.West,
        DualShock4DPadDirection.Northwest,
    ];

    private readonly IDualShock4Controller _pad;
    private bool _connected;

    public DualShock4Device(ViGEmClient client, byte player)
    {
        Player = player;
        _pad = client.CreateDualShock4Controller();
        _pad.AutoSubmitReport = false;

        try
        {
            _pad.Connect();
        }
        catch (Exception ex)
        {
            throw new VirtualControllerException($"ViGEmBus 拒绝接入 DualShock 4 手柄（player {player}）：{ex.Message}", ex);
        }

        // 官方已把 DS4 的 FeedbackReceived 标记为过时（建议改用 AwaitRawOutputReport）。
        // 震动属于 P5 的可选增强，这里先沿用旧事件，将来换掉。
#pragma warning disable CS0618
        _pad.FeedbackReceived += OnFeedbackReceived;
#pragma warning restore CS0618
        _connected = true;
    }

    public byte Player { get; }

    public ControllerType Type => ControllerType.Ds4;

    /// <summary>
    /// DS4 在 ViGEm 里不暴露槽位（它以 HID 设备出现，不占 XInput 槽位），
    /// 这里用 player 编号占位。
    /// </summary>
    public int Slot => Player;

    public event EventHandler<RumbleEventArgs>? Rumble;

    public void Submit(in GamepadState state)
    {
        var buttons = state.Buttons;

        // 物理位置语义 → DS4 的符号键名：bit0 是下方那颗键，对应 Cross。
        Set(DualShock4Button.Cross, buttons.HasFlag(GamepadButtons.A));
        Set(DualShock4Button.Circle, buttons.HasFlag(GamepadButtons.B));
        Set(DualShock4Button.Square, buttons.HasFlag(GamepadButtons.X));
        Set(DualShock4Button.Triangle, buttons.HasFlag(GamepadButtons.Y));
        Set(DualShock4Button.ShoulderLeft, buttons.HasFlag(GamepadButtons.LeftShoulder));
        Set(DualShock4Button.ShoulderRight, buttons.HasFlag(GamepadButtons.RightShoulder));
        Set(DualShock4Button.Share, buttons.HasFlag(GamepadButtons.Back));
        Set(DualShock4Button.Options, buttons.HasFlag(GamepadButtons.Start));
        Set(DualShock4Button.ThumbLeft, buttons.HasFlag(GamepadButtons.LeftThumb));
        Set(DualShock4Button.ThumbRight, buttons.HasFlag(GamepadButtons.RightThumb));

        _pad.SetDPadDirection(state.DPad == DPad.Neutral
            ? DualShock4DPadDirection.None
            : DPadTable[(byte)state.DPad - 1]);

        _pad.SetAxisValue(DualShock4Axis.LeftThumbX, Mapping.ToDs4Axis(state.LeftX));
        _pad.SetAxisValue(DualShock4Axis.LeftThumbY, Mapping.ToDs4AxisNegated(state.LeftY));
        _pad.SetAxisValue(DualShock4Axis.RightThumbX, Mapping.ToDs4Axis(state.RightX));
        _pad.SetAxisValue(DualShock4Axis.RightThumbY, Mapping.ToDs4AxisNegated(state.RightY));

        // DS4 的扳机既是模拟量也是「按钮」（按下时置位），两边都要给。
        var leftTrigger = Mapping.ToByte(state.LeftTrigger);
        var rightTrigger = Mapping.ToByte(state.RightTrigger);
        _pad.SetSliderValue(DualShock4Slider.LeftTrigger, leftTrigger);
        _pad.SetSliderValue(DualShock4Slider.RightTrigger, rightTrigger);
        Set(DualShock4Button.TriggerLeft, leftTrigger > 0);
        Set(DualShock4Button.TriggerRight, rightTrigger > 0);

        // PS / Touchpad 是「特殊按钮」，走单独的位掩码接口。
        byte special = 0;
        if (buttons.HasFlag(GamepadButtons.Guide)) special |= (byte)DualShock4SpecialButton.Ps.Value;
        if (buttons.HasFlag(GamepadButtons.Touchpad)) special |= (byte)DualShock4SpecialButton.Touchpad.Value;
        _pad.SetSpecialButtonsFull(special);

        _pad.SubmitReport();
    }

    public void Dispose()
    {
        if (!_connected) return;
        _connected = false;
#pragma warning disable CS0618
        _pad.FeedbackReceived -= OnFeedbackReceived;
#pragma warning restore CS0618
        _pad.Disconnect();
    }

    private void Set(DualShock4Button button, bool value) => _pad.SetButtonState(button, value);

    private void OnFeedbackReceived(object? sender, DualShock4FeedbackReceivedEventArgs e)
        => Rumble?.Invoke(this, new RumbleEventArgs(e.LargeMotor, e.SmallMotor));
}
