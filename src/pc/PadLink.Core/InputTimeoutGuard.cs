namespace PadLink.Core;

/// <summary>
/// PC 端 fail-safe（protocol.md §7）。超过 <see cref="_timeout"/> 未收到合规输入帧时，
/// 调用方必须把手柄归零一次——否则 WiFi 一抖，游戏里的角色就会一直往前跑。
/// <para>超时只归零输入，<b>不销毁</b>虚拟手柄：销毁会让游戏重排 XInput 槽位。</para>
/// </summary>
public sealed class InputTimeoutGuard
{
    private readonly TimeSpan _timeout;
    private readonly TimeProvider _time;
    private long _lastFrameTimestamp;
    private bool _hasFrame;

    public InputTimeoutGuard(TimeSpan timeout, TimeProvider? timeProvider = null)
    {
        if (timeout <= TimeSpan.Zero) throw new ArgumentOutOfRangeException(nameof(timeout));
        _timeout = timeout;
        _time = timeProvider ?? TimeProvider.System;
    }

    /// <summary>是否已进入失效状态。</summary>
    public bool IsStale { get; private set; }

    /// <summary>收到一帧合规数据。</summary>
    public void OnFrame()
    {
        _lastFrameTimestamp = _time.GetTimestamp();
        _hasFrame = true;
        IsStale = false;
    }

    /// <summary>周期性调用（建议 ≥ 20Hz）。</summary>
    /// <returns>true 表示本次发生了「正常 → 失效」的跃迁，调用方应立即归零一次。</returns>
    public bool Poll()
    {
        if (!_hasFrame || IsStale) return false;
        if (_time.GetElapsedTime(_lastFrameTimestamp) <= _timeout) return false;

        IsStale = true;
        return true;
    }

    /// <summary>会话结束时重置。</summary>
    public void Reset()
    {
        _hasFrame = false;
        IsStale = false;
        _lastFrameTimestamp = 0;
    }
}
