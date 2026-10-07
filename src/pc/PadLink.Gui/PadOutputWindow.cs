using System.Drawing;
using System.Text;
using PadLink.Core;
using PadLink.Core.Protocol;

namespace PadLink.Gui;

/// <summary>
/// 「手柄输出」窗口：这台虚拟手柄此刻在输出什么。
///
/// 上面 8 行是**当前值**，原地刷新；下面是**变化记录**，只记真的变了的行。
/// 轴按 0.05 的粒度比较，否则浮点抖一点点就会把记录刷爆。
///
/// 数据来自 <c>PadSession.LastState</c>——每帧转成 GamepadState 后提交给驱动的那份，
/// 和游戏里收到的是同一个东西。调手机端输入（比如重力转向）时看它就够了。
/// </summary>
internal sealed class PadOutputWindow : Form
{
    /// <summary>轴和扳机的比较粒度。太小会一直刷，太大会看不出小幅移动。</summary>
    private const float Step = 0.05f;

    /// <summary>变化记录的上限，超了就丢掉前半截，别让文本框无限涨。</summary>
    private const int MaxEventChars = 200_000;

    private static readonly (GamepadButtons Bit, string Name)[] Keys =
    [
        (GamepadButtons.A, "A"),
        (GamepadButtons.B, "B"),
        (GamepadButtons.X, "X"),
        (GamepadButtons.Y, "Y"),
        (GamepadButtons.LeftShoulder, "LB"),
        (GamepadButtons.RightShoulder, "RB"),
        (GamepadButtons.Back, "Back"),
        (GamepadButtons.Start, "Start"),
        (GamepadButtons.LeftThumb, "L3"),
        (GamepadButtons.RightThumb, "R3"),
        (GamepadButtons.Guide, "Guide"),
        (GamepadButtons.Touchpad, "触摸板"),
    ];

    private readonly Func<GamepadState?> _read;
    private readonly TextBox _current = new();
    private readonly TextBox _events = new();
    private readonly CheckBox _record = new();
    private readonly System.Windows.Forms.Timer _poll = new() { Interval = 33 };

    private GamepadState? _last;
    private string _currentText = string.Empty;

    internal PadOutputWindow(Func<GamepadState?> read, Icon icon)
    {
        _read = read;

        Icon = icon;
        Text = "PadLink · 手柄输出";
        StartPosition = FormStartPosition.CenterParent;
        MinimumSize = new Size(560, 400);
        ClientSize = new Size(880, 560);
        BackColor = PadTheme.Background;
        ForeColor = PadTheme.Text;
        Font = PadTheme.Body;
        ShowInTaskbar = false;

        Build();

        _poll.Tick += (_, _) => Poll();

        // 收起来就停，没必要在后台一直读。
        VisibleChanged += (_, _) =>
        {
            if (Visible) _poll.Start();
            else _poll.Stop();
        };
    }

    private void Build()
    {
        var bar = new FlowLayoutPanel
        {
            Dock = DockStyle.Top,
            Height = 42,
            WrapContents = false,
            BackColor = PadTheme.Background,
            Padding = new Padding(10, 6, 10, 6),
        };

        _record.Text = "记录变化";
        _record.Checked = true;
        _record.AutoSize = true;
        _record.FlatStyle = FlatStyle.Flat;
        _record.ForeColor = PadTheme.TextMuted;
        _record.Cursor = Cursors.Hand;
        _record.Margin = new Padding(0, 8, 18, 0);

        var clear = PadTheme.MakeButton("清空", 76, 30);
        clear.Click += (_, _) =>
        {
            _events.Clear();
            _last = null;
        };

        bar.Controls.Add(_record);
        bar.Controls.Add(clear);

        _current.Dock = DockStyle.Top;
        _current.Height = 168;
        _current.Multiline = true;
        _current.ReadOnly = true;
        _current.WordWrap = false;
        _current.ScrollBars = ScrollBars.None;
        _current.BorderStyle = BorderStyle.None;
        _current.BackColor = Color.FromArgb(21, 21, 21);
        _current.ForeColor = PadTheme.Text;
        _current.Font = PadTheme.Mono;

        _events.Dock = DockStyle.Fill;
        _events.Multiline = true;
        _events.ReadOnly = true;
        _events.WordWrap = false;
        _events.ScrollBars = ScrollBars.Both;
        _events.BorderStyle = BorderStyle.None;
        _events.BackColor = Color.FromArgb(21, 21, 21);
        _events.ForeColor = PadTheme.TextMuted;
        _events.Font = PadTheme.Mono;

        // 跟主界面一个排法：Fill 先加，边缘后加，Top 才压得住。
        Controls.Add(_events);
        Controls.Add(_current);
        Controls.Add(bar);
    }

    // ------------------------------------------------------------------ 刷新

    private void Poll()
    {
        var now = _read();

        var text = now is { } state ? Format(state) : "（没有会话）";
        if (text != _currentText)
        {
            _currentText = text;
            _current.Text = text;
        }

        // 第一次拿到状态时不记：那会把"从零到现在"的所有差别当成一次突变。
        if (now is { } current && _last is { } previous && _record.Checked)
        {
            var changes = Diff(previous, current);
            if (changes.Count > 0) AppendEvent(changes);
        }

        _last = now;
    }

    private void AppendEvent(List<string> changes)
    {
        if (_events.TextLength > MaxEventChars)
            _events.Text = _events.Text[(_events.TextLength / 2)..];

        _events.AppendText($"{DateTime.Now:HH:mm:ss.fff}  {string.Join("  ", changes)}{Environment.NewLine}");
        _events.SelectionStart = _events.TextLength;
        _events.ScrollToCaret();
    }

    // ------------------------------------------------------------------ 格式化

    private static string Format(GamepadState s)
    {
        var sb = new StringBuilder();
        sb.AppendLine($"LX  {Signed(s.LeftX)}");
        sb.AppendLine($"LY  {Signed(s.LeftY)}");
        sb.AppendLine($"RX  {Signed(s.RightX)}");
        sb.AppendLine($"RY  {Signed(s.RightY)}");
        sb.AppendLine($"LT   {s.LeftTrigger:0.00}");
        sb.AppendLine($"RT   {s.RightTrigger:0.00}");
        sb.AppendLine($"按键  {PressedKeys(s)}");
        sb.Append($"十字  {Direction(s.DPad)}");
        return sb.ToString();
    }

    private static string PressedKeys(GamepadState s)
    {
        var names = Keys.Where(k => s.Buttons.HasFlag(k.Bit)).Select(k => k.Name).ToList();
        return names.Count == 0 ? "—" : string.Join(" ", names);
    }

    private static string Direction(DPad dpad) => dpad switch
    {
        DPad.North => "北",
        DPad.NorthEast => "东北",
        DPad.East => "东",
        DPad.SouthEast => "东南",
        DPad.South => "南",
        DPad.SouthWest => "西南",
        DPad.West => "西",
        DPad.NorthWest => "西北",
        _ => "中立",
    };

    private static string Signed(float value) =>
        (value >= 0 ? "+" : "-") + Math.Abs(value).ToString("0.00");

    // ------------------------------------------------------------------ 比较

    private static List<string> Diff(GamepadState a, GamepadState b)
    {
        var changes = new List<string>();

        Axis("LX", a.LeftX, b.LeftX, changes);
        Axis("LY", a.LeftY, b.LeftY, changes);
        Axis("RX", a.RightX, b.RightX, changes);
        Axis("RY", a.RightY, b.RightY, changes);
        Axis("LT", a.LeftTrigger, b.LeftTrigger, changes);
        Axis("RT", a.RightTrigger, b.RightTrigger, changes);

        foreach (var (bit, name) in Keys)
        {
            var was = a.Buttons.HasFlag(bit);
            var held = b.Buttons.HasFlag(bit);
            if (was != held) changes.Add(held ? $"{name} 按下" : $"{name} 松开");
        }

        if (a.DPad != b.DPad) changes.Add($"十字 {Direction(b.DPad)}");

        return changes;
    }

    private static void Axis(string name, float before, float after, List<string> into)
    {
        if (Math.Abs(before - after) < Step) return;
        into.Add($"{name} {Signed(after)}");
    }

    // ------------------------------------------------------------------ 窗口行为

    /// <summary>点按钮：已经开着就拎到前面，别开第二个。</summary>
    internal void ShowOutput()
    {
        if (!Visible) Show();

        if (WindowState == FormWindowState.Minimized) WindowState = FormWindowState.Normal;
        BringToFront();
        Activate();
    }

    protected override void OnFormClosing(FormClosingEventArgs e)
    {
        if (e.CloseReason == CloseReason.UserClosing)
        {
            e.Cancel = true;
            Hide();
            return;
        }

        base.OnFormClosing(e);
    }

    protected override void OnHandleCreated(EventArgs e)
    {
        base.OnHandleCreated(e);
        DarkTitleBar.Apply(this);
    }
}
