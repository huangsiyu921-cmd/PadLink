package com.padlink.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 输入帧——**全量状态快照**，固定 28 字节。见 protocol.md §5。
 *
 * 一切输入都是物理语义：[leftY] / [rightY] **上为正**，[leftX] / [rightX] 右为正，
 * 与具体后端（XInput / DS4）无关。
 */
data class InputFrame(
    val controller: ControllerType = ControllerType.XBOX360,
    val player: Int = 0,
    val rumbleAck: Boolean = false,
    /** uint32，回绕由 uint 算术处理。 */
    val sequence: Long = 0,
    /** uint32，发送时刻（`SystemClock.uptimeMillis()` 低 32 位）。 */
    val timestampMs: Long = 0,
    val buttons: Int = 0,
    val dpad: DPad = DPad.NEUTRAL,
    val leftX: Short = 0,
    val leftY: Short = 0,
    val rightX: Short = 0,
    val rightY: Short = 0,
    /** 0..32767 表示 0.0..1.0。 */
    val leftTrigger: Int = 0,
    val rightTrigger: Int = 0,
) {
    fun encode(): ByteArray {
        val buffer = ByteArray(ProtocolConstants.FRAME_SIZE)
        encodeInto(buffer, 0)
        return buffer
    }

    /** 复用缓冲区，避免 60–120Hz 下每帧分配。 */
    fun encodeInto(destination: ByteArray, offset: Int = 0) {
        require(destination.size - offset >= ProtocolConstants.FRAME_SIZE) {
            "缓冲区不足 ${ProtocolConstants.FRAME_SIZE} 字节"
        }
        require(player in 0..3) { "player 必须 0..3，实际 $player" }
        require(buttons in 0..0xFFFF) { "buttons 必须是 16 位掩码，实际 $buttons" }
        require(leftTrigger in AxisRange.TRIGGER_MIN..AxisRange.TRIGGER_MAX) { "leftTrigger 越界" }
        require(rightTrigger in AxisRange.TRIGGER_MIN..AxisRange.TRIGGER_MAX) { "rightTrigger 越界" }

        val attr = (player and 0x03) or ((controller.id and 0x07) shl 2) or (if (rumbleAck) 1 shl 5 else 0)

        val buffer = ByteBuffer.wrap(destination, offset, ProtocolConstants.FRAME_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(ProtocolConstants.MAGIC_0)
        buffer.put(ProtocolConstants.MAGIC_1)
        buffer.put(ProtocolConstants.VERSION)
        buffer.put(attr.toByte())
        buffer.putInt((sequence and 0xFFFFFFFFL).toInt())
        buffer.putInt((timestampMs and 0xFFFFFFFFL).toInt())
        buffer.putShort((buttons and 0xFFFF).toShort())
        buffer.put(dpad.value.toByte())
        buffer.put(0)
        buffer.putShort(leftX)
        buffer.putShort(leftY)
        buffer.putShort(rightX)
        buffer.putShort(rightY)
        buffer.putShort(leftTrigger.toShort())
        buffer.putShort(rightTrigger.toShort())
    }

    companion object {
        fun neutral(
            controller: ControllerType = ControllerType.XBOX360,
            player: Int = 0,
        ) = InputFrame(controller = controller, player = player)

        /** 解码；不合规抛 [ProtocolException]。顺序与 tools/gen_testvectors.py 的参考实现一致。 */
        fun decode(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): InputFrame {
            if (length != ProtocolConstants.FRAME_SIZE) {
                throw ProtocolException(
                    ProtocolError.BAD_SIZE,
                    "帧长必须 ${ProtocolConstants.FRAME_SIZE}，实际 $length",
                )
            }
            if (bytes[offset] != ProtocolConstants.MAGIC_0 || bytes[offset + 1] != ProtocolConstants.MAGIC_1) {
                throw ProtocolException(ProtocolError.BAD_MAGIC, "magic 不是 'PL'")
            }
            if (bytes[offset + 2] != ProtocolConstants.VERSION) {
                throw ProtocolException(
                    ProtocolError.UNSUPPORTED_VERSION,
                    "协议版本 ${bytes[offset + 2]} 不受支持",
                )
            }
            // offset 15 是保留字节，见 protocol.md §5。
            if (bytes[offset + 15] != 0.toByte()) {
                throw ProtocolException(ProtocolError.RESERVED_NOT_ZERO, "保留字节必须为 0")
            }

            val attr = bytes[offset + 3].toInt() and 0xFF
            val controller = ControllerType.fromId((attr shr 2) and 0x07)
                ?: throw ProtocolException(ProtocolError.UNKNOWN_CONTROLLER, "未知手柄类型 id ${(attr shr 2) and 0x07}")

            val buffer = ByteBuffer.wrap(bytes, offset, ProtocolConstants.FRAME_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(4)
            val sequence = buffer.int.toLong() and 0xFFFFFFFFL
            val timestamp = buffer.int.toLong() and 0xFFFFFFFFL
            val buttons = buffer.short.toInt() and 0xFFFF
            val dpadValue = buffer.get().toInt() and 0xFF
            buffer.get() // offset 15，已校验
            val leftX = buffer.short
            val leftY = buffer.short
            val rightX = buffer.short
            val rightY = buffer.short
            val leftTrigger = buffer.short.toInt() and 0xFFFF
            val rightTrigger = buffer.short.toInt() and 0xFFFF

            return InputFrame(
                controller = controller,
                player = attr and 0x03,
                // bit6-7 是保留位，接收端忽略，便于后续扩展。
                rumbleAck = (attr and (1 shl 5)) != 0,
                sequence = sequence,
                timestampMs = timestamp,
                buttons = buttons,
                dpad = DPad.fromValue(dpadValue) ?: DPad.NEUTRAL,
                leftX = leftX,
                leftY = leftY,
                rightX = rightX,
                rightY = rightY,
                leftTrigger = leftTrigger,
                rightTrigger = rightTrigger,
            )
        }

        /** 解码；不合规返回 null。 */
        fun decodeOrNull(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): InputFrame? =
            try {
                decode(bytes, offset, length)
            } catch (_: ProtocolException) {
                null
            }
    }
}
