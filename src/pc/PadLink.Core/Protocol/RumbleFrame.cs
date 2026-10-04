namespace PadLink.Core.Protocol;

/// <summary>震动回传帧——PC → 手机，固定 8 字节。见 protocol.md §6。不重传、不确认。</summary>
public readonly record struct RumbleFrame(byte Player, byte Left, byte Right)
{
    public const byte TypeRumble = 0x01;

    public void Encode(Span<byte> destination)
    {
        if (destination.Length < ProtocolConstants.RumbleSize)
            throw new ArgumentException($"目标缓冲区不足 {ProtocolConstants.RumbleSize} 字节", nameof(destination));

        destination[0] = ProtocolConstants.Magic0;
        destination[1] = ProtocolConstants.Magic1;
        destination[2] = ProtocolConstants.Version;
        destination[3] = TypeRumble;
        destination[4] = Player;
        destination[5] = Left;
        destination[6] = Right;
        destination[7] = 0;
    }

    public byte[] Encode()
    {
        var buffer = new byte[ProtocolConstants.RumbleSize];
        Encode(buffer);
        return buffer;
    }

    public static bool TryDecode(ReadOnlySpan<byte> source, out RumbleFrame frame, out ProtocolError error)
    {
        frame = default;

        if (source.Length != ProtocolConstants.RumbleSize)
        {
            error = ProtocolError.BadSize;
            return false;
        }

        if (source[0] != ProtocolConstants.Magic0 || source[1] != ProtocolConstants.Magic1)
        {
            error = ProtocolError.BadMagic;
            return false;
        }

        if (source[2] != ProtocolConstants.Version)
        {
            error = ProtocolError.UnsupportedVersion;
            return false;
        }

        if (source[3] != TypeRumble)
        {
            error = ProtocolError.UnknownMessageType;
            return false;
        }

        frame = new RumbleFrame(source[4], source[5], source[6]);
        error = ProtocolError.None;
        return true;
    }
}
