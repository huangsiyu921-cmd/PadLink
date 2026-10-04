using System.Net;
using System.Net.Sockets;
using PadLink.Backends.ViGEm;
using PadLink.Core.Protocol;
using PadLink.Server;

namespace PadLink.Gui;

/// <summary>
/// PC 端管理界面：启动/停止服务，直观看到谁连着、丢包多少、有没有卡住。
/// 界面很朴素（WinForms），够用就行——它只是壳，真正的活都在 <see cref="PadLinkHost"/> 里。
/// </summary>
public sealed class MainForm : Form
{
    private const string AppFont = "Microsoft YaHei UI";

    private readonly Button _startButton = new();
    private readonly Button _stopButton = new();
    private readonly Label _statusLabel = new();
    private readonly DataGridView _sessionGrid = new();
    private readonly TextBox _logBox = new();
    private readonly System.Windows.Forms.Timer _refreshTimer = new() { Interval = 500 };

    private PadLinkHost? _host;
    private readonly string _localAddress;

    public MainForm()
    {
        _localAddress = Dns.GetHostAddresses(Dns.GetHostName())
            .FirstOrDefault(a => a.AddressFamily == AddressFamily.InterNetwork)
            ?.ToString() ?? "127.0.0.1";

        Text = "PadLink";
        StartPosition = FormStartPosition.CenterScreen;
        MinimumSize = new Size(760, 480);
        Size = new Size(900, 620);
        Font = new Font(AppFont, 9f);
        BackColor = Color.FromArgb(243, 243, 243);

        BuildLayout();

        // 服务端那层照旧 Console.WriteLine，这里把它接到日志框上，省得改一遍代码。
        // 同时落一份到文件——界面万一出问题，还能事后翻日志。
        var fileLog = OpenLogFile();
        Console.SetOut(new TeeTextWriter(new TextBoxWriter(_logBox), fileLog));
        Console.SetError(new TeeTextWriter(new TextBoxWriter(_logBox), fileLog));

        _refreshTimer.Tick += (_, _) => RefreshSessions();
        Shown += (_, _) => StartServer();
        FormClosing += (_, _) => StopServer();
    }

    // ------------------------------------------------------------------ 界面

    private void BuildLayout()
    {
        SuspendLayout();

        var top = new Panel
        {
            Dock = DockStyle.Top,
            Height = 56,
            BackColor = Color.White,
            Padding = new Padding(16, 12, 16, 12),
        };

        StyleButton(_startButton, "启动", 0, StartServer);
        StyleButton(_stopButton, "停止", 96, StopServer);
        _stopButton.Enabled = false;

        _statusLabel.AutoSize = false;
        _statusLabel.Location = new Point(212, 20);
        _statusLabel.Size = new Size(640, 20);
        _statusLabel.ForeColor = Color.FromArgb(90, 90, 90);
        _statusLabel.Text = "未启动";

        top.Controls.Add(_startButton);
        top.Controls.Add(_stopButton);
        top.Controls.Add(_statusLabel);

        // 日志在下：深色等宽字体，一眼能看出归零 / 回收这些事件。
        _logBox.Dock = DockStyle.Bottom;
        _logBox.Height = 190;
        _logBox.Multiline = true;
        _logBox.ReadOnly = true;
        _logBox.WordWrap = false;
        _logBox.ScrollBars = ScrollBars.Both;
        _logBox.BorderStyle = BorderStyle.None;
        _logBox.BackColor = Color.FromArgb(32, 32, 32);
        _logBox.ForeColor = Color.FromArgb(206, 206, 206);
        _logBox.Font = new Font("Consolas", 9f);

        ConfigureGrid();

        // 顺序有讲究：Fill 先加，边缘区域后加，这样 Top/Bottom 才能真正压住边缘。
        Controls.Add(_sessionGrid);
        Controls.Add(_logBox);
        Controls.Add(top);

        ResumeLayout();
    }

    private static TextWriter OpenLogFile()
    {
        var dir = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PadLink");
        Directory.CreateDirectory(dir);

        // 每次启动截断：日志只用于排障，不需要累积历史。
        return new StreamWriter(Path.Combine(dir, "padlink-gui.log"), append: false) { AutoFlush = true };
    }

    private static void StyleButton(Button button, string text, int x, Action onClick)
    {
        button.Text = text;
        button.Location = new Point(16 + x, 12);
        button.Size = new Size(88, 32);
        button.FlatStyle = FlatStyle.System;
        button.Click += (_, _) => onClick();
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
        _sessionGrid.BackgroundColor = Color.White;
        _sessionGrid.BorderStyle = BorderStyle.None;
        _sessionGrid.EnableHeadersVisualStyles = false;
        _sessionGrid.ColumnHeadersDefaultCellStyle.BackColor = Color.FromArgb(248, 248, 248);
        _sessionGrid.ColumnHeadersDefaultCellStyle.ForeColor = Color.FromArgb(80, 80, 80);
        _sessionGrid.ColumnHeadersHeightSizeMode = DataGridViewColumnHeadersHeightSizeMode.DisableResizing;
        _sessionGrid.ColumnHeadersHeight = 30;
        _sessionGrid.RowTemplate.Height = 28;

        _sessionGrid.Columns.AddRange(
            new DataGridViewTextBoxColumn { Name = "address", HeaderText = "地址", FillWeight = 30 },
            new DataGridViewTextBoxColumn { Name = "pad", HeaderText = "手柄", FillWeight = 15 },
            new DataGridViewTextBoxColumn { Name = "player", HeaderText = "玩家", FillWeight = 10 },
            new DataGridViewTextBoxColumn { Name = "frames", HeaderText = "已收帧", FillWeight = 15 },
            new DataGridViewTextBoxColumn { Name = "loss", HeaderText = "丢包", FillWeight = 10 },
            new DataGridViewTextBoxColumn { Name = "idle", HeaderText = "空闲", FillWeight = 10 },
            new DataGridViewTextBoxColumn { Name = "state", HeaderText = "状态", FillWeight = 10 });
    }

    // ------------------------------------------------------------------ 服务控制

    private void StartServer()
    {
        if (_host is not null) return;

        try
        {
            var backend = new ViGEmBackend();
            _host = new PadLinkHost(backend, ProtocolConstants.UdpPort, ProtocolConstants.IdleSessionTimeout);
            _host.Start();
        }
        catch (Exception ex)
        {
            _host?.Dispose();
            _host = null;

            // 不弹 MessageBox：模态框会把界面卡住等人点，而完整堆栈日志里已经有了。
            Console.WriteLine($"启动失败：{ex}");
            _statusLabel.Text = $"启动失败：{ex.Message}";
            _statusLabel.ForeColor = Color.FromArgb(180, 60, 40);
            return;
        }

        _startButton.Enabled = false;
        _stopButton.Enabled = true;
        _statusLabel.ForeColor = Color.FromArgb(90, 90, 90);
        _refreshTimer.Start();
        RefreshSessions();
    }

    private void StopServer()
    {
        _refreshTimer.Stop();

        if (_host is null) return;

        _host.Dispose();
        _host = null;

        _sessionGrid.Rows.Clear();
        _startButton.Enabled = true;
        _stopButton.Enabled = false;
        _statusLabel.Text = "已停止";
    }

    // ------------------------------------------------------------------ 状态刷新

    private void RefreshSessions()
    {
        if (_host is null)
        {
            _statusLabel.Text = "未启动";
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
            row.DefaultCellStyle.ForeColor = session.Stale
                ? Color.FromArgb(160, 120, 0)
                : Color.FromArgb(30, 30, 30);
        }

        var head = $"监听中 · {_host.BackendName} · {_localAddress}:{_host.UdpPort} · ";
        _statusLabel.Text = sessions.Count == 0
            ? head + "等待手机连接"
            : head + $"{sessions.Count} 个手柄";
    }
}
