package com.padlink.core

/** 端口、节拍、超时等协议常量。见 protocol/protocol.md §1 / §7。 */
object ProtocolConstants {
    /** 控制通道（TCP）。 */
    const val CONTROL_PORT = 42312

    /** 发现 + 数据通道（UDP，PC 端单 socket 复用）。 */
    const val UDP_PORT = 42313

    const val DISCOVER_PROBE = "PADLINK?1"
    const val ANNOUNCE_PREFIX = "PADLINK!1 "

    const val VERSION: Byte = 1

    /** 输入帧固定长度。 */
    const val FRAME_SIZE = 28

    /** 震动帧固定长度。 */
    const val RUMBLE_SIZE = 8

    const val MAGIC_0: Byte = 0x50 // 'P'
    const val MAGIC_1: Byte = 0x4C // 'L'

    const val ACTIVE_RATE_HZ = 60
    const val IDLE_RATE_HZ = 10

    /** PC 端 fail-safe：超过此时长未收到合规输入帧即归零输入。 */
    const val FAIL_SAFE_TIMEOUT_MS = 300L

    const val MAX_PLAYERS = 4
}

/** 协议 `attr` 字段 bit2-4 的取值，见 protocol.md §5。 */
enum class ControllerType(val wireName: String, val id: Int) {
    XBOX360("xbox360", 0),
    DS4("ds4", 1),

    // 预留：协议已分配编号，PC 端后端尚未实现。
    DUAL_SENSE("dualsense", 2),
    SWITCH_PRO("switchpro", 3),
    ;

    companion object {
        fun fromId(id: Int): ControllerType? = entries.firstOrNull { it.id == id }

        fun fromWireName(name: String?): ControllerType? = entries.firstOrNull { it.wireName == name }
    }
}

/**
 * 协议 `buttons` 位掩码。**物理位置语义**——bit0 永远是手柄下方那颗键，
 * UI 上显示 A 还是 ✕ 由布局模板决定。见 protocol.md §5.2。
 */
object GamepadButtons {
    const val NONE = 0
    const val A = 1 shl 0
    const val B = 1 shl 1
    const val X = 1 shl 2
    const val Y = 1 shl 3
    const val LEFT_SHOULDER = 1 shl 4
    const val RIGHT_SHOULDER = 1 shl 5
    const val BACK = 1 shl 6
    const val START = 1 shl 7
    const val LEFT_THUMB = 1 shl 8
    const val RIGHT_THUMB = 1 shl 9
    const val GUIDE = 1 shl 10
    const val TOUCHPAD = 1 shl 11
    const val ALL = 0x0FFF
}

/** 协议 `dpad` 枚举，见 protocol.md §5.3。 */
enum class DPad(val value: Int) {
    NEUTRAL(0),
    NORTH(1),
    NORTH_EAST(2),
    EAST(3),
    SOUTH_EAST(4),
    SOUTH(5),
    SOUTH_WEST(6),
    WEST(7),
    NORTH_WEST(8),
    ;

    companion object {
        fun fromValue(value: Int): DPad? = entries.firstOrNull { it.value == value }
    }
}

/** 解码失败的原因。用于日志与测试断言，不跨网络传输。 */
enum class ProtocolError {
    NONE,
    BAD_SIZE,
    BAD_MAGIC,
    UNSUPPORTED_VERSION,
    RESERVED_NOT_ZERO,
    UNKNOWN_CONTROLLER,
    UNKNOWN_MESSAGE_TYPE,
}

class ProtocolException(val error: ProtocolError, message: String) : Exception(message)

/** 轴与扳机的取值范围。 */
object AxisRange {
    const val AXIS_MIN = -32767
    const val AXIS_MAX = 32767
    const val TRIGGER_MIN = 0
    const val TRIGGER_MAX = 32767
}
