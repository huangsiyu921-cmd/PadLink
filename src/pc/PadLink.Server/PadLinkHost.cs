using System.Net.Sockets;
using PadLink.Core;
using PadLink.Core.Protocol;

namespace PadLink.Server;

/// <summary>
/// 服务端宿主：把 UDP/TCP 输入通道、会话管理、fail-safe、空闲回收打包成一个能启动/停止的对象，
/// 让 CLI（<c>Program.cs</c>）和 GUI（<c>PadLink.Gui</c>）共用同一套逻辑，不复制一份。
/// <para>生命周期：<c>new</c> → <see cref="Start"/> → <see cref="Dispose"/>。构造时就会绑定端口，
/// 所以同一个实例不能"停了再启"，重启请新建。</para>
/// <para>它<b>拥有</b>传进来的 backend，Dispose 时一并释放。</para>
/// </summary>
public sealed class PadLinkHost : IDisposable
{
    private readonly IVirtualControllerBackend _backend;
    private readonly UdpInputServer _udpServer;
    private readonly TcpInputServer? _tcpServer;
    private readonly SessionRegistry _registry;
    private readonly TimeSpan _idleTimeout;
    private readonly int _reapEveryNTicks;

    private CancellationTokenSource? _cts;
    private readonly List<Task> _loops = [];
    private string? _adbPath;               // 我们自己建的隧道，退出时要拆掉
    private bool _disposed;

    /// <param name="tcpPort">ADB 模式用的 TCP 端口；传 null 就只用 UDP。</param>
    /// <param name="preferredController">PC 端指定的手柄类型；null 表示跟随手机声明。</param>
    public PadLinkHost(IVirtualControllerBackend backend, int udpPort, TimeSpan idleTimeout,
        int? tcpPort = ProtocolConstants.ControlPort, ControllerType? preferredController = null)
    {
        _backend = backend;
        _idleTimeout = idleTimeout;
        UdpPort = udpPort;
        PreferredController = preferredController;

        _registry = new SessionRegistry(backend, preferredController);
        _udpServer = new UdpInputServer(udpPort, _registry);

        // TCP 绑不上（端口被占之类）不该把整个服务拖死：记一笔，UDP 照常跑。
        if (tcpPort is { } port)
        {
            try
            {
                _tcpServer = new TcpInputServer(port, _registry);
                TcpPort = port;
            }
            catch (SocketException ex)
            {
                Console.WriteLine($"TCP 端口 {port} 绑定失败：{ex.Message}（ADB 模式不可用）");
                _tcpServer = null;
            }
        }

        // 扫描周期取阈值的 1/4，夹在 200ms 与 5s 之间：既不太迟钝，也不空转。
        var reapInterval = TimeSpan.FromMilliseconds(
            Math.Clamp(idleTimeout.TotalMilliseconds / 4, 200, 5000));
        _reapEveryNTicks = Math.Max(1, (int)(reapInterval.TotalMilliseconds / 50));
    }

    public int UdpPort { get; }

    /// <summary>PC 端指定的手柄类型；null 表示跟随手机声明。</summary>
    public ControllerType? PreferredController { get; }

    /// <summary>ADB 模式用的 TCP 端口；为 0 表示没启用。</summary>
    public int TcpPort { get; }

    public bool AdbModeAvailable => _tcpServer is not null;

    public string BackendName => _backend.Name;

    public bool IsRunning => _cts is not null;

    /// <summary>当前在线的会话。GUI 靠它渲染"连接情况"。</summary>
    public IReadOnlyCollection<PadSession> Sessions => _registry.Sessions;

    public void Start()
    {
        ObjectDisposedException.ThrowIf(_disposed, this);
        if (_cts is not null) return;

        _cts = new CancellationTokenSource();
        var token = _cts.Token;

        var controllerLabel = PreferredController is { } type ? type.ToWireName() : "跟随手机";
        Console.WriteLine($"PadLink 服务端 · 后端 {BackendName} · 手柄 {controllerLabel}");
        Console.WriteLine($"UDP :{UdpPort}（WiFi）"
                          + (_tcpServer is null ? "   TCP：未启用" : $"   TCP :{TcpPort}（ADB）")
                          + $"   fail-safe {ProtocolConstants.FailSafeTimeout.TotalMilliseconds:F0}ms（只归零）"
                          + $"   空闲 {_idleTimeout.TotalSeconds:F0}s 回收会话");

        _loops.Add(Task.Run(() => _udpServer.RunAsync(token), token));

        if (_tcpServer is not null)
            _loops.Add(Task.Run(() => _tcpServer.RunAsync(token), token));

        _loops.Add(Task.Run(async () =>
        {
            using var timer = new PeriodicTimer(TimeSpan.FromMilliseconds(50));
            var ticks = 0;
            try
            {
                while (await timer.WaitForNextTickAsync(token))
                {
                    _registry.PollFailSafe();

                    if (++ticks % _reapEveryNTicks == 0)
                        _registry.ReapIdleSessions(_idleTimeout);
                }
            }
            catch (OperationCanceledException)
            {
                // 正常停止
            }
        }, token));
    }

    /// <summary>
    /// 建立 ADB 隧道：设备上连 <c>localhost:TcpPort</c> 就会打到本机。
    /// 需要 PC 上有 adb，且手机已插线并授权。
    /// </summary>
    public (bool Ok, string Message) TryEstablishAdbTunnel()
    {
        if (_tcpServer is null) return (false, "TCP 通道未启用");

        var adb = AdbTunnel.FindAdb();
        if (adb is null) return (false, "没找到 adb 可执行文件");

        var devices = AdbTunnel.ListDevices(adb);
        if (devices.Count == 0) return (false, "没有已连接并授权的设备");

        var (ok, message) = AdbTunnel.EstablishReverse(adb, TcpPort);
        if (ok) _adbPath = adb;                 // 记住是谁建的，退出时要拆
        return (ok, ok ? $"隧道已建立（{devices[0]} → localhost:{TcpPort}）" : message);
    }

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;

        if (_cts is not null)
        {
            _cts.Cancel();
            try
            {
                Task.WhenAll(_loops).GetAwaiter().GetResult();
            }
            catch (OperationCanceledException)
            {
                // 正常停止
            }

            _cts.Dispose();
            _cts = null;
        }

        _registry.Dispose();        // 销毁所有虚拟手柄
        _udpServer.Dispose();
        _tcpServer?.Dispose();

        // 拆掉我们自己建的隧道——别在用户机器上留一条 adb 端口转发。
        if (_adbPath is not null)
        {
            var (ok, message) = AdbTunnel.RemoveReverse(_adbPath, TcpPort);
            Console.WriteLine(ok ? "ADB 隧道已拆除" : $"ADB 隧道拆除失败：{message}");
            _adbPath = null;
        }

        _backend.Dispose();
    }
}
