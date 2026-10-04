using System.Buffers.Binary;

namespace PadLink.Core.Protocol;

/// <summary>
/// 输入帧——<b>全量状态快照</b>，固定 28 字节。见 protocol.md §5。
/// <para>
/// 一切输入都是物理语义：<c>LeftY/RightY</c> <b>上为正</b>，<c>LeftX/RightX</c> 右为正，
/// 与具体后端（XInput / DS4）无关。硬件怪癖由 Mapper 处理。
/// </para>
/// </summary>
public readonly record struct InputFrame(
    ControllerType Controller,
    byte Player,
    bool RumbleAck,
    uint Sequence,
    uint TimestampMs,
    GamepadButtons Buttons,
    DPad DPad,
    short LeftX,
    short LeftY,
    short RightX,
    short RightY,
    ushort LeftTrigger,
    ushort RightTrigger)
{
    /// <summary>归零帧（所有输入松开）。fail-safe 用。</summary>
    public static InputFrame Neutral(ControllerType controller = ControllerType.Xbox360, byte player = 0)
        => new(controller, player, false, 0, 0, GamepadButtons.None, DPad.Neutral, 0, 0, 0, 0, 0, 0);

    public void Encode(Span<byte> destination)
    {
        if (destination.Length < ProtocolConstants.FrameSize)
            throw new ArgumentException($"目标缓冲区不足 {ProtocolConstants.FrameSize} 字节", nameof(destination));

        destination[0] = ProtocolConstants.Magic0;
        destination[1] = ProtocolConstants.Magic1;
        destination[2] = ProtocolConstants.Version;
        destination[3] = (byte)((Player & 0x03)
                                | (((byte)Controller & 0x07) << 2)
                                | (RumbleAck ? 1 << 5 : 0));
        BinaryPrimitives.WriteUInt32LittleEndian(destination[4..], Sequence);
        BinaryPrimitives.WriteUInt32LittleEndian(destination[8..], TimestampMs);
        BinaryPrimitives.WriteUInt16LittleEndian(destination[12..], (ushort)Buttons);
        destination[14] = (byte)DPad;
        destination[15] = 0;
        BinaryPrimitives.WriteInt16LittleEndian(destination[16..], LeftX);
        BinaryPrimitives.WriteInt16LittleEndian(destination[18..], LeftY);
        BinaryPrimitives.WriteInt16LittleEndian(destination[20..], RightX);
        BinaryPrimitives.WriteInt16LittleEndian(destination[22..], RightY);
        BinaryPrimitives.WriteUInt16LittleEndian(destination[24..], LeftTrigger);
        BinaryPrimitives.WriteUInt16LittleEndian(destination[26..], RightTrigger);
    }

    public byte[] Encode()
    {
        var buffer = new byte[ProtocolConstants.FrameSize];
        Encode(buffer);
        return buffer;
    }

    public static bool TryDecode(ReadOnlySpan<byte> source, out InputFrame frame, out ProtocolError error)
    {
        frame = default;

        if (source.Length != ProtocolConstants.FrameSize)
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

        if (source[15] != 0)
        {
            error = ProtocolError.ReservedNotZero;
            return false;
        }

        var attr = source[3];
        var controllerId = (byte)((attr >> 2) & 0x07);
        if (controllerId > (byte)ControllerType.SwitchPro)
        {
            error = ProtocolError.UnknownController;
            return false;
        }

        frame = new InputFrame(
            Controller: (ControllerType)controllerId,
            Player: (byte)(attr & 0x03),
            RumbleAck: (attr & (1 << 5)) != 0,
            Sequence: BinaryPrimitives.ReadUInt32LittleEndian(source[4..]),
            TimestampMs: BinaryPrimitives.ReadUInt32LittleEndian(source[8..]),
            Buttons: (GamepadButtons)BinaryPrimitives.ReadUInt16LittleEndian(source[12..]),
            DPad: (DPad)source[14],
            LeftX: BinaryPrimitives.ReadInt16LittleEndian(source[16..]),
            LeftY: BinaryPrimitives.ReadInt16LittleEndian(source[18..]),
            RightX: BinaryPrimitives.ReadInt16LittleEndian(source[20..]),
            RightY: BinaryPrimitives.ReadInt16LittleEndian(source[22..]),
            LeftTrigger: BinaryPrimitives.ReadUInt16LittleEndian(source[24..]),
            RightTrigger: BinaryPrimitives.ReadUInt16LittleEndian(source[26..]));

        // bit6-7 保留位：接收端忽略，便于后续扩展。
        error = ProtocolError.None;
        return true;
    }
}
