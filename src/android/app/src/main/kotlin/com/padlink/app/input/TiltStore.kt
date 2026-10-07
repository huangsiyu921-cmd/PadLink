package com.padlink.app.input

import android.content.Context
import com.padlink.core.TiltSettings

/**
 * 重力转向存在本地的全部东西：手感参数、开关状态、零点。
 *
 * 三者都得存。手感是配置；**开关和零点是在 Activity 被回收重建之后接着玩的凭据**——
 * 不存的话重建后重力自己就关了、零点也丢了，表现就是"重力模式老是自己退出"。
 */
class TiltStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSettings(): TiltSettings {
        val defaults = TiltSettings()
        return TiltSettings(
            deadZoneDeg = prefs.getFloat(KEY_DEAD_ZONE, defaults.deadZoneDeg),
            maxAngleDeg = prefs.getFloat(KEY_MAX_ANGLE, defaults.maxAngleDeg),
            curve = prefs.getFloat(KEY_CURVE, defaults.curve),
            smoothing = prefs.getFloat(KEY_SMOOTHING, defaults.smoothing),
        )
    }

    // 下面几处一律 commit() 而不是 apply()：apply 是异步写盘，碰上强杀进程
    // （系统回收、force-stop）有机会丢掉，而这几项要的恰恰是"下次还在"。
    // 一次写的不到 1KB，同步落盘的开销可以忽略。

    fun saveSettings(settings: TiltSettings) {
        prefs.edit()
            .putFloat(KEY_DEAD_ZONE, settings.deadZoneDeg)
            .putFloat(KEY_MAX_ANGLE, settings.maxAngleDeg)
            .putFloat(KEY_CURVE, settings.curve)
            .putFloat(KEY_SMOOTHING, settings.smoothing)
            .commit()
    }

    fun loadEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun saveEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).commit()
    }

    /** 上次定的零点（弧度）。从没定过就是 null，那就等第一帧自动定。 */
    fun loadZeroRad(): Float? =
        if (prefs.contains(KEY_ZERO_RAD)) prefs.getFloat(KEY_ZERO_RAD, 0f) else null

    fun saveZeroRad(rad: Float) {
        prefs.edit().putFloat(KEY_ZERO_RAD, rad).commit()
    }

    private companion object {
        const val PREFS_NAME = "padlink-tilt"
        const val KEY_DEAD_ZONE = "deadZoneDeg"
        const val KEY_MAX_ANGLE = "maxAngleDeg"
        const val KEY_CURVE = "curve"
        const val KEY_SMOOTHING = "smoothing"
        const val KEY_ENABLED = "enabled"
        const val KEY_ZERO_RAD = "zeroRad"
    }
}
