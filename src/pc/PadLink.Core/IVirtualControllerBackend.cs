using PadLink.Core.Protocol;

namespace PadLink.Core;

/// <summary>
/// 虚拟手柄后端的统一门面。ViGEmBus 是第一个实现，VIIPER 是计划中的第二个
/// （见 docs/02 §3）——所有调用方只依赖这个接口，换后端不动业务代码。
/// </summary>
public interface IVirtualControllerBackend : IDisposable
{
    /// <summary>后端名，用于日志与握手时的能力宣告。</summary>
    string Name { get; }

    /// <summary>该后端能否创建指定类型的手柄。</summary>
    bool Supports(ControllerType type);

    /// <summary>创建并接入一个虚拟手柄。失败抛 <see cref="VirtualControllerException"/>。</summary>
    /// <param name="player">目标槽位，0..3。</param>
    /// <param name="type">要模拟的手柄类型。</param>
    IVirtualController Connect(byte player, ControllerType type);
}

/// <summary>一台虚拟手柄。</summary>
public interface IVirtualController : IDisposable
{
    byte Player { get; }

    ControllerType Type { get; }

    /// <summary>提交一次<b>全量</b>状态。调用频率应等于协商好的发送节拍。</summary>
    void Submit(in GamepadState state);

    /// <summary>驱动回传的震动强度（0..255）。后端不支持时不触发。</summary>
    event EventHandler<RumbleEventArgs>? Rumble;
}

public sealed class RumbleEventArgs(byte left, byte right) : EventArgs
{
    public byte Left { get; } = left;

    public byte Right { get; } = right;
}

public sealed class VirtualControllerException(string message, Exception? innerException = null)
    : Exception(message, innerException);
