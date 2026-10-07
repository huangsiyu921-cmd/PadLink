package com.padlink.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow

/**
 * 重力转向的解算：把"屏幕坐标下的左右倾角"换算成 -1..1 的轴值。
 *
 * 只管"角度 → 轴值"这一段，**没有任何 Android 依赖**：传感器怎么读、屏幕方向怎么换算
 * 由 app 模块负责，这里能在 JVM 上直接测。
 *
 * 用法：先 [recenter] 定零点，再反复 [update]。零点必须由用户当前姿态决定——
 * 坐着玩和躺着玩，手机的物理倾角可以完全一样，但玩家要的是"相对自己舒服的姿势"。
 *
 * 手感参数在 [settings] 上，随时可改，改完不用重新定零点。
 */
class TiltSolver(settings: TiltSettings = TiltSettings()) {

    var settings: TiltSettings = settings

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
        val current = settings
        val target = shape(deltaDegrees(rollRad), current)
        output += (target - output) * current.smoothing.coerceIn(0f, 1f)
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
     * 死区 → 曲线 → 饱和。
     *
     * 出死区后是从 0 连续长起来的（把死区宽度减掉），不是从死区边界直接跳一段——
     * 后者在回中的瞬间会"啪"地弹一下。
     */
    private fun shape(degrees: Float, settings: TiltSettings): Float {
        val magnitude = abs(degrees)
        if (magnitude <= settings.deadZoneDeg) return 0f

        val usable = settings.maxAngleDeg - settings.deadZoneDeg
        if (usable <= 0f) return if (degrees < 0f) -1f else 1f

        val travel = ((magnitude - settings.deadZoneDeg) / usable).coerceAtMost(1f)
        val curved = travel.pow(settings.curve.coerceIn(MIN_CURVE, MAX_CURVE))
        return if (degrees < 0f) -curved else curved
    }

    companion object {
        /** 曲线指数的可用范围。再往外就成了一根折线，没有实用价值。 */
        const val MIN_CURVE = 0.2f
        const val MAX_CURVE = 3f
    }
}
