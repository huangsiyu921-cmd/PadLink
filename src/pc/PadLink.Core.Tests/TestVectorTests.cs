using System.Text.Json;
using PadLink.Core.Protocol;

namespace PadLink.Core.Tests;

/// <summary>
/// 协议向量一致性测试。向量文件由 <c>tools/gen_testvectors.py</c> 生成，
/// Kotlin 端读的是同一份文件——两端必须对同一字节序列得出同一结论。
/// </summary>
public sealed class TestVectorTests
{
    private static readonly JsonDocument Document = Load();

    private static JsonDocument Load()
    {
        var path = Path.Combine(AppContext.BaseDirectory, "testvectors", "frames.json");
        if (!File.Exists(path))
            throw new FileNotFoundException($"缺少协议测试向量，请先运行 tools/gen_testvectors.py：{path}");
        return JsonDocument.Parse(File.ReadAllText(path));
    }

    private static JsonElement Root => Document.RootElement;

    private static JsonElement FindVector(string name)
        => Root.GetProperty("vectors").EnumerateArray()
            .First(v => v.GetProperty("name").GetString() == name);

    public static TheoryData<string> ValidNames => NamesOf("vectors");

    public static TheoryData<string> InvalidNames => NamesOf("invalid");

    private static TheoryData<string> NamesOf(string property)
    {
        var data = new TheoryData<string>();
        foreach (var item in Root.GetProperty(property).EnumerateArray())
            data.Add(item.GetProperty("name").GetString()!);
        return data;
    }

    [Fact]
    public void VectorFile_AgreesWithProtocolConstants()
    {
        Assert.Equal(ProtocolConstants.Version, Root.GetProperty("ver").GetByte());
        Assert.Equal(ProtocolConstants.FrameSize, Root.GetProperty("frame_size").GetInt32());
        Assert.Equal(ProtocolConstants.RumbleSize, Root.GetProperty("rumble_size").GetInt32());
        Assert.Equal("504c", Root.GetProperty("magic").GetString());
    }

    [Fact]
    public void VectorFile_AgreesWithButtonBitmask()
    {
        var bits = Root.GetProperty("button_bits");
        Assert.Equal((ushort)GamepadButtons.A, bits.GetProperty("a").GetUInt16());
        Assert.Equal((ushort)GamepadButtons.B, bits.GetProperty("b").GetUInt16());
        Assert.Equal((ushort)GamepadButtons.X, bits.GetProperty("x").GetUInt16());
        Assert.Equal((ushort)GamepadButtons.Y, bits.GetProperty("y").GetUInt16());
        Assert.Equal((ushort)GamepadButtons.LeftShoulder, bits.GetProperty("lb").GetUInt16());
        Assert.Equal((ushort)GamepadButtons.RightShoulder, bits.GetProperty("rb").GetUInt16());
        Assert.Equal((ushort)GamepadButtons.Back, bits.GetProperty("back").GetUInt16());
        Assert.Equal((ushort)GamepadButtons.Start, bits.GetProperty("start").GetUInt16());
        Assert.Equal((ushort)GamepadButtons.LeftThumb, bits.GetProperty("l3").GetUInt16());
        Assert.Equal((ushort)GamepadButtons.RightThumb, bits.GetProperty("r3").GetUInt16());
        Assert.Equal((ushort)GamepadButtons.Guide, bits.GetProperty("guide").GetUInt16());
        Assert.Equal((ushort)GamepadButtons.Touchpad, bits.GetProperty("touchpad").GetUInt16());
    }

    [Theory]
    [MemberData(nameof(ValidNames))]
    public void Decode_ReconstructsFields(string name)
    {
        var vector = FindVector(name);
        var bytes = Convert.FromHexString(vector.GetProperty("bytes").GetString()!);

        Assert.True(InputFrame.TryDecode(bytes, out var frame, out var error), $"{name}: 解码失败 {error}");

        var expected = vector.GetProperty("fields");
        Assert.Equal(expected.GetProperty("player").GetByte(), frame.Player);
        Assert.Equal(expected.GetProperty("controller").GetString(), frame.Controller.ToWireName());
        Assert.Equal(expected.GetProperty("rumble_ack").GetBoolean(), frame.RumbleAck);
        Assert.Equal(expected.GetProperty("seq").GetUInt32(), frame.Sequence);
        Assert.Equal(expected.GetProperty("ts").GetUInt32(), frame.TimestampMs);
        Assert.Equal(expected.GetProperty("buttons").GetUInt16(), (ushort)frame.Buttons);
        Assert.Equal(expected.GetProperty("dpad").GetByte(), (byte)frame.DPad);
        Assert.Equal(expected.GetProperty("lx").GetInt16(), frame.LeftX);
        Assert.Equal(expected.GetProperty("ly").GetInt16(), frame.LeftY);
        Assert.Equal(expected.GetProperty("rx").GetInt16(), frame.RightX);
        Assert.Equal(expected.GetProperty("ry").GetInt16(), frame.RightY);
        Assert.Equal(expected.GetProperty("lt").GetUInt16(), frame.LeftTrigger);
        Assert.Equal(expected.GetProperty("rt").GetUInt16(), frame.RightTrigger);
    }

    [Theory]
    [MemberData(nameof(ValidNames))]
    public void Encode_ReproducesVectorBytes(string name)
    {
        var vector = FindVector(name);
        var expected = Convert.FromHexString(vector.GetProperty("bytes").GetString()!);

        Assert.True(InputFrame.TryDecode(expected, out var frame, out var error), $"{name}: 解码失败 {error}");
        Assert.Equal(expected, frame.Encode());
    }

    [Theory]
    [MemberData(nameof(InvalidNames))]
    public void InvalidFrames_AreRejected(string name)
    {
        var vector = Root.GetProperty("invalid").EnumerateArray()
            .First(v => v.GetProperty("name").GetString() == name);
        var bytes = Convert.FromHexString(vector.GetProperty("bytes").GetString()!);

        Assert.False(InputFrame.TryDecode(bytes, out _, out var error), $"{name}: 本应被拒绝");
        Assert.NotEqual(ProtocolError.None, error);
    }

    [Fact]
    public void RumbleFrame_MatchesVector()
    {
        var rumble = Root.GetProperty("rumble");
        var expected = Convert.FromHexString(rumble.GetProperty("bytes").GetString()!);
        var frame = new RumbleFrame(
            rumble.GetProperty("player").GetByte(),
            rumble.GetProperty("left").GetByte(),
            rumble.GetProperty("right").GetByte());

        Assert.Equal(expected, frame.Encode());
        Assert.True(RumbleFrame.TryDecode(expected, out var decoded, out _));
        Assert.Equal(frame, decoded);
    }
}
