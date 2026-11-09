package com.aanext.app

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent

/**
 * Strategy A from the spike: dispatching a media key on the head unit reaches ZLINK's media
 * session, which forwards it to the phone (confirmed for NEXT on the alps YT5760B).
 */
object MediaKeys {
    fun send(ctx: Context, action: Action): Boolean {
        val code = when (action) {
            Action.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            Action.PREV -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            Action.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        return try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val t = SystemClock.uptimeMillis()
            am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
            am.dispatchMediaKeyEvent(KeyEvent(t, t + 20, KeyEvent.ACTION_UP, code, 0))
            true
        } catch (e: Exception) {
            AppLog.add(ctx, "media key ${action.name} FAILED: ${e.message}")
            false
        }
    }
}
