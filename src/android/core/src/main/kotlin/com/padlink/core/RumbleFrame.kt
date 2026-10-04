package com.padlink.core

/** 震动回传帧——PC → 手机，固定 8 字节。见 protocol.md §6。不重传、不确认。 */
data class RumbleFrame(
    val player: Int,
    val left: Int,
    val right: Int,
) {
    fun encode(): ByteArray {
        val buffer = ByteArray(ProtocolConstants.RUMBLE_SIZE)
        buffer[0] = ProtocolConstants.MAGIC_0
        buffer[1] = ProtocolConstants.MAGIC_1
        buffer[2] = ProtocolConstants.VERSION
        buffer[3] = RUMBLE_TYPE
        buffer[4] = player.toByte()
        buffer[5] = left.toByte()
        buffer[6] = right.toByte()
        buffer[7] = 0
        return buffer
    }

    companion object {
        const val RUMBLE_TYPE: Byte = 0x01

        fun decode(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): RumbleFrame {
            if (length != ProtocolConstants.RUMBLE_SIZE) {
                throw ProtocolException(ProtocolError.BAD_SIZE, "震动帧长必须 ${ProtocolConstants.RUMBLE_SIZE}")
            }
            if (bytes[offset] != ProtocolConstants.MAGIC_0 || bytes[offset + 1] != ProtocolConstants.MAGIC_1) {
                throw ProtocolException(ProtocolError.BAD_MAGIC, "magic 不是 'PL'")
            }
            if (bytes[offset + 2] != ProtocolConstants.VERSION) {
                throw ProtocolException(ProtocolError.UNSUPPORTED_VERSION, "协议版本不受支持")
            }
            if (bytes[offset + 3] != RUMBLE_TYPE) {
                throw ProtocolException(ProtocolError.UNKNOWN_MESSAGE_TYPE, "不是震动帧")
            }

            return RumbleFrame(
                player = bytes[offset + 4].toInt() and 0xFF,
                left = bytes[offset + 5].toInt() and 0xFF,
                right = bytes[offset + 6].toInt() and 0xFF,
            )
        }

        fun decodeOrNull(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): RumbleFrame? =
            try {
                decode(bytes, offset, length)
            } catch (_: ProtocolException) {
                null
            }
    }
}
