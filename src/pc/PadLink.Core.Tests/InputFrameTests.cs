using PadLink.Core.Protocol;

namespace PadLink.Core.Tests;

public sealed class InputFrameTests
{
    [Fact]
    public void Neutral_EncodesToAllZeroPayload()
    {
        var bytes = InputFrame.Neutral().Encode();

        Assert.Equal(ProtocolConstants.FrameSize, bytes.Length);
        Assert.Equal(0x50, bytes[0]);
        Assert.Equal(0x4C, bytes[1]);
        Assert.Equal(ProtocolConstants.Version, bytes[2]);
        Assert.Equal(0, bytes[3]);              // player 0 + controller xbox360 + 无 ack
        Assert.All(bytes[4..], b => Assert.Equal(0, b));
    }

    [Fact]
    public void PlayerAndController_OccupyAttrBits()
    {
        // 协议 §5：attr = player(2b) | controller(3b)<<2 | rumble_ack(1b)<<5
        var frame = InputFrame.Neutral(ControllerType.Ds4, player: 3) with { RumbleAck = true };
        var attr = frame.Encode()[3];

        Assert.Equal(3, attr & 0b0000_0011);
        Assert.Equal(1, (attr >> 2) & 0b0000_0111);
        Assert.True((attr & (1 << 5)) != 0);
    }

    [Fact]
    public void RoundTrip_PreservesEveryField()
    {
        var random = new Random(20261004);

        for (var i = 0; i < 2000; i++)
        {
            var frame = new InputFrame(
                Controller: (ControllerType)random.Next(0, 4),
                Player: (byte)random.Next(0, 4),
                RumbleAck: random.Next(2) == 1,
                Sequence: (uint)random.NextInt64(0, (long)uint.MaxValue + 1),
                TimestampMs: (uint)random.NextInt64(0, (long)uint.MaxValue + 1),
                Buttons: (GamepadButtons)(ushort)random.Next(0, 1 << 16),
                DPad: (DPad)random.Next(0, 9),
                LeftX: (short)random.Next(short.MinValue, short.MaxValue + 1),
                LeftY: (short)random.Next(short.MinValue, short.MaxValue + 1),
                RightX: (short)random.Next(short.MinValue, short.MaxValue + 1),
                RightY: (short)random.Next(short.MinValue, short.MaxValue + 1),
                LeftTrigger: (ushort)random.Next(0, 32768),
                RightTrigger: (ushort)random.Next(0, 32768));

            Assert.True(InputFrame.TryDecode(frame.Encode(), out var decoded, out var error),
                $"第 {i} 次往返失败：{error}");
            Assert.Equal(frame, decoded);
        }
    }

    [Fact]
    public void Decode_RejectsWrongMagic()
    {
        var bytes = InputFrame.Neutral().Encode();
        bytes[0] = (byte)'X';

        Assert.False(InputFrame.TryDecode(bytes, out _, out var error));
        Assert.Equal(ProtocolError.BadMagic, error);
    }

    [Fact]
    public void Decode_RejectsUnknownVersion()
    {
        var bytes = InputFrame.Neutral().Encode();
        bytes[2] = 9;

        Assert.False(InputFrame.TryDecode(bytes, out _, out var error));
        Assert.Equal(ProtocolError.UnsupportedVersion, error);
    }

    [Fact]
    public void Decode_RejectsDirtyReservedByte()
    {
        var bytes = InputFrame.Neutral().Encode();
        bytes[15] = 0xFF;

        Assert.False(InputFrame.TryDecode(bytes, out _, out var error));
        Assert.Equal(ProtocolError.ReservedNotZero, error);
    }

    [Fact]
    public void Decode_RejectsUnknownControllerId()
    {
        var bytes = InputFrame.Neutral().Encode();
        bytes[3] = 5 << 2;

        Assert.False(InputFrame.TryDecode(bytes, out _, out var error));
        Assert.Equal(ProtocolError.UnknownController, error);
    }

    [Fact]
    public void Decode_IgnoresReservedAttrBits()
    {
        // 接收端对 attr bit6-7 宽容，便于将来扩展（协议 §5）。
        var bytes = InputFrame.Neutral().Encode();
        bytes[3] |= 0b1100_0000;

        Assert.True(InputFrame.TryDecode(bytes, out var frame, out _));
        Assert.Equal(0, frame.Player);
    }

    [Fact]
    public void Encode_RejectsUndersizedBuffer()
    {
        var tooSmall = new byte[ProtocolConstants.FrameSize - 1];
        Assert.Throws<ArgumentException>(() => InputFrame.Neutral().Encode(tooSmall));
    }

    [Fact]
    public void GamepadState_NormalizesAxesAndTriggers()
    {
        var frame = InputFrame.Neutral() with
        {
            LeftX = 32767,
            LeftY = -32767,
            RightX = short.MinValue,
            RightY = 0,
            LeftTrigger = 32767,
            RightTrigger = 16384,
        };

        var state = GamepadState.FromFrame(frame);

        Assert.Equal(1f, state.LeftX);
        Assert.Equal(-1f, state.LeftY);
        Assert.Equal(-1f, state.RightX);        // short.MinValue 会被 clamp 到 -1
        Assert.Equal(0f, state.RightY);
        Assert.Equal(1f, state.LeftTrigger);
        Assert.InRange(state.RightTrigger, 0.49f, 0.51f);
    }
}
