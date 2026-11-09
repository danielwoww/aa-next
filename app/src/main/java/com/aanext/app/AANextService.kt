package com.aanext.app

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Owns the overlay window. Shows it only while ZLINK is in front (or while calibrating),
 * hides it for everything else so the head unit's own screens are never blocked.
 */
class AANextService : AccessibilityService(), PanelView.Host, SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())

    override lateinit var layout: Layout
        private set
    override val editMode get() = prefs.editMode
    override val outlines get() = prefs.outlines
    override val haptic get() = prefs.haptic
    override var screenW = 0
        private set
    override var screenH = 0
        private set

    private var view: PanelView? = null
    private var lp: WindowManager.LayoutParams? = null
    private var targetInFront = false
    private var snoozedUntil = 0L
    private var savingLayout = false
    private val refreshRunnable = Runnable { refresh() }

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
    }

    /** Writes any crash to the on-screen log before the process dies, so we can see it after a drive. */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is CrashLogger) return
        Thread.setDefaultUncaughtExceptionHandler(CrashLogger(applicationContext, previous))
    }

    private class CrashLogger(val ctx: android.content.Context, val next: Thread.UncaughtExceptionHandler?) :
        Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            runCatching {
                AppLog.add(ctx, "CRASH ${e.javaClass.simpleName}: ${e.message} @ " +
                    e.stackTrace.take(3).joinToString(" < ") { "${it.fileName}:${it.lineNumber}" })
            }
            next?.uncaughtException(t, e)
        }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        AppLog.add(this, "service unbound by Android (switched off or app stopped)")
        return super.onUnbind(intent)
    }

    override fun onServiceConnected() {
        prefs = Prefs(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        readScreen()
        layout = Layout.fromJson(prefs.layoutJson, screenW, screenH)
        prefs.raw.registerOnSharedPreferenceChangeListener(this)
        val front = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
        targetInFront = front == prefs.targetPackage
        instance = this
        AppLog.add(this, "service started, screen ${screenW}x$screenH, in front: $front")
        refresh()
    }

    // ---------------- which app is in front ----------------

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Volume popups and other system dialogs float over ZLINK without replacing it,
        // but the Recents screen (also systemui) does replace it.
        val cls = event.className?.toString() ?: ""
        if (pkg in TRANSPARENT_PACKAGES && !cls.contains("Recents")) return
        // Adding our own overlay window fires a window-state event under our package name.
        // Only our settings screen really "replaces" ZLINK; everything else from us is the overlay itself.
        if (pkg == packageName && cls != MainActivity::class.java.name) return
        lastForeground = pkg
        val nowFront = pkg == prefs.targetPackage
        if (nowFront != targetInFront) {
            targetInFront = nowFront
            AppLog.add(this, "[fg] $pkg -> overlay ${if (nowFront) "on" else "off"}")
            refresh()
        }
    }

    // ---------------- window management ----------------

    private fun shouldShow() = prefs.enabled &&
        (prefs.editMode || (targetInFront && SystemClock.uptimeMillis() >= snoozedUntil))

    fun refresh() {
        if (!::prefs.isInitialized) return
        if (shouldShow()) {
            if (view == null) addPanel() else applyWindow()
            view?.invalidate()
        } else {
            removePanel()
        }
    }

    private fun addPanel() {
        layout.clamp(screenW, screenH)
        val p = WindowManager.LayoutParams(
            layout.w, layout.h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = layout.x
            y = layout.y
        }
        val v = PanelView(this, this)
        try {
            wm.addView(v, p)
            view = v
            lp = p
        } catch (e: Exception) {
            AppLog.add(this, "addView FAILED: ${e.message}")
        }
    }

    private fun removePanel() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        lp = null
    }

    override fun applyWindow() {
        val v = view ?: return
        val p = lp ?: return
        p.x = layout.x; p.y = layout.y; p.width = layout.w; p.height = layout.h
        runCatching { wm.updateViewLayout(v, p) }
    }

    @Suppress("DEPRECATION")
    private fun readScreen() {
        val m = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(m)
        screenW = m.widthPixels
        screenH = m.heightPixels
    }

    // ---------------- PanelView.Host ----------------

    override fun fire(zone: Zone) {
        val ok = MediaKeys.send(this, zone.action)
        AppLog.add(this, "${zone.action.label} ${if (ok) "sent" else "FAILED"}")
    }

    override fun snooze() {
        snoozedUntil = SystemClock.uptimeMillis() + prefs.snoozeSeconds * 1000L
        AppLog.add(this, "hidden for ${prefs.snoozeSeconds}s (press-and-hold)")
        refresh()
        main.removeCallbacks(refreshRunnable)
        main.postDelayed(refreshRunnable, prefs.snoozeSeconds * 1000L + 50)
    }

    override fun finishEdit() {
        saveLayout()
        prefs.editMode = false // triggers refresh via the prefs listener
        AppLog.add(this, "calibration saved: ${layout.describe()}")
    }

    private fun saveLayout() {
        savingLayout = true
        prefs.layoutJson = layout.toJson()
        savingLayout = false
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        when (key) {
            Prefs.K_LAYOUT -> if (!savingLayout) {
                // Reset from the settings screen.
                layout = Layout.fromJson(prefs.layoutJson, screenW, screenH)
                applyWindow()
            }
            Prefs.K_EDIT -> if (!prefs.editMode) saveLayout() // also save if calibration was stopped from settings
            Prefs.K_PACKAGE -> targetInFront = lastForeground == prefs.targetPackage
        }
        refresh()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        AppLog.add(this, "service destroyed")
        removePanel()
        if (::prefs.isInitialized) prefs.raw.unregisterOnSharedPreferenceChangeListener(this)
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: AANextService? = null
            private set
        @Volatile var lastForeground: String? = null
            private set

        private val TRANSPARENT_PACKAGES = setOf("com.android.systemui", "android")
    }
}
