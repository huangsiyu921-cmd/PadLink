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

    /// <summary>
    /// 回收长时间没数据的会话，销毁其虚拟手柄。
    /// <para>
    /// 为什么不能靠 <see cref="PollFailSafe"/> 顺手做：fail-safe 的 300ms 太短，那时候只能归零输入，
    /// 一旦销毁设备游戏就会重排槽位。所以回收用的是几分钟量级的独立阈值。
    /// </para>
    /// </summary>
    /// <returns>回收掉的会话数。</returns>
    public int ReapIdleSessions(TimeSpan idle)
    {
        var victims = _sessions
            .Where(pair => pair.Value.IdleFor > idle)
            .Select(pair => pair.Key)
            .ToList();

        foreach (var key in victims)
        {
            var session = _sessions[key];
            _sessions.Remove(key);

            var idleSeconds = session.IdleFor.TotalSeconds;
            Console.WriteLine(
                $"  回收空闲会话 {key}（{idleSeconds:F0}s 无数据），销毁虚拟手柄 P{session.Controller.Player}");
            session.Dispose();
        }

        return victims.Count;
    }

    public void Dispose()
    {
        foreach (var session in _sessions.Values) session.Dispose();
        _sessions.Clear();
    }
}
