using System.Net;
using PadLink.Core;
using PadLink.Core.Protocol;

namespace PadLink.Server;

/// <summary>管理手机会话。</summary>
/// <param name="preferred">
/// PC 端指定的手柄类型。传 null 就跟随手机在帧里声明的类型。
/// <para>
/// 之所以让 PC 端有话语权：虚拟手柄最终是"这台电脑上的一个设备"，
/// 想让它以 PS4 还是 Xbox 身份出现，取决于你在玩的游戏和 Steam 设置，跟手机没关系。
/// </para>
/// </param>
public sealed class SessionRegistry(
    IVirtualControllerBackend backend,
    ControllerType? preferred = null) : IDisposable
{
    /// <summary>
    /// 按 <b>player</b> 索引，<b>不是</b>按来源地址。
    /// <para>
    /// 一台手柄 = 一个虚拟设备。手机重连或换了源端口时只接管已有会话，
    /// 绝不新建——否则 XInput 的 4 个槽位很快会被历史连接占满，而且旧设备还会一直挂着。
    /// </para>
    /// </summary>
    private readonly Dictionary<byte, PadSession> _sessions = [];

    public IReadOnlyCollection<PadSession> Sessions => _sessions.Values;

    /// <summary>查找会话；不存在则按帧里声明的 player / controller 创建一台虚拟手柄。</summary>
    public PadSession GetOrCreate(IPEndPoint remote, in InputFrame frame)
    {
        if (_sessions.TryGetValue(frame.Player, out var existing))
        {
            if (!existing.Remote.Equals(remote))
            {
                Console.WriteLine($"  P{frame.Player} 来源变更 {existing.Remote} → {remote}，沿用已有虚拟手柄");
                existing.Rebind(remote);
            }

            return existing;
        }

        // PC 端指定了类型就用它；后端做不出来再退回手机声明的。
        var requested = preferred is { } forced && backend.Supports(forced) ? forced : frame.Controller;

        if (!backend.Supports(requested))
            throw new VirtualControllerException($"后端 {backend.Name} 不支持 {requested.ToWireName()}");

        var controller = backend.Connect(frame.Player, requested);
        var session = new PadSession(remote, controller);
        _sessions[frame.Player] = session;

        var source = preferred is { } p && requested == p ? "PC 指定" : "手机声明";
        Console.WriteLine(
            $"  新建会话 P{frame.Player} {requested.ToWireName()}（{source}）← {remote}（后端 {backend.Name}）");
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

        foreach (var player in victims)
        {
            var session = _sessions[player];
            _sessions.Remove(player);

            Console.WriteLine(
                $"  回收空闲会话 P{player}（{session.IdleFor.TotalSeconds:F0}s 无数据），销毁虚拟手柄");
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
