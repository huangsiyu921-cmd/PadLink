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

PadLinkHost host;
try
{
    host = new PadLinkHost(backend, udpPort, idleTimeout);
}
catch (SocketException ex)
{
    Console.WriteLine($"UDP 端口 {udpPort} 绑定失败：{ex.Message}");
    Console.WriteLine("换一个端口：dotnet run --project PadLink.Server -- --port 12345");
    backend.Dispose();
    return 1;
}

using (host)
{
    host.Start();

    foreach (var address in LocalAddresses())
        Console.WriteLine($"  本机地址 {address}:{udpPort}");
    Console.WriteLine("等待数据帧…（Ctrl+C 退出）");

    using var cts = new CancellationTokenSource();
    Console.CancelKeyPress += (_, e) =>
    {
        e.Cancel = true;
        cts.Cancel();
    };

    try
    {
        await Task.Delay(Timeout.Infinite, cts.Token);
    }
    catch (OperationCanceledException)
    {
        // Ctrl+C
    }

    foreach (var session in host.Sessions)
    {
        var total = session.FramesReceived + session.FramesLost;
        var loss = total == 0 ? 0 : 100.0 * session.FramesLost / total;
        Console.WriteLine(
            $"{session.Remote}  收到 {session.FramesReceived} 帧，丢 {session.FramesLost} 帧（{loss:F2}%）");
    }

    Console.WriteLine("正在停止…");
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
