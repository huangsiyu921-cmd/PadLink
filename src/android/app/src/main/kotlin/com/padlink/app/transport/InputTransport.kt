package com.padlink.app.transport

import com.padlink.core.InputFrame

/**
 * 输入帧的出口。两种传输模式各一个实现：WiFi 走 UDP，ADB 走 TCP。
 * <para>见 protocol/protocol.md §1。</para>
 */
interface InputTransport : AutoCloseable {
    /** 给界面看的描述，例如 `WiFi 192.168.10.165:42313`。 */
    val describe: String

    /**
     * 发一帧。会被 [FramePump] 以 60Hz 调用，实现里别做重活。
     * @throws TransportException 传输已坏（上层据此显示断线）
     */
    fun send(frame: InputFrame)

    override fun close()
}

class TransportException(message: String, cause: Throwable? = null) : Exception(message, cause)
