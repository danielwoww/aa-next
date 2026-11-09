package com.aanext.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * After a reboot or an app update, switch the accessibility service back on if needed.
 * Note: Android does not deliver these to an app that was force-stopped until it is opened
 * once by hand, so a swipe-away still needs one tap on the AA Next icon to recover.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        AppLog.add(ctx, "broadcast ${intent.action?.substringAfterLast('.')}")
        A11y.ensureEnabled(ctx, intent.action?.substringAfterLast('.') ?: "broadcast")
    }
}
