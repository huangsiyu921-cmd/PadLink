using System.Drawing;
using System.Runtime.InteropServices;

namespace PadLink.Gui;

/// <summary>
/// 独立日志窗口。
///
/// 主界面底下那条只有 190px 高，够瞄一眼，不够翻历史、不够对比前后两段。
/// 这里给个能拉大、能选中复制的地方。
///
/// 窗口只创建一次，**关掉只是藏起来**：日志照旧往它的 TextBox 里写，
/// 所以下次打开时之前那段还在。要是关了就销毁，用户会以为"日志丢了"。
/// </summary>
internal sealed class LogWindow : Form
{
    private readonly TextBox _box = new();
    private readonly CheckBox _follow = new();

    /// <summary>日志往这里写。窗口藏起来时也照写，所以别把它换成别的实例。</summary>
    internal TextBox Box => _box;

    /// <summary>是否自动滚到最新。要往上翻或者选一段文本时，先把它关掉。</summary>
    internal bool FollowTail => _follow.Checked;

    public LogWindow(Icon icon)
    {
        Icon = icon;
        Text = "PadLink · 日志";
        StartPosition = FormStartPosition.CenterParent;
        MinimumSize = new Size(520, 300);
        ClientSize = new Size(880, 520);
        BackColor = PadTheme.Background;
        ForeColor = PadTheme.Text;
        Font = PadTheme.Body;
        ShowInTaskbar = false;

        Build();
    }

    private void Build()
    {
        var bar = new FlowLayoutPanel
        {
            Dock = DockStyle.Top,
            Height = 44,
            FlowDirection = FlowDirection.LeftToRight,
            WrapContents = false,
            BackColor = PadTheme.Background,
            Padding = new Padding(12, 7, 12, 7),
        };

        var clear = PadTheme.MakeButton("清空", 76, 30);
        clear.Click += (_, _) => _box.Clear();

        var copy = PadTheme.MakeButton("复制", 76, 30);
        copy.Click += (_, _) => CopyAll();

        _follow.Text = "跟随";
        _follow.Checked = true;
        _follow.AutoSize = true;
        _follow.FlatStyle = FlatStyle.Flat;
        _follow.ForeColor = PadTheme.TextMuted;
        _follow.Cursor = Cursors.Hand;
        _follow.Margin = new Padding(14, 8, 0, 0);

        bar.Controls.Add(clear);
        bar.Controls.Add(copy);
        bar.Controls.Add(_follow);

        _box.Dock = DockStyle.Fill;
        _box.Multiline = true;
        _box.ReadOnly = true;
        _box.WordWrap = false;      // 日志一行一条，换行会把堆栈搅烂
        _box.ScrollBars = ScrollBars.Both;
        _box.BorderStyle = BorderStyle.None;
        _box.BackColor = Color.FromArgb(21, 21, 21);
        _box.ForeColor = PadTheme.TextMuted;
        _box.Font = PadTheme.Mono;

        // 跟主界面一样：Fill 先加、边缘后加，Dock 的 Top 才压得住。
        Controls.Add(_box);
        Controls.Add(bar);
    }

    private void CopyAll()
    {
        if (_box.TextLength == 0) return;

        try
        {
            Clipboard.SetText(_box.Text);
        }
        catch (ExternalException)
        {
            // 剪贴板被别的程序占着（很常见），别为这个弹框打断人。
        }
    }

    /// <summary>点「日志」按钮：已经开着就把它拎到前面，别开第二个。</summary>
    internal void ShowLog()
    {
        if (!Visible) Show();

        if (WindowState == FormWindowState.Minimized) WindowState = FormWindowState.Normal;
        BringToFront();
        Activate();
    }

    protected override void OnFormClosing(FormClosingEventArgs e)
    {
        // 用户点 X 只是收起来，日志还在继续收。真要销毁走 Dispose（主界面退出时）。
        if (e.CloseReason == CloseReason.UserClosing)
        {
            e.Cancel = true;
            Hide();
            return;
        }

        base.OnFormClosing(e);
    }

    /// <summary>标题栏跟主界面一起变深，否则窗口边框那条白很跳。</summary>
    protected override void OnHandleCreated(EventArgs e)
    {
        base.OnHandleCreated(e);
        DarkTitleBar.Apply(this);
    }
}
