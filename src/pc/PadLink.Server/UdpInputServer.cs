using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using PadLink.Core;
using PadLink.Core.Protocol;
using PadLink.Server.Backends;

namespace PadLink.Server;

/// <summary>
/// UDP 输入通道：一个 socket 同时处理发现探测和输入帧（protocol.md §1）。
/// 发现探测是文本 <c>PADLINK?1</c>，输入帧以二进制 <c>0x50 0x4C</c> 开头，靠首字节区分。
/// </summary>
public sealed class UdpInputServer : IDisposable
{
    private readonly Socket _socket;
    private readonly SessionRegistry _registry;
    private readonly bool _verbose;

    public UdpInputServer(int port, SessionRegistry registry, bool verbose = true)
    {
        _registry = registry;
        _verbose = verbose;

        _socket = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, ProtocolType.Udp);
        _socket.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
        _socket.Bind(new IPEndPoint(IPAddress.Any, port));
    }

    public async Task RunAsync(CancellationToken cancellationToken)
    {
        var buffer = new byte[2048];

        while (!cancellationToken.IsCancellationRequested)
        {
            SocketReceiveFromResult result;
            try
            {
                result = await _socket.ReceiveFromAsync(
                    buffer, SocketFlags.None, new IPEndPoint(IPAddress.Any, 0), cancellationToken);
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (SocketException ex)
            {
                Console.WriteLine($"UDP 接收错误：{ex.SocketErrorCode} {ex.Message}");
                continue;
            }

            Handle(buffer.AsSpan(0, result.ReceivedBytes), (IPEndPoint)result.RemoteEndPoint);
        }
    }

    private void Handle(ReadOnlySpan<byte> payload, IPEndPoint remote)
    {
        if (payload.SequenceEqual(Encoding.ASCII.GetBytes(ProtocolConstants.DiscoverProbe)))
        {
            ReplyAnnounce(remote);
            return;
        }

        if (!InputFrame.TryDecode(payload, out var frame, out var error))
        {
            if (_verbose)
                Console.WriteLine($"丢弃 {remote} 的 {payload.Length} 字节包：{error}");
            return;
        }

        try
        {
            _registry.GetOrCreate(remote, frame).OnFrame(frame);
        }
        catch (VirtualControllerException ex)
        {
            Console.WriteLine($"创建虚拟手柄失败：{ex.Message}");
        }
    }

    private void ReplyAnnounce(IPEndPoint remote)
    {
        var payload = JsonSerializer.Serialize(new
        {
            type = "announce",
            v = ProtocolConstants.Version,
            name = Environment.MachineName,
            tcp_port = ProtocolConstants.ControlPort,
            udp_port = ProtocolConstants.UdpPort,
            auth = "none",
            players_max = ProtocolConstants.MaxPlayers,
        });

        var bytes = Encoding.UTF8.GetBytes(ProtocolConstants.AnnouncePrefix + payload);

        try
        {
            _socket.SendTo(bytes, remote);
            if (_verbose) Console.WriteLine($"回应发现请求 → {remote}");
        }
        catch (SocketException ex)
        {
            Console.WriteLine($"回应发现请求失败：{ex.Message}");
        }
    }

    public void Dispose() => _socket.Dispose();
}
