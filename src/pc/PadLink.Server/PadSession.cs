using System.Net;
using PadLink.Core;
using PadLink.Core.Protocol;

namespace PadLink.Server;

/// <summary>
/// 一个手机会话：把收到的帧喂给虚拟手柄，同时守住 fail-safe 与丢包统计。
/// </summary>
public sealed class PadSession(IPEndPoint remote, IVirtualController controller) : IDisposable
{
    private const uint SequenceJumpIgnoreThreshold = 300;

    private bool _hasSequence;

    public IPEndPoint Remote { get; } = remote;

    public IVirtualController Controller { get; } = controller;

    public InputTimeoutGuard Guard { get; } = new(ProtocolConstants.FailSafeTimeout);

    /// <summary>最近一次收到数据帧的时间戳（<see cref="TimeProvider.GetTimestamp"/>）。</summary>
    public long LastFrameTimestamp { get; private set; } = TimeProvider.System.GetTimestamp();

    /// <summary>空闲时长，供会话回收判断。</summary>
    public TimeSpan IdleFor => TimeProvider.System.GetElapsedTime(LastFrameTimestamp);

    public uint LastSequence { get; private set; }

    public long FramesReceived { get; private set; }

    public long FramesLost { get; private set; }

    public bool Stale => Guard.IsStale;

    public void OnFrame(in InputFrame frame)
    {
        if (_hasSequence)
        {
            // uint 环绕算术天然处理 seq 回绕；跳变过大视为对端重启，不计入丢包。
            var lost = frame.Sequence - (LastSequence + 1);
            if (lost <= SequenceJumpIgnoreThreshold) FramesLost += lost;
        }

        _hasSequence = true;
        LastSequence = frame.Sequence;
        LastFrameTimestamp = TimeProvider.System.GetTimestamp();
        FramesReceived++;

        if (Guard.IsStale)
            Console.WriteLine($"  [P{Controller.Player}] 连接恢复，重新接管输入");

        Guard.OnFrame();
        Controller.Submit(GamepadState.FromFrame(frame));
    }

    /// <summary>周期调用。返回 true 表示本次刚进入失效状态并已归零。</summary>
    public bool PollFailSafe()
    {
        if (!Guard.Poll()) return false;

        Controller.Submit(GamepadState.Neutral);
        Console.WriteLine($"  [P{Controller.Player}] 超过 {ProtocolConstants.FailSafeTimeout.TotalMilliseconds:F0}ms 未收到数据，已归零输入");
        return true;
    }

    public void Dispose() => Controller.Dispose();
}
