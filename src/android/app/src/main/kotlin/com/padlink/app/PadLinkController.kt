package com.padlink.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.padlink.app.input.PadInputState
import com.padlink.app.input.TiltSettingsStore
import com.padlink.app.input.TiltSource
import com.padlink.app.transport.FramePump
import com.padlink.app.transport.PumpStats
import com.padlink.app.transport.TcpTransport
import com.padlink.app.transport.UdpTransport
import com.padlink.core.ProtocolConstants
import com.padlink.core.TiltSettings
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
 *
 * 重力转向也在这层：它只是 [input] 的又一个写入者，出了这里发出去的还是普通输入帧。
 */
class PadLinkController(
    private val scope: CoroutineScope,
    private val tilt: TiltSource,
    private val tiltStore: TiltSettingsStore,
) {

    val input = PadInputState()

    /** 重力转向开着时，左摇杆的 X 轴由手机姿态驱动，触摸让位。 */
    var tiltEnabled by mutableStateOf(false)
        private set

    /** 重力转向的手感参数。拖滑杆时实时生效，松手才落盘。 */
    var tiltSettings by mutableStateOf(tiltStore.load())
        private set

    val tiltAvailable: Boolean get() = tilt.isAvailable

    init {
        tilt.settings = tiltSettings
        tilt.onTilt = { value ->
            input.setTiltX(value)
            push()
        }
    }

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

    /**
     * 开关重力转向。打开时顺手以当前姿态定零点，用户不用再单独校准一次。
     *
     * 没有陀螺仪的机器上永远打不开——那种情况界面那边的按钮应该是灰的。
     */
    fun enableTilt(enabled: Boolean) {
        val next = enabled && tilt.isAvailable
        if (next == tiltEnabled) return

        tiltEnabled = next
        input.tiltOwnsLeftX = next

        if (next) {
            tilt.start()
        } else {
            tilt.stop()
            input.setTiltX(0f)   // 别把摇杆留在最后那个位置上
        }
        push()
    }

    /** 以当前姿态为零点。玩到一半换了姿势（躺下、翘腿）之后调一次。 */
    fun recenterTilt() {
        if (tiltEnabled) tilt.recenter()
    }

    /**
     * 改手感参数。拖滑杆的过程中实时生效（[persist] = false），松手时才落盘——
     * 拖一下写几十次盘没必要。
     */
    fun applyTiltSettings(settings: TiltSettings, persist: Boolean) {
        tiltSettings = settings
        tilt.settings = settings
        if (persist) tiltStore.save(settings)
    }

    /** 界面要走了：收连接、关传感器。 */
    fun release() {
        disconnect()
        enableTilt(false)
    }

    companion object {
        /** ADB 模式固定连回环地址——隧道那头就是 PC。 */
        const val ADB_LOOPBACK = "127.0.0.1"

        const val DEFAULT_WIFI_HOST = "192.168.1.100"
    }
}
