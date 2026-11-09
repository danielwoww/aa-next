package com.aanext.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/**
 * Content of the single overlay window that sits over the media card.
 *
 * Driving mode: tap a zone -> its media key; tap elsewhere on the panel -> swallowed (guard);
 *               press and hold anywhere -> panel hides for a few seconds.
 * Edit mode:    drag the panel / a zone to move it, drag the white corner squares to resize,
 *               tap a zone's label strip to switch it on/off, tap DONE to save.
 */
@SuppressLint("ViewConstructor")
class PanelView(ctx: Context, private val host: Host) : View(ctx) {

    interface Host {
        val layout: Layout
        val editMode: Boolean
        val outlines: Boolean
        val haptic: Boolean
        val screenW: Int
        val screenH: Int
        /** Window position/size changed during editing. */
        fun applyWindow()
        fun fire(zone: Zone)
        fun snooze()
        fun finishEdit()
    }

    private val d = resources.displayMetrics.density
    private val handle = 44 * d          // corner resize squares
    private val labelStrip = 30 * d      // top strip of a zone in edit mode (tap = enable/disable)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3 * d }
    private val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2 * d
        pathEffect = DashPathEffect(floatArrayOf(10 * d, 8 * d), 0f)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 16 * d; isFakeBoldText = true
        setShadowLayer(3 * d, 0f, 0f, Color.BLACK)
    }
    private val small = Paint(text).apply { textSize = 12 * d; isFakeBoldText = false }

    private var flashZone: Zone? = null
    private var flashGuard = false
    private val clearFlash = Runnable { flashZone = null; flashGuard = false; invalidate() }

    // ---------------- drawing ----------------

    override fun onDraw(c: Canvas) {
        val l = host.layout
        if (host.editMode) drawEdit(c, l) else drawDriving(c, l)
    }

    private fun drawDriving(c: Canvas, l: Layout) {
        if (host.outlines) {
            dashed.color = 0x55FFFFFF
            c.drawRoundRect(1f, 1f, l.w - 1f, l.h - 1f, 12 * d, 12 * d, dashed)
            for (z in l.zones) if (z.enabled) {
                stroke.color = withAlpha(z.action.color, 0x99)
                c.drawRoundRect(rect(z, 2f), 10 * d, 10 * d, stroke)
            }
        }
        flashZone?.let {
            fill.color = withAlpha(it.action.color, 0x99)
            c.drawRoundRect(rect(it, 2f), 10 * d, 10 * d, fill)
            c.drawText(it.action.label, it.x + 10 * d, it.y + it.h / 2f, text)
        }
        if (flashGuard) {
            stroke.color = 0xAAE53935.toInt()
            c.drawRoundRect(2f, 2f, l.w - 2f, l.h - 2f, 12 * d, 12 * d, stroke)
        }
    }

    private fun drawEdit(c: Canvas, l: Layout) {
        // Panel = guard area.
        fill.color = 0x66000000
        c.drawRect(0f, 0f, l.w.toFloat(), l.h.toFloat(), fill)
        stroke.color = Color.WHITE
        c.drawRect(1f, 1f, l.w - 1f, l.h - 1f, stroke)
        c.drawText("GUARD (drag to move)", 10 * d, l.h - 12 * d - handle, small)
        c.drawText("panel ${l.x},${l.y}  ${l.w}x${l.h}", 10 * d, l.h - handle + 4 * d, small)
        drawHandle(c, l.w - handle, l.h - handle)

        for (z in l.zones) {
            val r = rect(z, 0f)
            fill.color = withAlpha(z.action.color, if (z.enabled) 0x88 else 0x22)
            c.drawRect(r, fill)
            stroke.color = if (z.enabled) z.action.color else 0x88FFFFFF.toInt()
            c.drawRect(r, stroke)
            fill.color = withAlpha(Color.BLACK, 0x66)
            c.drawRect(r.left, r.top, r.right, r.top + labelStrip, fill)
            c.drawText((if (z.enabled) "[on] " else "[off] ") + z.action.label, r.left + 6 * d, r.top + 21 * d, text)
            if (z.enabled) {
                c.drawText("${z.w}x${z.h}", r.left + 6 * d, r.top + labelStrip + 16 * d, small)
                drawHandle(c, r.right - handle, r.bottom - handle)
            }
        }

        // DONE button, top-left of the panel.
        val done = doneRect()
        fill.color = 0xEE2E7D32.toInt()
        c.drawRoundRect(done, 8 * d, 8 * d, fill)
        c.drawText("DONE", done.left + 14 * d, done.centerY() + 6 * d, text)
    }

    private fun drawHandle(c: Canvas, x: Float, y: Float) {
        fill.color = Color.WHITE
        c.drawRect(x + 8 * d, y + 8 * d, x + handle, y + handle, fill)
    }

    private fun doneRect() = RectF(6 * d, 6 * d, 86 * d, 46 * d)
    private fun rect(z: Zone, inset: Float) = RectF(z.x + inset, z.y + inset, z.x + z.w - inset, z.y + z.h - inset)
    private fun withAlpha(color: Int, a: Int) = (color and 0x00FFFFFF) or (a shl 24)

    private fun flash(zone: Zone?) {
        flashZone = zone
        flashGuard = zone == null
        removeCallbacks(clearFlash)
        postDelayed(clearFlash, if (zone != null) 300 else 200)
        invalidate()
    }

    // ---------------- touch ----------------

    private enum class Drag { NONE, DONE, PANEL_MOVE, PANEL_RESIZE, ZONE_MOVE, ZONE_RESIZE, ZONE_TOGGLE }

    private var drag = Drag.NONE
    private var dragZone: Zone? = null
    private var sx = 0f
    private var sy = 0f
    private var s0 = IntArray(4)
    private var downZone: Zone? = null
    private var held = false
    private val holdRunnable = Runnable {
        held = true
        host.snooze()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (host.editMode) editTouch(e) else drivingTouch(e)
        return true // always consume: nothing on the panel reaches ZLINK
    }

    private fun drivingTouch(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downZone = host.layout.zones.lastOrNull { it.contains(e.x, e.y) }
                held = false
                postDelayed(holdRunnable, HOLD_MS)
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(holdRunnable)
                if (held) return
                val z = downZone
                if (z != null) {
                    if (host.haptic) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                    host.fire(z)
                }
                flash(z)
            }
            MotionEvent.ACTION_CANCEL -> removeCallbacks(holdRunnable)
        }
    }

    private fun editTouch(e: MotionEvent) {
        val l = host.layout
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                sx = e.rawX; sy = e.rawY
                dragZone = null
                val x = e.x
                val y = e.y
                val enabledZones = l.zones.filter { it.enabled }.asReversed()
                drag = when {
                    doneRect().contains(x, y) -> Drag.DONE
                    enabledZones.firstOrNull { inHandle(x, y, it.x + it.w, it.y + it.h) }?.also { dragZone = it } != null -> Drag.ZONE_RESIZE
                    l.zones.asReversed().firstOrNull { x >= it.x && x < it.x + it.w && y >= it.y && y < it.y + labelStrip }?.also { dragZone = it } != null -> Drag.ZONE_TOGGLE
                    enabledZones.firstOrNull { it.contains(x, y) }?.also { dragZone = it } != null -> Drag.ZONE_MOVE
                    inHandle(x, y, l.w, l.h) -> Drag.PANEL_RESIZE
                    else -> Drag.PANEL_MOVE
                }
                val z = dragZone
                s0 = when (drag) {
                    Drag.ZONE_MOVE, Drag.ZONE_RESIZE, Drag.ZONE_TOGGLE -> intArrayOf(z!!.x, z.y, z.w, z.h)
                    else -> intArrayOf(l.x, l.y, l.w, l.h)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (e.rawX - sx).toInt()
                val dy = (e.rawY - sy).toInt()
                val z = dragZone
                when (drag) {
                    Drag.PANEL_MOVE -> {
                        l.x = (s0[0] + dx).coerceIn(0, host.screenW - l.w)
                        l.y = (s0[1] + dy).coerceIn(0, host.screenH - l.h)
                        host.applyWindow()
                    }
                    Drag.PANEL_RESIZE -> {
                        // Never smaller than the zones it contains.
                        val minW = maxOf(Layout.MIN, l.zones.filter { it.enabled }.maxOfOrNull { it.x + it.w } ?: 0)
                        val minH = maxOf(Layout.MIN, l.zones.filter { it.enabled }.maxOfOrNull { it.y + it.h } ?: 0)
                        l.w = (s0[2] + dx).coerceIn(minW, host.screenW - l.x)
                        l.h = (s0[3] + dy).coerceIn(minH, host.screenH - l.y)
                        host.applyWindow()
                    }
                    Drag.ZONE_MOVE, Drag.ZONE_TOGGLE -> if (z != null && z.enabled) {
                        if (drag == Drag.ZONE_TOGGLE && (kotlin.math.abs(dx) > 12 * d || kotlin.math.abs(dy) > 12 * d)) drag = Drag.ZONE_MOVE
                        if (drag == Drag.ZONE_MOVE) {
                            z.x = (s0[0] + dx).coerceIn(0, l.w - z.w)
                            z.y = (s0[1] + dy).coerceIn(0, l.h - z.h)
                        }
                    }
                    Drag.ZONE_RESIZE -> if (z != null) {
                        z.w = (s0[2] + dx).coerceIn(Layout.MIN_ZONE, l.w - z.x)
                        z.h = (s0[3] + dy).coerceIn(Layout.MIN_ZONE, l.h - z.y)
                    }
                    else -> {}
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                when (drag) {
                    Drag.DONE -> if (doneRect().contains(e.x, e.y)) host.finishEdit()
                    Drag.ZONE_TOGGLE -> dragZone?.let {
                        it.enabled = !it.enabled
                        if (it.enabled) { it.x = it.x.coerceIn(0, l.w - it.w); it.y = it.y.coerceIn(0, l.h - it.h) }
                    }
                    else -> {}
                }
                drag = Drag.NONE
                invalidate()
            }
        }
    }

    private fun inHandle(x: Float, y: Float, right: Int, bottom: Int) =
        x >= right - handle && x <= right && y >= bottom - handle && y <= bottom

    companion object {
        const val HOLD_MS = 1200L
    }
}
