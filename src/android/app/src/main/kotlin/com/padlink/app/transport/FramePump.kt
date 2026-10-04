package com.padlink.app.transport

import android.os.SystemClock
import com.padlink.core.InputFrame
import com.padlink.core.ProtocolConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 固定节拍发送器（protocol.md §7）。
 *
 * 触摸事件**不直接触发发包**：UI 只把最新的全量状态写进 [pending]，
 * 由这里的循环按 60Hz 发快照。这才是 UDP 能用的前提——每包都是完整状态，
 * 丢一包下一包自然覆盖；如果改成"有变化才发"的增量，丢一包就永久错位。
 */
class FramePump(
    val transport: InputTransport,
    private val rateHz: Int = ProtocolConstants.ACTIVE_RATE_HZ,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /**
     * 最新状态。整帧替换，所以不需要锁——UI 线程写、发送线程读。
     * 触摸期间每帧重建一个 InputFrame 是廉价的（data class，没有数组）。
     */
    @Volatile
    var pending: InputFrame = InputFrame.neutral()

    private val _stats = MutableStateFlow(PumpStats())
    val stats: StateFlow<PumpStats> = _stats.asStateFlow()

    val describe: String get() = transport.describe

    val isRunning: Boolean get() = job?.isActive == true

    fun start() {
        if (job != null) return
        job = scope.launch { loop() }
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { transport.close() }
    }

    private suspend fun loop() {
        val intervalNanos = 1_000_000_000L / rateHz
        var nextTick = System.nanoTime()
        var sequence = 0L
        var sent = 0L

        try {
            while (true) {
                val frame = pending.copy(
                    sequence = sequence++ and 0xFFFFFFFFL,
                    timestampMs = SystemClock.uptimeMillis() and 0xFFFFFFFFL,
                )
                transport.send(frame)
                sent++

                // 约每 0.25 秒刷一次统计，让界面上的帧数看着在动。
                if (sent % 15L == 0L) _stats.value = PumpStats(framesSent = sent)

                nextTick += intervalNanos
                val sleepNanos = nextTick - System.nanoTime()
                if (sleepNanos > 0) {
                    delay(sleepNanos / 1_000_000)
                } else {
                    // 落后太多就重新对齐，别让欠账越滚越多；delay 一下避免空转。
                    nextTick = System.nanoTime()
                    delay(1)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 传输坏了（拔线、PC 关掉），把错误交给界面显示。
            _stats.value = PumpStats(
                framesSent = sent,
                lastError = e.message ?: e.javaClass.simpleName,
            )
        }
    }
}

data class PumpStats(
    val framesSent: Long = 0,
    val lastError: String? = null,
)
