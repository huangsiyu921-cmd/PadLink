using System.Net;
using PadLink.Core;
using PadLink.Core.Protocol;

namespace PadLink.Server;

/// <summary>按「源 IP + 源端口」管理手机会话。见 protocol.md §1。</summary>
public sealed class SessionRegistry(IVirtualControllerBackend backend) : IDisposable
{
    private readonly Dictionary<string, PadSession> _sessions = [];

    public IReadOnlyCollection<PadSession> Sessions => _sessions.Values;

    /// <summary>查找会话；不存在则按帧里声明的 player / controller 创建一台虚拟手柄。</summary>
    public PadSession GetOrCreate(IPEndPoint remote, in InputFrame frame)
    {
        var key = $"{remote.Address}:{remote.Port}";
        if (_sessions.TryGetValue(key, out var existing)) return existing;

        if (!backend.Supports(frame.Controller))
            throw new VirtualControllerException($"后端 {backend.Name} 不支持 {frame.Controller.ToWireName()}");

        var controller = backend.Connect(frame.Player, frame.Controller);
        var session = new PadSession(remote, controller);
        _sessions[key] = session;

        Console.WriteLine($"  新建会话 {key} → P{frame.Player} {frame.Controller.ToWireName()}（后端 {backend.Name}）");
        return session;
    }

    public void PollFailSafe()
    {
        foreach (var session in _sessions.Values) session.PollFailSafe();
    }

    /// <summary>移除长时间无数据的会话。手柄销毁会让游戏重排槽位，所以阈值远大于 fail-safe。</summary>
    public void ReapIdleSessions(TimeSpan idle)
    {
        _ = idle;
        _ = _sessions;
        // TODO(P4)：需要 LastSeen 时间戳；当前只在进程退出时统一销毁。
    }

    public void Dispose()
    {
        foreach (var session in _sessions.Values) session.Dispose();
        _sessions.Clear();
    }
}
