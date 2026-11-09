package com.aanext.app

import android.content.Context
import android.content.SharedPreferences

class Prefs(ctx: Context) {
    val raw: SharedPreferences = ctx.applicationContext.getSharedPreferences("aanext", Context.MODE_PRIVATE)

    /** Master switch. */
    var enabled: Boolean
        get() = raw.getBoolean(K_ENABLED, true)
        set(v) = raw.edit().putBoolean(K_ENABLED, v).apply()

    /** Calibration mode: overlay is always visible and editable. */
    var editMode: Boolean
        get() = raw.getBoolean(K_EDIT, false)
        set(v) = raw.edit().putBoolean(K_EDIT, v).apply()

    /** Draw faint zone outlines while driving (off = fully invisible except the tap flash). */
    var outlines: Boolean
        get() = raw.getBoolean(K_OUTLINES, true)
        set(v) = raw.edit().putBoolean(K_OUTLINES, v).apply()

    var haptic: Boolean
        get() = raw.getBoolean(K_HAPTIC, true)
        set(v) = raw.edit().putBoolean(K_HAPTIC, v).apply()

    /** Overlay only shows while this package is in front. */
    var targetPackage: String
        get() = raw.getString(K_PACKAGE, DEFAULT_PACKAGE) ?: DEFAULT_PACKAGE
        set(v) = raw.edit().putString(K_PACKAGE, v.trim().ifEmpty { DEFAULT_PACKAGE }).apply()

    /** Press-and-hold on the panel hides it this long, so the card underneath can be used. */
    var snoozeSeconds: Int
        get() = raw.getInt(K_SNOOZE, 15)
        set(v) = raw.edit().putInt(K_SNOOZE, v.coerceIn(3, 300)).apply()

    var layoutJson: String?
        get() = raw.getString(K_LAYOUT, null)
        set(v) = raw.edit().putString(K_LAYOUT, v).apply()

    companion object {
        const val DEFAULT_PACKAGE = "com.zjinnova.zlink"
        const val K_ENABLED = "enabled"
        const val K_EDIT = "edit"
        const val K_OUTLINES = "outlines"
        const val K_HAPTIC = "haptic"
        const val K_PACKAGE = "package"
        const val K_SNOOZE = "snooze"
        const val K_LAYOUT = "layout"
    }
}
