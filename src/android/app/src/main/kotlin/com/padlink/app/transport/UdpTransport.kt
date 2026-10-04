package com.padlink.app.transport

import com.padlink.core.InputFrame
import com.padlink.core.ProtocolConstants
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

/**
 * WiFi 模式：单播 UDP 到 PC。
 *
 * 整个会话共用一个 socket，**源端口必须稳定**——PC 靠 (IP, 源端口) 认出是哪个会话
 * （protocol.md §1）。所以这里绝不中途重建 socket。
 */
class UdpTransport(
    host: String,
    port: Int = ProtocolConstants.UDP_PORT,
) : InputTransport {

    private val socket = DatagramSocket()
    private val target = InetSocketAddress(host, port)

    /** 复用同一块缓冲区：60Hz 下每帧新建数组会白白制造 GC 压力。 */
    private val buffer = ByteArray(ProtocolConstants.FRAME_SIZE)

    override val describe: String = "WiFi $host:$port"

    override fun send(frame: InputFrame) {
        frame.encodeInto(buffer)
        try {
            socket.send(DatagramPacket(buffer, ProtocolConstants.FRAME_SIZE, target))
        } catch (e: IOException) {
            throw TransportException("UDP 发送失败：${e.message}", e)
        }
    }

    override fun close() {
        socket.close()
    }
}
