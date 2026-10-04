package com.padlink.app.layout

import android.content.Context

/** 布局存在本地（SharedPreferences），重启 App 还在。 */
class LayoutStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): PadLayout =
        prefs.getString(KEY_LAYOUT, null)
            ?.let { text -> runCatching { PadLayout.decode(text) }.getOrNull() }
            ?: PadLayout.Default

    fun save(layout: PadLayout) {
        prefs.edit().putString(KEY_LAYOUT, layout.encode()).apply()
    }

    fun reset() {
        prefs.edit().remove(KEY_LAYOUT).apply()
    }

    private companion object {
        const val PREFS_NAME = "padlink-layout"
        const val KEY_LAYOUT = "layout"
    }
}
