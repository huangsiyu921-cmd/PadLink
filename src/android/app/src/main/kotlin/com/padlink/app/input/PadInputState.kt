package com.padlink.app.input

import com.padlink.core.AxisRange
import com.padlink.core.ControllerType
import com.padlink.core.DPad
import com.padlink.core.InputFrame
import kotlin.math.roundToInt

/**
 * 手柄的当前输入。UI 层写它，发送循环读它。
 *
 * 触屏坐标在这里换算成协议的**物理语义**——这是全项目唯一做这个换算的地方：
 * 屏幕 y 向下为正，协议 y 向上为正（protocol.md §5.4）。
 * 别在 UI 里到处取反，那样迟早有一处漏了，表现就是"上下颠倒"。
 */
class PadInputState(
    private val controller: ControllerType = ControllerType.XBOX360,
    private val player: Int = 0,
) {
    // 分散的 @Volatile 字段：理论上可能读到几十微秒内的混合状态，对输入无影响，
    // 换来的是零分配、零锁。发送循环每 16ms 才读一次，实际几乎不会撞上。
    @Volatile var leftX = 0f
    @Volatile var leftY = 0f
    @Volatile var rightX = 0f
    @Volatile var rightY = 0f

    /** [GamepadButtons] 位掩码。 */
    @Volatile var buttons = 0

    @Volatile var dpad = DPad.NEUTRAL
    @Volatile var leftTrigger = 0f
    @Volatile var rightTrigger = 0f

    fun toFrame(): InputFrame = InputFrame(
        controller = controller,
        player = player,
        buttons = buttons,
        dpad = dpad,
        leftX = axis(leftX),
        leftY = axis(-leftY),                  // 屏幕往下 → 协议往上
        rightX = axis(rightX),
        rightY = axis(-rightY),
        leftTrigger = trigger(leftTrigger),
        rightTrigger = trigger(rightTrigger),
    )

    private fun axis(value: Float): Short =
        (value.coerceIn(-1f, 1f) * AxisRange.AXIS_MAX).roundToInt().toShort()

    private fun trigger(value: Float): Int =
        (value.coerceIn(0f, 1f) * AxisRange.TRIGGER_MAX).roundToInt()
}
