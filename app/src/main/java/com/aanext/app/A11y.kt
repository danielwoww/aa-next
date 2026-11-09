package com.aanext.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Android removes an app's accessibility service from the enabled list whenever the app is
 * force-stopped (e.g. swiped away in Recents on this head unit). With WRITE_SECURE_SETTINGS,
 * granted once over ADB, we can put it back ourselves.
 *
 *   adb shell pm grant com.aanext.app android.permission.WRITE_SECURE_SETTINGS
 */
object A11y {
    private fun component(ctx: Context) = ComponentName(ctx, AANextService::class.java)

    private fun enabledList(ctx: Context): List<String> =
        (Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
            .split(':').filter { it.isNotBlank() }

    private fun isOurs(ctx: Context, s: String) = ComponentName.unflattenFromString(s) == component(ctx)

    fun isEnabledInSettings(ctx: Context) = enabledList(ctx).any { isOurs(ctx, it) }

    fun canSelfEnable(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Turns the service back on if it was switched off. Returns true if it is (now) enabled. */
    fun ensureEnabled(ctx: Context, reason: String): Boolean {
        if (isEnabledInSettings(ctx)) return true
        if (!canSelfEnable(ctx)) return false
        return write(ctx, enabledList(ctx) + component(ctx).flattenToString(), "re-enabled accessibility service ($reason)")
    }

    /** Removes and re-adds the service, which makes Android re-bind it (for a stuck service). */
    fun restart(ctx: Context): Boolean {
        if (!canSelfEnable(ctx)) return false
        val others = enabledList(ctx).filterNot { isOurs(ctx, it) }
        write(ctx, others, "accessibility service stopped for restart")
        return write(ctx, others + component(ctx).flattenToString(), "accessibility service restarted")
    }

    private fun write(ctx: Context, list: List<String>, msg: String): Boolean = try {
        val cr = ctx.contentResolver
        Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list.joinToString(":"))
        Settings.Secure.putString(cr, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
        AppLog.add(ctx, msg)
        true
    } catch (e: SecurityException) {
        AppLog.add(ctx, "could not change accessibility settings: ${e.message}")
        false
    }
}
