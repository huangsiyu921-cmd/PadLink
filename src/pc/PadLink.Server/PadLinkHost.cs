using PadLink.Core;
using PadLink.Core.Protocol;

namespace PadLink.Server;

/// <summary>
/// 服务端宿主：把 UDP 接收、会话管理、fail-safe、空闲回收打包成一个能启动/停止的对象，
/// 让 CLI（<c>Program.cs</c>）和 GUI（<c>PadLink.Gui</c>）共用同一套逻辑，不复制一份。
/// <para>生命周期：<c>new</c> → <see cref="Start"/> → <see cref="Dispose"/>。构造时就会绑定 UDP 端口，
/// 所以同一个实例不能"停了再启"，重启请新建。</para>
/// <para>它<b>拥有</b>传进来的 backend，Dispose 时一并释放。</para>
/// </summary>
public sealed class PadLinkHost : IDisposable
{
    private readonly IVirtualControllerBackend _backend;
    private readonly UdpInputServer _server;
    private readonly SessionRegistry _registry;
    private readonly TimeSpan _idleTimeout;
    private readonly int _reapEveryNTicks;

    private CancellationTokenSource? _cts;
    private Task? _runTask;
    private Task? _maintainTask;
    private bool _disposed;

    public PadLinkHost(IVirtualControllerBackend backend, int udpPort, TimeSpan idleTimeout)
    {
        _backend = backend;
        _idleTimeout = idleTimeout;
        UdpPort = udpPort;

        _registry = new SessionRegistry(backend);
        _server = new UdpInputServer(udpPort, _registry);

        // 扫描周期取阈值的 1/4，夹在 200ms 与 5s 之间：既不太迟钝，也不空转。
        var reapInterval = TimeSpan.FromMilliseconds(
            Math.Clamp(idleTimeout.TotalMilliseconds / 4, 200, 5000));
        _reapEveryNTicks = Math.Max(1, (int)(reapInterval.TotalMilliseconds / 50));
    }

    public int UdpPort { get; }

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

        Console.WriteLine($"PadLink 服务端 · 后端 {BackendName}");
        Console.WriteLine($"UDP :{UdpPort}   fail-safe {ProtocolConstants.FailSafeTimeout.TotalMilliseconds:F0}ms（只归零）   "
                          + $"空闲 {_idleTimeout.TotalSeconds:F0}s 回收会话");
        Console.WriteLine($"节拍 {ProtocolConstants.ActiveRateHz}Hz / 保活 {ProtocolConstants.IdleRateHz}Hz");

        _runTask = Task.Run(() => _server.RunAsync(token), token);
        _maintainTask = Task.Run(async () =>
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
        }, token);
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
                Task.WhenAll(_runTask ?? Task.CompletedTask, _maintainTask ?? Task.CompletedTask)
                    .GetAwaiter().GetResult();
            }
            catch (OperationCanceledException)
            {
                // 正常停止
            }

            _cts.Dispose();
            _cts = null;
        }

        _registry.Dispose();        // 销毁所有虚拟手柄
        _server.Dispose();
        _backend.Dispose();
    }
}
