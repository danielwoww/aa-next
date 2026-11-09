package com.aanext.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Settings screen. Use it parked only. */
class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var log: TextView
    private lateinit var calibrate: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(header("Status"))
        status = mono()
        root.addView(status)
        root.addView(row(
            button("Accessibility settings") { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            button("Restart service") {
                if (!A11y.restart(this)) AppLog.add(this, "restart needs the one-time adb grant (see Status)")
                window.decorView.postDelayed({ refresh() }, 800)
            },
            button("Refresh") { refresh() },
        ))

        root.addView(header("Overlay"))
        root.addView(check("Enabled", prefs.enabled) { prefs.enabled = it })
        root.addView(check("Show faint zone outlines while driving", prefs.outlines) { prefs.outlines = it })
        root.addView(check("Haptic click on tap (if the unit supports it)", prefs.haptic) { prefs.haptic = it })

        root.addView(header("Calibration (parked!)"))
        root.addView(text(
            "1. Tap START, then switch to ZLINK with Waze + Spotify showing.\n" +
            "2. Drag the dark panel so it covers the whole media card. Resize it with the white square in its bottom-right corner.\n" +
            "3. Drag and resize the coloured zones the same way. Make NEXT as big as you like.\n" +
            "4. Tap a zone's top strip to switch it on/off. Uncovered panel area = guard: taps there do nothing.\n" +
            "5. Tap DONE (top-left of the panel)."
        ))
        calibrate = button("") {
            prefs.editMode = !prefs.editMode
            if (prefs.editMode) moveTaskToBack(true) // get out of the way so ZLINK shows
            refresh()
        }
        root.addView(row(calibrate, button("Reset to default layout") {
            prefs.layoutJson = null
            AppLog.add(this, "layout reset to default")
            refresh()
        }))

        root.addView(header("Advanced"))
        val pkg = EditText(this).apply { setText(prefs.targetPackage); minEms = 14; isSingleLine = true }
        val snooze = EditText(this).apply { setText(prefs.snoozeSeconds.toString()); inputType = InputType.TYPE_CLASS_NUMBER; minEms = 3 }
        root.addView(row(text("Show over package"), pkg))
        root.addView(row(text("Press-and-hold hides overlay for (s)"), snooze))
        root.addView(row(
            button("Save") {
                prefs.targetPackage = pkg.text.toString()
                prefs.snoozeSeconds = snooze.text.toString().toIntOrNull() ?: 15
                pkg.setText(prefs.targetPackage); snooze.setText(prefs.snoozeSeconds.toString())
                AppLog.add(this, "settings saved: package=${prefs.targetPackage}, snooze=${prefs.snoozeSeconds}s")
            },
            button("Test NEXT key now") { MediaKeys.send(this, Action.NEXT); AppLog.add(this, "NEXT sent from settings") },
        ))

        root.addView(header("Log (newest first)"))
        root.addView(button("Clear log") { AppLog.clear(this) })
        log = mono()
        root.addView(log)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        AppLog.onChange = { runOnUiThread { log.text = AppLog.read(this) } }
        // Recover from a force-stop (swipe-away) automatically if we have the adb grant.
        if (A11y.ensureEnabled(this, "app opened") && AANextService.instance == null) {
            window.decorView.postDelayed({ refresh() }, 800)
        }
        refresh()
    }

    override fun onPause() {
        AppLog.onChange = null
        super.onPause()
    }

    private fun refresh() {
        val svc = AANextService.instance
        calibrate.text = if (prefs.editMode) "STOP calibrating" else "START calibrating"
        val layout = svc?.layout?.describe() ?: prefs.layoutJson?.let { "saved (service off)" } ?: "default"
        status.text = buildString {
            appendLine("Service:   ${if (svc != null) "ON" else "OFF  <- enable 'AA Next' in Accessibility settings"}")
            appendLine("Self-repair: " + if (A11y.canSelfEnable(this@MainActivity)) "yes (re-enables itself after a force-stop)"
                else "no  <- adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS")
            appendLine("Overlay:   ${if (prefs.enabled) "enabled" else "disabled"}${if (prefs.editMode) " (CALIBRATING)" else ""}")
            appendLine("Shows over ${prefs.targetPackage}")
            appendLine("Last app:  ${AANextService.lastForeground ?: "?"}")
            append("Layout:    $layout")
        }
        log.text = AppLog.read(this)
    }

    private fun open(i: Intent) {
        try { startActivity(i) } catch (e: ActivityNotFoundException) { AppLog.add(this, "no screen for ${i.action}") }
    }

    // ---- view helpers ----
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun header(t: String) = TextView(this).apply {
        text = t; textSize = 18f; setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(16), 0, dp(4))
    }
    private fun text(t: String) = TextView(this).apply { text = t; textSize = 15f; setPadding(0, 0, dp(8), 0) }
    private fun mono() = TextView(this).apply { typeface = Typeface.MONOSPACE; textSize = 13f; setTextIsSelectable(true) }
    private fun button(t: String, onClick: () -> Unit) = Button(this).apply { text = t; setOnClickListener { onClick() } }
    private fun check(t: String, v: Boolean, onChange: (Boolean) -> Unit) = CheckBox(this).apply {
        text = t; isChecked = v; textSize = 16f; setOnCheckedChangeListener { _, c -> onChange(c) }
    }
    private fun row(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEach { addView(it) }
    }
}
