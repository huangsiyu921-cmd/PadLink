using System.Drawing;

namespace PadLink.Gui;

/// <summary>
/// 配色。跟 Android 端保持一致——两端看起来得像同一个东西。
/// </summary>
internal static class PadTheme
{
    public static readonly Color Background = Color.FromArgb(28, 28, 28);
    public static readonly Color Surface = Color.FromArgb(44, 44, 44);
    public static readonly Color SurfaceHover = Color.FromArgb(58, 58, 58);
    public static readonly Color SurfaceLine = Color.FromArgb(64, 64, 64);
    public static readonly Color Accent = Color.FromArgb(76, 141, 255);
    public static readonly Color Text = Color.FromArgb(237, 237, 237);
    public static readonly Color TextMuted = Color.FromArgb(158, 158, 158);
    public static readonly Color TextOnAccent = Color.FromArgb(16, 25, 43);
    public static readonly Color Ok = Color.FromArgb(129, 199, 132);
    public static readonly Color Warn = Color.FromArgb(255, 183, 77);
    public static readonly Color Error = Color.FromArgb(229, 115, 115);

    public static readonly Font Body = new("Microsoft YaHei UI", 9f);
    public static readonly Font BodySmall = new("Microsoft YaHei UI", 8.25f);
    public static readonly Font Mono = new("Consolas", 9f);

    /// <summary>深色主题下的扁平按钮。WinForms 默认外观在暗色底上很刺眼。</summary>
    public static Button MakeButton(string text, int width = 88, int height = 32)
    {
        var button = new Button
        {
            Text = text,
            Size = new Size(width, height),
            FlatStyle = FlatStyle.Flat,
            BackColor = Surface,
            ForeColor = Text,
            Font = Body,
            Cursor = Cursors.Hand,
            UseVisualStyleBackColor = false,
        };

        button.FlatAppearance.BorderSize = 0;
        button.FlatAppearance.MouseOverBackColor = SurfaceHover;
        button.FlatAppearance.MouseDownBackColor = Accent;

        // FlatStyle.Flat 下禁用态会自己变灰，但前景色得手动压暗，不然还是亮的。
        button.EnabledChanged += (_, _) => button.ForeColor = button.Enabled ? Text : TextMuted;
        return button;
    }

    /// <summary>像 FilterChip 那样的选择项：选中就填强调色。</summary>
    public static Button MakeChip(string text, bool selected, int width = 128)
    {
        var button = MakeButton(text, width, 30);
        ApplyChipState(button, selected);
        return button;
    }

    public static void ApplyChipState(Button button, bool selected)
    {
        button.BackColor = selected ? Accent : Surface;
        button.ForeColor = selected ? TextOnAccent : Text;
        button.FlatAppearance.MouseOverBackColor = selected ? Accent : SurfaceHover;
    }
}
