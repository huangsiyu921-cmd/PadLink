namespace PadLink.Core.Protocol;

/// <summary>端口、节拍、超时等协议常量。见 protocol.md §1 / §7。</summary>
public static class ProtocolConstants
{
    /// <summary>控制通道（TCP）。</summary>
    public const int ControlPort = 42312;

    /// <summary>发现 + 数据通道（UDP，PC 端单 socket 复用）。</summary>
    public const int UdpPort = 42313;

    /// <summary>发现探测报文（文本）。</summary>
    public const string DiscoverProbe = "PADLINK?1";

    /// <summary>发现应答的前缀，用于与数据帧区分。</summary>
    public const string AnnouncePrefix = "PADLINK!1 ";

    public const byte Version = 1;

    /// <summary>输入帧固定长度。</summary>
    public const int FrameSize = 28;

    /// <summary>震动帧固定长度。</summary>
    public const int RumbleSize = 8;

    public const byte Magic0 = 0x50; // 'P'
    public const byte Magic1 = 0x4C; // 'L'

    /// <summary>正常发送节拍（Hz）。</summary>
    public const int ActiveRateHz = 60;

    /// <summary>无输入变化时的保活节拍（Hz）。</summary>
    public const int IdleRateHz = 10;

    /// <summary>PC 端 fail-safe：超过此时长未收到合规输入帧即归零输入。</summary>
    public static readonly TimeSpan FailSafeTimeout = TimeSpan.FromMilliseconds(300);

    /// <summary>XInput 槽位数上限。</summary>
    public const int MaxPlayers = 4;
}
