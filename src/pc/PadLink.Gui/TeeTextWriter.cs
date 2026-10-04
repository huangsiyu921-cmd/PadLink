using System.Text;

namespace PadLink.Gui;

/// <summary>把一份输出同时写到两个地方（界面日志框 + 日志文件）。</summary>
internal sealed class TeeTextWriter(TextWriter first, TextWriter second) : TextWriter
{
    public override Encoding Encoding => first.Encoding;

    public override void Write(char value)
    {
        first.Write(value);
        second.Write(value);
    }

    public override void Write(string? value)
    {
        if (string.IsNullOrEmpty(value)) return;
        first.Write(value);
        second.Write(value);
    }
}
