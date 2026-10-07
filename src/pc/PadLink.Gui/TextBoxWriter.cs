using System.Text;

namespace PadLink.Gui;

/// <summary>
/// 把 <see cref="Console"/> 的输出接到界面的日志框上。
/// <para>
/// 这样服务端那一层完全不用改：它照旧 <c>Console.WriteLine</c>，界面自然就能看到连接、归零、回收等事件。
/// </para>
/// </summary>
/// <param name="target">接哪块文本框。</param>
/// <param name="shouldFollow">
/// 写完要不要滚到最新。独立日志窗口会传自己的「跟随」开关——不跟随时也不能动
/// SelectionStart，否则用户刚选中的一段文本会被光标跳走。
/// </param>
internal sealed class TextBoxWriter(TextBox target, Func<bool>? shouldFollow = null) : TextWriter
{
    /// <summary>超过这个长度就把前半截丢掉，免得日志框无限膨胀。</summary>
    private const int MaxChars = 120_000;

    public override Encoding Encoding => Encoding.UTF8;

    public override void Write(char value) => Append(value.ToString());

    public override void Write(string? value)
    {
        if (!string.IsNullOrEmpty(value)) Append(value);
    }

    private void Append(string text)
    {
        if (target.IsDisposed) return;

        if (target.InvokeRequired)
        {
            try
            {
                target.BeginInvoke(() => Append(text));
            }
            catch (InvalidOperationException)
            {
                // 窗口正在销毁，忽略
            }

            return;
        }

        if (target.TextLength > MaxChars)
            target.Text = target.Text[(target.TextLength / 3)..];

        target.AppendText(text);

        if (shouldFollow?.Invoke() ?? true)
        {
            target.SelectionStart = target.TextLength;
            target.ScrollToCaret();
        }
    }
}
