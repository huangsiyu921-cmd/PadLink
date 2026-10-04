using System.Net;
using System.Net.Sockets;
using PadLink.Core.Protocol;
using PadLink.Server;
using PadLink.Server.Backends;

try { Console.OutputEncoding = System.Text.Encoding.UTF8; } catch (IOException) { /* 输出被重定向时忽略 */ }

var udpPort = ProtocolConstants.UdpPort;
if (args.Length > 0 && int.TryParse(args[0], out var parsedPort)) udpPort = parsedPort;

using var backend = new ConsoleBackend();
using var registry = new SessionRegistry(backend);

UdpInputServer server;
try
{
    server = new UdpInputServer(udpPort, registry);
}
catch (SocketException ex)
{
    Console.WriteLine($"UDP 端口 {udpPort} 绑定失败：{ex.Message}");
    Console.WriteLine("换一个端口：dotnet run --project PadLink.Server -- 12345");
    return 1;
}

using (server)
{
    Console.WriteLine($"PadLink 服务端 · 后端 {backend.Name}");
    Console.WriteLine($"UDP :{udpPort}   fail-safe {ProtocolConstants.FailSafeTimeout.TotalMilliseconds:F0}ms   "
                      + $"节拍 {ProtocolConstants.ActiveRateHz}Hz / 保活 {ProtocolConstants.IdleRateHz}Hz");
    foreach (var address in LocalAddresses())
        Console.WriteLine($"  本机地址 {address}:{udpPort}");
    Console.WriteLine("等待数据帧…（Ctrl+C 退出）");

    using var cts = new CancellationTokenSource();
    Console.CancelKeyPress += (_, e) =>
    {
        e.Cancel = true;
        cts.Cancel();
    };

    var failSafeLoop = Task.Run(async () =>
    {
        using var timer = new PeriodicTimer(TimeSpan.FromMilliseconds(50));
        try
        {
            while (await timer.WaitForNextTickAsync(cts.Token))
                registry.PollFailSafe();
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

    await failSafeLoop;

    foreach (var session in registry.Sessions)
    {
        var total = session.FramesReceived + session.FramesLost;
        var loss = total == 0 ? 0 : 100.0 * session.FramesLost / total;
        var seconds = session.FramesReceived / (double)ProtocolConstants.ActiveRateHz;
        Console.WriteLine(
            $"{session.Remote}  收到 {session.FramesReceived} 帧，丢 {session.FramesLost} 帧（{loss:F2}%），约 {seconds:F1} 秒");
    }
}

Console.WriteLine("已退出。");
return 0;

static IEnumerable<IPAddress> LocalAddresses()
    => Dns.GetHostAddresses(Dns.GetHostName())
        .Where(a => a.AddressFamily == AddressFamily.InterNetwork)
        .Distinct();
