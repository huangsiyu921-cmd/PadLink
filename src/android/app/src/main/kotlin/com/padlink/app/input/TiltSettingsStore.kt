package com.padlink.app.input

import android.content.Context
import com.padlink.core.TiltSettings

/** 重力转向的手感参数存本地。跟布局一样，重启还在。 */
class TiltSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): TiltSettings {
        val defaults = TiltSettings()
        return TiltSettings(
            deadZoneDeg = prefs.getFloat(KEY_DEAD_ZONE, defaults.deadZoneDeg),
            maxAngleDeg = prefs.getFloat(KEY_MAX_ANGLE, defaults.maxAngleDeg),
            curve = prefs.getFloat(KEY_CURVE, defaults.curve),
            smoothing = prefs.getFloat(KEY_SMOOTHING, defaults.smoothing),
        )
    }

    fun save(settings: TiltSettings) {
        prefs.edit()
            .putFloat(KEY_DEAD_ZONE, settings.deadZoneDeg)
            .putFloat(KEY_MAX_ANGLE, settings.maxAngleDeg)
            .putFloat(KEY_CURVE, settings.curve)
            .putFloat(KEY_SMOOTHING, settings.smoothing)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "padlink-tilt"
        const val KEY_DEAD_ZONE = "deadZoneDeg"
        const val KEY_MAX_ANGLE = "maxAngleDeg"
        const val KEY_CURVE = "curve"
        const val KEY_SMOOTHING = "smoothing"
    }
}
