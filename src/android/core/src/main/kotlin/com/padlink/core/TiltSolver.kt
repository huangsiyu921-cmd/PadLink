package com.padlink.core

import kotlin.math.PI
import kotlin.math.abs

/**
 * 重力转向的解算：把"屏幕坐标下的左右倾角"换算成 -1..1 的轴值。
 *
 * 只管"角度 → 轴值"这一段，**没有任何 Android 依赖**：传感器怎么读、屏幕方向怎么换算
 * 由 app 模块负责，这里能在 JVM 上直接测。
 *
 * 用法：先 [recenter] 定零点，再反复 [update]。零点必须由用户当前姿态决定——
 * 坐着玩和躺着玩，手机的物理倾角可以完全一样，但玩家要的是"相对自己舒服的姿势"。
 */
class TiltSolver(
    private val maxAngleDeg: Float = DEFAULT_MAX_ANGLE_DEG,
    private val deadZoneDeg: Float = DEFAULT_DEAD_ZONE_DEG,
    private val smoothing: Float = DEFAULT_SMOOTHING,
) {
    private var zeroRad = 0f
    private var output = 0f

    /** 以 [rollRad] 这个姿态为零点。玩到一半换姿势（躺下、翘腿）要重新定一次。 */
    fun recenter(rollRad: Float) {
        zeroRad = rollRad
        output = 0f
    }

    /** 关掉重力时清干净，下次开起来从 0 开始，不带上次的残留。 */
    fun reset() {
        zeroRad = 0f
        output = 0f
    }

    /**
     * 喂一个倾角，返回 -1..1。
     *
     * 平滑放在最后一步：传感器的噪声比触摸大得多，不做低通的话摇杆会一直在抖。
     */
    fun update(rollRad: Float): Float {
        val target = shape(deltaDegrees(rollRad))
        output += (target - output) * smoothing.coerceIn(0f, 1f)
        return output
    }

    /** 相对零点的角度，归一到 -180..180。 */
    private fun deltaDegrees(rollRad: Float): Float {
        val delta = (rollRad - zeroRad).toDouble()
        val twoPi = 2.0 * PI
        var wrapped = delta % twoPi
        if (wrapped > PI) wrapped -= twoPi
        if (wrapped < -PI) wrapped += twoPi
        return (wrapped * 180.0 / PI).toFloat()
    }

    /**
     * 死区 → 线性 → 饱和。
     *
     * 出死区后是从 0 连续长起来的（把死区宽度减掉），不是从死区边界直接跳一段——
     * 后者在回中的瞬间会"啪"地弹一下。
     */
    private fun shape(degrees: Float): Float {
        val magnitude = abs(degrees)
        if (magnitude <= deadZoneDeg) return 0f

        val usable = maxAngleDeg - deadZoneDeg
        if (usable <= 0f) return if (degrees < 0f) -1f else 1f

        val unit = ((magnitude - deadZoneDeg) / usable).coerceAtMost(1f)
        return if (degrees < 0f) -unit else unit
    }

    companion object {
        /** 推到 ±25° 算满舵。赛车游戏一般在 ±20~30°，再大手腕就拧不动了。 */
        const val DEFAULT_MAX_ANGLE_DEG = 25f

        /** ±2.5° 以内不动，不然手一抖摇杆就飘。 */
        const val DEFAULT_DEAD_ZONE_DEG = 2.5f

        /** 低通系数。游戏传感器 50Hz 下，时间常数约 80ms。 */
        const val DEFAULT_SMOOTHING = 0.25f
    }
}
