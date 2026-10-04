using Nefarius.ViGEm.Client;
using Nefarius.ViGEm.Client.Targets;
using Nefarius.ViGEm.Client.Targets.Xbox360;
using PadLink.Core;
using PadLink.Core.Protocol;

namespace PadLink.Backends.ViGEm;

/// <summary>Xbox 360 虚拟手柄（XInput 设备，兼容性最好，是默认输出）。</summary>
internal sealed class Xbox360Device : IVirtualController
{
    private readonly IXbox360Controller _pad;
    private bool _connected;

    public Xbox360Device(ViGEmClient client, byte player)
    {
        Player = player;
        _pad = client.CreateXbox360Controller();
        _pad.AutoSubmitReport = false;          // 一次 Submit 提交一整帧，避免多次上报

        try
        {
            _pad.Connect();
        }
        catch (Exception ex)
        {
            throw new VirtualControllerException($"ViGEmBus 拒绝接入 Xbox 360 手柄（player {player}）：{ex.Message}", ex);
        }

        _pad.FeedbackReceived += OnFeedbackReceived;
        _connected = true;
    }

    public byte Player { get; }

    public ControllerType Type => ControllerType.Xbox360;

    /// <summary>
    /// ViGEmBus 把这个设备挂到了哪个 XInput 槽位（0..3）。
    /// 驱动是<b>异步回报</b>的，刚 <c>Connect()</c> 时还没值，此时返回 -1。
    /// </summary>
    public int Slot
    {
        get
        {
            try
            {
                return _pad.UserIndex;
            }
            catch (Exception)
            {
                return -1;      // Xbox360UserIndexNotReportedException：还没收到驱动回报
            }
        }
    }

    public event EventHandler<RumbleEventArgs>? Rumble;

    public void Submit(in GamepadState state)
    {
        var buttons = state.Buttons;

        Set(Xbox360Button.A, buttons.HasFlag(GamepadButtons.A));
        Set(Xbox360Button.B, buttons.HasFlag(GamepadButtons.B));
        Set(Xbox360Button.X, buttons.HasFlag(GamepadButtons.X));
        Set(Xbox360Button.Y, buttons.HasFlag(GamepadButtons.Y));
        Set(Xbox360Button.LeftShoulder, buttons.HasFlag(GamepadButtons.LeftShoulder));
        Set(Xbox360Button.RightShoulder, buttons.HasFlag(GamepadButtons.RightShoulder));
        Set(Xbox360Button.Back, buttons.HasFlag(GamepadButtons.Back));
        Set(Xbox360Button.Start, buttons.HasFlag(GamepadButtons.Start));
        Set(Xbox360Button.LeftThumb, buttons.HasFlag(GamepadButtons.LeftThumb));
        Set(Xbox360Button.RightThumb, buttons.HasFlag(GamepadButtons.RightThumb));
        Set(Xbox360Button.Guide, buttons.HasFlag(GamepadButtons.Guide));
        // Touchpad 是 DS4 专属，Xbox 360 忽略。

        var (up, down, left, right) = Mapping.SplitDPad(state.DPad);
        Set(Xbox360Button.Up, up);
        Set(Xbox360Button.Down, down);
        Set(Xbox360Button.Left, left);
        Set(Xbox360Button.Right, right);

        // XInput 的 Y 轴正方向也是「上」，与协议一致，不需要取反。
        // （这一点由 --verify 用 XInput 回读实测确认，见 PadLink.Server/SelfTest.cs）
        _pad.SetAxisValue(Xbox360Axis.LeftThumbX, Mapping.ToXInputAxis(state.LeftX));
        _pad.SetAxisValue(Xbox360Axis.LeftThumbY, Mapping.ToXInputAxis(state.LeftY));
        _pad.SetAxisValue(Xbox360Axis.RightThumbX, Mapping.ToXInputAxis(state.RightX));
        _pad.SetAxisValue(Xbox360Axis.RightThumbY, Mapping.ToXInputAxis(state.RightY));

        _pad.SetSliderValue(Xbox360Slider.LeftTrigger, Mapping.ToByte(state.LeftTrigger));
        _pad.SetSliderValue(Xbox360Slider.RightTrigger, Mapping.ToByte(state.RightTrigger));

        _pad.SubmitReport();
    }

    public void Dispose()
    {
        if (!_connected) return;
        _connected = false;
        _pad.FeedbackReceived -= OnFeedbackReceived;
        _pad.Disconnect();
    }

    private void Set(Xbox360Button button, bool value) => _pad.SetButtonState(button, value);

    private void OnFeedbackReceived(object? sender, Xbox360FeedbackReceivedEventArgs e)
        => Rumble?.Invoke(this, new RumbleEventArgs(e.LargeMotor, e.SmallMotor));
}
