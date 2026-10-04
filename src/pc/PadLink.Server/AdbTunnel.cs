using System.Diagnostics;

namespace PadLink.Server;

/// <summary>
/// adb reverse 隧道管理，见 <c>protocol/protocol.md</c> §1.1。
/// <para>
/// 方向别记反：<c>adb reverse tcp:A tcp:B</c> 的语义是
/// "<b>设备</b>上连 <c>localhost:A</c> → 转到<b>主机</b>的 B"。
/// 所以 PC 做 server 监听 B，手机做 client 连自己的 <c>127.0.0.1:A</c>，数据就打到 PC 上了。
/// </para>
/// </summary>
public static class AdbTunnel
{
    private static readonly string[] CandidateCommands =
    [
        "adb",                                  // 走 PATH
        @"C:\adb\adb.exe",
        @"C:\platform-tools\adb.exe",
        @"C:\Users\Public\adb.exe",
    ];

    /// <summary>找一个能用的 adb；都没有就返回 null。</summary>
    public static string? FindAdb()
    {
        foreach (var candidate in CandidateCommands)
        {
            var (ok, _) = Run(candidate, "version", 4000);
            if (ok) return candidate;
        }

        return null;
    }

    /// <summary>已连接（且已授权）的设备序列号。空列表表示没插线或没授权。</summary>
    public static IReadOnlyList<string> ListDevices(string adb)
    {
        var (ok, output) = Run(adb, "devices", 5000);
        if (!ok) return [];

        return output
            .Split('\n', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
            .Skip(1)                                        // 第一行是 "List of devices attached"
            .Select(line => line.Split('\t', ' ', StringSplitOptions.RemoveEmptyEntries))
            .Where(parts => parts.Length >= 2 && parts[1] is "device")
            .Select(parts => parts[0])
            .ToList();
    }

    /// <summary>建立隧道。设备上连 localhost:port 就会打到主机的同一个端口。</summary>
    public static (bool Ok, string Message) EstablishReverse(string adb, int port)
        => RunChecked(adb, $"reverse tcp:{port} tcp:{port}");

    public static (bool Ok, string Message) RemoveReverse(string adb, int port)
        => RunChecked(adb, $"reverse --remove tcp:{port}");

    public static (bool Ok, string Message) ListReverses(string adb)
        => RunChecked(adb, "reverse --list");

    private static (bool Ok, string Message) RunChecked(string adb, string arguments)
    {
        var (ok, output) = Run(adb, arguments, 8000);
        var message = output.Trim();
        if (string.IsNullOrEmpty(message)) message = ok ? "ok" : "（无输出）";
        return (ok, message);
    }

    private static (bool Ok, string Output) Run(string fileName, string arguments, int timeoutMs)
    {
        try
        {
            using var process = Process.Start(new ProcessStartInfo(fileName, arguments)
            {
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true,
            });

            if (process is null) return (false, "无法启动进程");

            var stdout = process.StandardOutput.ReadToEnd();
            var stderr = process.StandardError.ReadToEnd();

            if (!process.WaitForExit(timeoutMs))
            {
                try { process.Kill(entireProcessTree: true); } catch (InvalidOperationException) { }
                return (false, "超时");
            }

            var combined = string.IsNullOrWhiteSpace(stderr) ? stdout : stdout + stderr;
            return (process.ExitCode == 0, combined);
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }
}
