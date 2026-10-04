package com.padlink.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.padlink.app.input.PadInputState
import com.padlink.app.transport.FramePump
import com.padlink.app.transport.PumpStats
import com.padlink.app.transport.TcpTransport
import com.padlink.app.transport.UdpTransport
import com.padlink.core.ProtocolConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TransportMode { ADB, WIFI }

sealed interface LinkStatus {
    data object Idle : LinkStatus
    data object Connecting : LinkStatus
    data class Connected(val describe: String) : LinkStatus
    data class Failed(val message: String) : LinkStatus
}

/**
 * 连接与发送的总控。界面只管改 [input] 然后调 [push]，其余（节拍、编码、传输）都在这下面。
 */
class PadLinkController(private val scope: CoroutineScope) {

    val input = PadInputState()

    var mode by mutableStateOf(TransportMode.ADB)
    var host by mutableStateOf(DEFAULT_WIFI_HOST)

    var status by mutableStateOf<LinkStatus>(LinkStatus.Idle)
        private set

    var stats by mutableStateOf(PumpStats())
        private set

    private var pump: FramePump? = null
    private var statsJob: Job? = null

    val isConnected: Boolean get() = status is LinkStatus.Connected

    fun connect() {
        disconnect()
        status = LinkStatus.Connecting

        scope.launch {
            // 连 socket 是阻塞的，扔到 IO 线程去，别卡住界面。
            val attempt = withContext(Dispatchers.IO) {
                runCatching {
                    val transport = when (mode) {
                        // ADB 模式下手机连的是自己的 127.0.0.1：PC 端用 adb reverse
                        // 把设备上的这个端口引到自己身上（protocol.md §1.1）。
                        TransportMode.ADB -> TcpTransport(
                            host = ADB_LOOPBACK,
                            port = ProtocolConstants.CONTROL_PORT,
                        )

                        TransportMode.WIFI -> UdpTransport(host, ProtocolConstants.UDP_PORT)
                    }
                    FramePump(transport).also { it.start() }
                }
            }

            attempt
                .onSuccess { created ->
                    pump = created
                    status = LinkStatus.Connected(created.describe)
                    statsJob = scope.launch { created.stats.collect { stats = it } }
                }
                .onFailure { failure ->
                    status = LinkStatus.Failed(failure.message ?: failure.javaClass.simpleName)
                }
        }
    }

    fun disconnect() {
        statsJob?.cancel()
        statsJob = null
        pump?.stop()
        pump = null
        status = LinkStatus.Idle
        stats = PumpStats()
    }

    /** 界面改过 [input] 之后调一次，把最新状态交给发送循环。 */
    fun push() {
        pump?.pending = input.toFrame()
    }

    fun setButton(bit: Int, pressed: Boolean) {
        input.buttons = if (pressed) input.buttons or bit else input.buttons and bit.inv()
        push()
    }

    companion object {
        /** ADB 模式固定连回环地址——隧道那头就是 PC。 */
        const val ADB_LOOPBACK = "127.0.0.1"

        const val DEFAULT_WIFI_HOST = "192.168.1.100"
    }
}
