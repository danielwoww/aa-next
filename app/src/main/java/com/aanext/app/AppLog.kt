package com.aanext.app

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tiny persistent log. Survives the head unit killing our process, so after a test drive
 * the user can open the app and read (or photograph) what happened.
 */
object AppLog {
    private const val MAX_LINES = 200
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Volatile var onChange: (() -> Unit)? = null

    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, "log.txt")

    @Synchronized
    fun add(ctx: Context, msg: String) {
        val line = "${fmt.format(Date())}  $msg"
        Log.i("AANext", msg)
        val f = file(ctx)
        val lines = (if (f.exists()) f.readLines() else emptyList()) + line
        f.writeText(lines.takeLast(MAX_LINES).joinToString("\n") + "\n")
        onChange?.invoke()
    }

    @Synchronized
    fun read(ctx: Context, last: Int = 60): String {
        val f = file(ctx)
        if (!f.exists()) return "(empty)"
        return f.readLines().takeLast(last).reversed().joinToString("\n")
    }

    @Synchronized
    fun clear(ctx: Context) {
        file(ctx).delete()
        onChange?.invoke()
    }
}
