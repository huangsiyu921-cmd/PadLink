package com.padlink.app.transport

import android.os.Build
import com.padlink.core.InputFrame
import com.padlink.core.ProtocolConstants
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * ADB 模式：PC 做 TCP server，手机做 client 连自己的 `127.0.0.1`。
 * 隧道由 PC 端执行 `adb reverse` 建立，见 protocol.md §1.1。
 *
 * 连 `127.0.0.1` 在 Android 上**不需要任何权限**，这也是这条路线的便宜之处。
 */
class TcpTransport(
    host: String,
    port: Int = ProtocolConstants.CONTROL_PORT,
    controller: String = "xbox360",
    players: Int = 1,
) : InputTransport {

    private val socket = Socket()
    private lateinit var out: OutputStream
    private val buffer = ByteArray(ProtocolConstants.FRAME_SIZE)

    override val describe: String = "ADB $host:$port"

    init {
        try {
            // 协议 §7：不关 Nagle，小包会被攒到 ~40ms 才发，手感直接完蛋。
            socket.tcpNoDelay = true
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS
            socket.connect(InetSocketAddress(host, port), HANDSHAKE_TIMEOUT_MS)

            out = socket.getOutputStream()
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

            // 协议 §4.1 的握手。服务端不做认证时 auth 为 none。
            sendLine(
                """{"v":${ProtocolConstants.VERSION},"type":"hello","device":"${deviceName()}",""" +
                    """"codecs":["binary"],"controller":"$controller","players":$players}"""
            )
            readLine(reader)                        // welcome
            sendLine("""{"v":${ProtocolConstants.VERSION},"type":"start"}""")
            readLine(reader)                        // started

            socket.soTimeout = 0                    // 握手之后不再需要读超时
        } catch (e: IOException) {
            runCatching { socket.close() }
            throw TransportException("连不上 PC（$host:$port）：${e.message}", e)
        }
    }

    override fun send(frame: InputFrame) {
        frame.encodeInto(buffer)
        try {
            out.write(buffer, 0, ProtocolConstants.FRAME_SIZE)
        } catch (e: IOException) {
            throw TransportException("TCP 发送失败：${e.message}", e)
        }
    }

    override fun close() {
        // 协议 §4.2：正常退出打个招呼。失败也无所谓，连接马上就要关了。
        runCatching { sendLine("""{"v":${ProtocolConstants.VERSION},"type":"bye"}""") }
        runCatching { socket.close() }
    }

    private fun sendLine(line: String) {
        out.write(line.toByteArray(Charsets.UTF_8))
        out.write('\n'.code)
        out.flush()
    }

    private fun readLine(reader: BufferedReader): String =
        reader.readLine() ?: throw TransportException("PC 在握手过程中关闭了连接")

    private fun deviceName(): String =
        runCatching { "${Build.MANUFACTURER} ${Build.MODEL}".trim() }.getOrDefault("android")

    private companion object {
        const val HANDSHAKE_TIMEOUT_MS = 5000
    }
}
