using System.Net;
using System.Net.Sockets;
using PadLink.Backends.ViGEm;
using PadLink.Core;
using PadLink.Core.Protocol;
using PadLink.Server;
using PadLink.Server.Backends;

try { Console.OutputEncoding = System.Text.Encoding.UTF8; } catch (IOException) { /* 输出被重定向时忽略 */ }

// --verify：自检虚拟手柄后端（创建手柄 → 提交状态 → XInput 回读比对），不启动网络服务。
if (args.Contains("--verify"))
    return SelfTest.Run();

// --probe：只读 XInput，看系统里有没有人在发输入（用来验证完整链路）。
if (args.Contains("--probe"))
    return SelfTest.Probe(int.TryParse(OptionValue(args, "--seconds"), out var probeSeconds) ? probeSeconds : 5);

var backendName = OptionValue(args, "--backend") ?? "vigem";
var udpPort = int.TryParse(OptionValue(args, "--port"), out var parsedPort)
    ? parsedPort
    : ProtocolConstants.UdpPort;

// 空闲回收阈值。默认 5 分钟；调试时可以调小（例如 --idle-seconds 3）来验证回收逻辑。
var idleTimeout = double.TryParse(OptionValue(args, "--idle-seconds"), out var idleSeconds) && idleSeconds > 0
    ? TimeSpan.FromSeconds(idleSeconds)
    : ProtocolConstants.IdleSessionTimeout;

// 扫描周期取阈值的 1/4，夹在 200ms 与 5s 之间：既不会太迟钝，也不会空转。
var reapInterval = TimeSpan.FromMilliseconds(
    Math.Clamp(idleTimeout.TotalMilliseconds / 4, 200, 5000));
var reapEveryNTicks = Math.Max(1, (int)(reapInterval.TotalMilliseconds / 50));

IVirtualControllerBackend backend;
try
{
    backend = backendName switch
    {
        "vigem" => new ViGEmBackend(),
        "console" => new ConsoleBackend(),
        _ => throw new ArgumentException($"未知后端「{backendName}」，可选 vigem / console"),
    };
}
catch (Exception ex)
{
    Console.WriteLine($"后端 {backendName} 初始化失败：{ex.Message}");
    if (backendName == "vigem")
        Console.WriteLine("需要已安装 ViGEmBus 驱动；也可先用 --backend console 只验证链路。");
    return 1;
}

using (backend)
using (var registry = new SessionRegistry(backend))
{
    UdpInputServer server;
    try
    {
        server = new UdpInputServer(udpPort, registry);
    }
    catch (SocketException ex)
    {
        Console.WriteLine($"UDP 端口 {udpPort} 绑定失败：{ex.Message}");
        Console.WriteLine("换一个端口：dotnet run --project PadLink.Server -- --port 12345");
        return 1;
    }

    using (server)
    {
        Console.WriteLine($"PadLink 服务端 · 后端 {backend.Name}");
        Console.WriteLine($"UDP :{udpPort}   fail-safe {ProtocolConstants.FailSafeTimeout.TotalMilliseconds:F0}ms（只归零）   "
                          + $"空闲 {idleTimeout.TotalSeconds:F0}s 回收会话");
        Console.WriteLine($"节拍 {ProtocolConstants.ActiveRateHz}Hz / 保活 {ProtocolConstants.IdleRateHz}Hz");
        foreach (var address in LocalAddresses())
            Console.WriteLine($"  本机地址 {address}:{udpPort}");
        Console.WriteLine("等待数据帧…（Ctrl+C 退出）");

        using var cts = new CancellationTokenSource();
        Console.CancelKeyPress += (_, e) =>
        {
            e.Cancel = true;
            cts.Cancel();
        };

        var maintainLoop = Task.Run(async () =>
        {
            using var timer = new PeriodicTimer(TimeSpan.FromMilliseconds(50));
            var ticks = 0;
            try
            {
                while (await timer.WaitForNextTickAsync(cts.Token))
                {
                    registry.PollFailSafe();

                    // 定期扫空闲会话并销毁其虚拟手柄（阈值默认 5 分钟，见 ProtocolConstants）。
                    if (++ticks % reapEveryNTicks == 0)
                        registry.ReapIdleSessions(idleTimeout);
                }
            }
            catch (OperationCanceledException)
            {
                // 正常退出
            }
        }, cts.Token);

        try
        {
            await server.RunAsync(cts.Token);
        }
        catch (OperationCanceledException)
        {
            // 正常退出
        }

        await maintainLoop;

        foreach (var session in registry.Sessions)
        {
            var total = session.FramesReceived + session.FramesLost;
            var loss = total == 0 ? 0 : 100.0 * session.FramesLost / total;
            var seconds = session.FramesReceived / (double)ProtocolConstants.ActiveRateHz;
            Console.WriteLine(
                $"{session.Remote}  收到 {session.FramesReceived} 帧，丢 {session.FramesLost} 帧（{loss:F2}%），约 {seconds:F1} 秒");
        }
    }
}

Console.WriteLine("已退出。");
return 0;

static string? OptionValue(string[] args, string name)
{
    var index = Array.IndexOf(args, name);
    return index >= 0 && index + 1 < args.Length ? args[index + 1] : null;
}

static IEnumerable<IPAddress> LocalAddresses()
    => Dns.GetHostAddresses(Dns.GetHostName())
        .Where(a => a.AddressFamily == AddressFamily.InterNetwork)
        .Distinct();
