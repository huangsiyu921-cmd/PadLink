using System.Net;
using System.Net.Sockets;
using PadLink.Backends.ViGEm;
using PadLink.Core.Protocol;
using PadLink.Server;

namespace PadLink.Gui;

/// <summary>
/// PC 端管理界面。配色与结构跟 Android 端对齐：
/// 深色底、顶上只一条（状态在左、设置居中）、其余都收进设置弹窗。
/// </summary>
public sealed class MainForm : Form
{
    private readonly Label _statusLabel = new();
    private readonly Button _settingsButton = new();
    private readonly DataGridView _sessionGrid = new();
    private readonly TextBox _logBox = new();
    private readonly System.Windows.Forms.Timer _refreshTimer = new() { Interval = 500 };
    private readonly NotifyIcon _trayIcon = new();

    private PadLinkHost? _host;
    private readonly string _localAddress;
    private bool _exiting;

    /// <summary>PC 端要模拟成什么手柄。切换会让服务重建一次。</summary>
    internal ControllerType PreferredController { get; private set; } = ControllerType.Xbox360;

    internal bool IsRunning => _host is not null;

    public MainForm()
    {
        _localAddress = Dns.GetHostAddresses(Dns.GetHostName())
            .FirstOrDefault(a => a.AddressFamily == AddressFamily.InterNetwork)
            ?.ToString() ?? "127.0.0.1";

        Text = "PadLink";
        StartPosition = FormStartPosition.CenterScreen;
        MinimumSize = new Size(820, 480);
        Size = new Size(940, 600);
        Font = PadTheme.Body;
        BackColor = PadTheme.Background;
        ForeColor = PadTheme.Text;

        BuildLayout();

        // 服务端那层照旧 Console.WriteLine，这里把它接到日志框上，省得改一遍代码。
        // 同时落一份到文件——界面万一出问题，还能事后翻日志。
        var fileLog = OpenLogFile();
        Console.SetOut(new TeeTextWriter(new TextBoxWriter(_logBox), fileLog));
        Console.SetError(new TeeTextWriter(new TextBoxWriter(_logBox), fileLog));

        // 缩到托盘后没有主窗口，出了事很难发现——把未处理异常也记进日志。
        Application.ThreadException += (_, e) => Console.WriteLine($"未处理的界面异常：{e.Exception}");
        AppDomain.CurrentDomain.UnhandledException +=
            (_, e) => Console.WriteLine($"未处理的异常：{e.ExceptionObject}");

        SetupTrayIcon();

        _refreshTimer.Tick += (_, _) => RefreshStatus();
        Shown += (_, _) => StartServer();
        FormClosing += OnFormClosing;
    }

    // ------------------------------------------------------------------ 界面

    private void BuildLayout()
    {
        SuspendLayout();

        // 顶上：状态在左、设置居中——跟 Android 的顶部条一个排法。
        var top = new TableLayoutPanel
        {
            Dock = DockStyle.Top,
            Height = 54,
            ColumnCount = 3,
            BackColor = PadTheme.Background,
            Padding = new Padding(14, 8, 14, 8),
        };
        top.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 40f));
        top.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 20f));
        top.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 40f));

        _statusLabel.Dock = DockStyle.Fill;
        _statusLabel.TextAlign = ContentAlignment.MiddleLeft;
        _statusLabel.ForeColor = PadTheme.TextMuted;
        _statusLabel.Text = "未启动";
        _statusLabel.AutoEllipsis = true;

        _settingsButton.Text = "设置";
        _settingsButton.Dock = DockStyle.Fill;
        _settingsButton.Margin = new Padding(30, 0, 30, 0);
        _settingsButton.FlatStyle = FlatStyle.Flat;
        _settingsButton.FlatAppearance.BorderSize = 0;
        _settingsButton.FlatAppearance.MouseOverBackColor = PadTheme.SurfaceHover;
        _settingsButton.FlatAppearance.MouseDownBackColor = PadTheme.Accent;
        _settingsButton.BackColor = PadTheme.Surface;
        _settingsButton.ForeColor = PadTheme.Text;
        _settingsButton.Cursor = Cursors.Hand;
        _settingsButton.UseVisualStyleBackColor = false;
        _settingsButton.Click += (_, _) => OpenSettings();

        top.Controls.Add(_statusLabel, 0, 0);
        top.Controls.Add(_settingsButton, 1, 0);

        // 日志在下：深色等宽，一眼能看出归零 / 回收这些事件。
        _logBox.Dock = DockStyle.Bottom;
        _logBox.Height = 190;
        _logBox.Multiline = true;
        _logBox.ReadOnly = true;
        _logBox.WordWrap = false;
        _logBox.ScrollBars = ScrollBars.Both;
        _logBox.BorderStyle = BorderStyle.None;
        _logBox.BackColor = Color.FromArgb(21, 21, 21);
        _logBox.ForeColor = PadTheme.TextMuted;
        _logBox.Font = PadTheme.Mono;

        ConfigureGrid();

        // 顺序有讲究：Fill 先加，边缘区域后加，这样 Top/Bottom 才能真正压住边缘。
        Controls.Add(_sessionGrid);
        Controls.Add(_logBox);
        Controls.Add(top);

        ResumeLayout();
    }

    private void ConfigureGrid()
    {
        _sessionGrid.Dock = DockStyle.Fill;
        _sessionGrid.AllowUserToAddRows = false;
        _sessionGrid.AllowUserToDeleteRows = false;
        _sessionGrid.AllowUserToResizeRows = false;
        _sessionGrid.ReadOnly = true;
        _sessionGrid.RowHeadersVisible = false;
        _sessionGrid.MultiSelect = false;
        _sessionGrid.SelectionMode = DataGridViewSelectionMode.FullRowSelect;
        _sessionGrid.AutoSizeColumnsMode = DataGridViewAutoSizeColumnsMode.Fill;
        _sessionGrid.BorderStyle = BorderStyle.None;
        _sessionGrid.EnableHeadersVisualStyles = false;
        _sessionGrid.ColumnHeadersHeightSizeMode = DataGridViewColumnHeadersHeightSizeMode.DisableResizing;
        _sessionGrid.ColumnHeadersHeight = 32;
        _sessionGrid.RowTemplate.Height = 30;
        _sessionGrid.GridColor = PadTheme.SurfaceLine;

        _sessionGrid.BackgroundColor = PadTheme.Background;
        _sessionGrid.DefaultCellStyle.BackColor = PadTheme.Background;
        _sessionGrid.DefaultCellStyle.ForeColor = PadTheme.Text;
        _sessionGrid.DefaultCellStyle.SelectionBackColor = Color.FromArgb(48, 74, 128);
        _sessionGrid.DefaultCellStyle.SelectionForeColor = PadTheme.Text;

        _sessionGrid.ColumnHeadersDefaultCellStyle.BackColor = PadTheme.Surface;
        _sessionGrid.ColumnHeadersDefaultCellStyle.ForeColor = PadTheme.TextMuted;
        _sessionGrid.ColumnHeadersDefaultCellStyle.SelectionBackColor = PadTheme.Surface;

        _sessionGrid.Columns.AddRange(
            new DataGridViewTextBoxColumn { Name = "address", HeaderText = "地址", FillWeight = 32 },
            new DataGridViewTextBoxColumn { Name = "pad", HeaderText = "手柄", FillWeight = 14 },
            new DataGridViewTextBoxColumn { Name = "player", HeaderText = "玩家", FillWeight = 10 },
            new DataGridViewTextBoxColumn { Name = "frames", HeaderText = "已收帧", FillWeight = 14 },
            new DataGridViewTextBoxColumn { Name = "loss", HeaderText = "丢包", FillWeight = 10 },
            new DataGridViewTextBoxColumn { Name = "idle", HeaderText = "空闲", FillWeight = 10 },
            new DataGridViewTextBoxColumn { Name = "state", HeaderText = "状态", FillWeight = 10 });

        // 表头不走 WinForms 那套样式了：ColumnHeadersDefaultCellStyle 实测压不住系统视觉样式，
        // 句柄一建出来就被盖回浅灰。干脆自己画，稳。
        _sessionGrid.CellPainting += PaintHeaderCell;
    }

    private void PaintHeaderCell(object? sender, DataGridViewCellPaintingEventArgs e)
    {
        if (e.RowIndex != -1 || e.ColumnIndex < 0) return;

        var bounds = e.CellBounds;

        using (var fill = new SolidBrush(PadTheme.Surface))
        {
            e.Graphics!.FillRectangle(fill, bounds);
        }

        using (var line = new Pen(PadTheme.SurfaceLine))
        {
            e.Graphics!.DrawLine(line, bounds.Left, bounds.Bottom - 1, bounds.Right - 1, bounds.Bottom - 1);
        }

        TextRenderer.DrawText(
            e.Graphics!,
            e.FormattedValue?.ToString() ?? string.Empty,
            PadTheme.BodySmall,
            bounds,
            PadTheme.TextMuted,
            TextFormatFlags.Left | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis);

        e.Handled = true;
    }

    // ------------------------------------------------------------------ 托盘

    private void SetupTrayIcon()
    {
        _trayIcon.Icon = SystemIcons.Application;
        _trayIcon.Text = "PadLink";
        _trayIcon.Visible = false;
        _trayIcon.DoubleClick += (_, _) => RestoreFromTray();

        var menu = new ContextMenuStrip();
        menu.Items.Add("打开", null, (_, _) => RestoreFromTray());
        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add("退出", null, (_, _) => ExitApplication());
        _trayIcon.ContextMenuStrip = menu;
    }

    private void OnFormClosing(object? sender, FormClosingEventArgs e)
    {
        // 除了系统关机/任务管理器，任何关闭动作都只是缩到托盘，服务继续跑；
        // 真要退出走托盘的「退出」。
        //
        // ⚠️ 实测坑：外部进程发来的 WM_CLOSE 会被判成 TaskManagerClosing，不是 UserClosing；
        // 真实点右上角 X 走的是 WM_SYSCOMMAND/SC_CLOSE，reason 才是 UserClosing。
        var systemClosing = e.CloseReason is CloseReason.WindowsShutDown or CloseReason.TaskManagerClosing;
        if (!_exiting && !systemClosing)
        {
            e.Cancel = true;
            HideToTray();
            return;
        }

        StopServer();
    }

    private void HideToTray()
    {
        // 千万别在这里动 ShowInTaskbar：改它会让 WinForms 销毁并重建窗口句柄，
        // 而主窗体句柄一销毁，Application.Run 就直接返回 —— 进程会"假装缩到托盘"然后退出。
        Hide();
        _trayIcon.Visible = true;
        UpdateTrayText();
    }

    private void RestoreFromTray()
    {
        Show();
        if (WindowState == FormWindowState.Minimized) WindowState = FormWindowState.Normal;
        Activate();
        _trayIcon.Visible = false;
    }

    private void ExitApplication()
    {
        _exiting = true;
        _trayIcon.Visible = false;
        Close();
    }

    private void UpdateTrayText()
    {
        if (!_trayIcon.Visible) return;

        var text = _host is null ? "PadLink · 已停止" : $"PadLink · {_host.Sessions.Count} 个手柄";
        _trayIcon.Text = text.Length > 63 ? text[..63] : text;    // NotifyIcon.Text 有长度上限
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            _trayIcon.Visible = false;      // 不先隐藏的话托盘里会留下幽灵图标
            _trayIcon.Dispose();
            _refreshTimer.Dispose();
        }

        base.Dispose(disposing);
    }

    /// <summary>让标题栏也跟着变深，否则窗口边框那条白跟里面的深色底格格不入。</summary>
    protected override void OnHandleCreated(EventArgs e)
    {
        base.OnHandleCreated(e);
        DarkTitleBar.Apply(this);
    }

    private void OpenSettings()
    {
        using var dialog = new SettingsDialog(this);
        dialog.ShowDialog(this);
    }

    // ------------------------------------------------------------------ 服务控制

    internal void StartServer()
    {
        if (_host is not null) return;

        try
        {
            var backend = new ViGEmBackend();
            _host = new PadLinkHost(
                backend,
                ProtocolConstants.UdpPort,
                ProtocolConstants.IdleSessionTimeout,
                ProtocolConstants.ControlPort,
                PreferredController);
            _host.Start();
        }
        catch (Exception ex)
        {
            _host?.Dispose();
            _host = null;

            // 不弹 MessageBox：模态框会把界面卡住等人点，而完整堆栈日志里已经有了。
            Console.WriteLine($"启动失败：{ex}");
            _statusLabel.Text = $"启动失败：{ex.Message}";
            _statusLabel.ForeColor = PadTheme.Error;
            return;
        }

        _refreshTimer.Start();
        RefreshStatus();

        // 插着线就把隧道顺手建好；没插线也不吵，日志里留一句就走。
        var (ok, message) = _host.TryEstablishAdbTunnel();
        Console.WriteLine(ok ? $"ADB 隧道已建立：{message}" : $"ADB 隧道未建立：{message}");
    }

    internal void StopServer()
    {
        _refreshTimer.Stop();

        if (_host is null) return;

        _host.Dispose();
        _host = null;

        _sessionGrid.Rows.Clear();
        _statusLabel.Text = "已停止";
        _statusLabel.ForeColor = PadTheme.TextMuted;
        UpdateTrayText();
    }

    /// <summary>建 adb reverse 隧道：手机插着线并授权后，连自己的 127.0.0.1 就能打到这台机器。</summary>
    internal void EstablishAdbTunnel()
    {
        if (_host is null) return;

        var (ok, message) = _host.TryEstablishAdbTunnel();
        Console.WriteLine(ok ? $"ADB 隧道：{message}" : $"ADB 隧道失败：{message}");
        _statusLabel.ForeColor = ok ? PadTheme.Ok : PadTheme.Error;
        _statusLabel.Text = ok ? "ADB 隧道已建立" : $"ADB 隧道失败：{message}";
    }

    /// <summary>
    /// 换了手柄类型。类型是建服务时定下来的（驱动层面创建设备），所以要重建一次；
    /// 正在跑就自动重启，免得用户还要手动点两下。
    /// </summary>
    internal void SetControllerType(ControllerType type)
    {
        if (type == PreferredController) return;

        PreferredController = type;
        Console.WriteLine($"手柄类型 → {type.ToWireName()}");

        if (_host is null) return;

        Console.WriteLine("正在按新类型重建服务…");
        StopServer();
        StartServer();
    }

    // ------------------------------------------------------------------ 状态刷新

    private void RefreshStatus()
    {
        if (_host is null)
        {
            _statusLabel.Text = "未启动";
            _statusLabel.ForeColor = PadTheme.TextMuted;
            UpdateTrayText();
            return;
        }

        var sessions = _host.Sessions.ToList();

        if (_sessionGrid.Rows.Count != sessions.Count)
        {
            _sessionGrid.Rows.Clear();
            for (var i = 0; i < sessions.Count; i++) _sessionGrid.Rows.Add();
        }

        for (var i = 0; i < sessions.Count; i++)
        {
            var session = sessions[i];
            var total = session.FramesReceived + session.FramesLost;
            var loss = total == 0 ? 0 : 100.0 * session.FramesLost / total;

            var row = _sessionGrid.Rows[i];
            row.Cells["address"].Value = session.Remote.ToString();
            row.Cells["pad"].Value = session.Controller.Type.ToWireName();
            row.Cells["player"].Value = $"P{session.Controller.Player}";
            row.Cells["frames"].Value = session.FramesReceived.ToString();
            row.Cells["loss"].Value = $"{loss:F1}%";
            row.Cells["idle"].Value = $"{session.IdleFor.TotalSeconds:F1}s";
            row.Cells["state"].Value = session.Stale ? "已归零" : "输入中";
            row.DefaultCellStyle.ForeColor = session.Stale ? PadTheme.Warn : PadTheme.Text;
        }

        var controller = PreferredController.ToWireName();
        var head = $"{_localAddress} · {controller} · ";
        _statusLabel.Text = sessions.Count == 0
            ? head + "等待手机连接"
            : head + $"{sessions.Count} 个手柄";
        _statusLabel.ForeColor = sessions.Count == 0 ? PadTheme.TextMuted : PadTheme.Ok;

        UpdateTrayText();
    }

    private static TextWriter OpenLogFile()
    {
        var dir = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PadLink");
        Directory.CreateDirectory(dir);

        // 每次启动截断：日志只用于排障，不需要累积历史。
        return new StreamWriter(Path.Combine(dir, "padlink-gui.log"), append: false) { AutoFlush = true };
    }
}
