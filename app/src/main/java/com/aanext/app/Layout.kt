package com.aanext.app

import org.json.JSONArray
import org.json.JSONObject

/** What a zone does when tapped. */
enum class Action(val label: String, val color: Int) {
    NEXT("NEXT", 0xFF43A047.toInt()),
    PLAY_PAUSE("PLAY / PAUSE", 0xFF1E88E5.toInt()),
    PREV("PREV", 0xFFFB8C00.toInt());
}

/** A tap zone, in pixels relative to the panel's top-left corner. */
class Zone(val action: Action, var x: Int, var y: Int, var w: Int, var h: Int, var enabled: Boolean = true) {
    fun contains(px: Float, py: Float) = enabled && px >= x && px < x + w && py >= y && py < y + h
}

/**
 * The panel is one overlay window placed over the Android Auto media card (head-unit screen pixels).
 * Taps inside a zone fire its action; taps anywhere else on the panel are swallowed (guard area),
 * so a near miss can never reach the card and open Spotify full screen.
 */
class Layout(var x: Int, var y: Int, var w: Int, var h: Int, val zones: List<Zone>) {

    fun zone(a: Action) = zones.first { it.action == a }

    /** Keeps the panel on screen and every zone inside the panel. */
    fun clamp(screenW: Int, screenH: Int) {
        w = w.coerceIn(MIN, screenW)
        h = h.coerceIn(MIN, screenH)
        x = x.coerceIn(0, screenW - w)
        y = y.coerceIn(0, screenH - h)
        for (z in zones) {
            z.w = z.w.coerceIn(MIN_ZONE, w)
            z.h = z.h.coerceIn(MIN_ZONE, h)
            z.x = z.x.coerceIn(0, w - z.w)
            z.y = z.y.coerceIn(0, h - z.h)
        }
    }

    fun toJson(): String = JSONObject().apply {
        put("x", x); put("y", y); put("w", w); put("h", h)
        put("zones", JSONArray().apply {
            zones.forEach { z ->
                put(JSONObject().apply {
                    put("action", z.action.name); put("x", z.x); put("y", z.y)
                    put("w", z.w); put("h", z.h); put("enabled", z.enabled)
                })
            }
        })
    }.toString()

    fun describe() = "panel ($x,$y) ${w}x$h; " + zones.filter { it.enabled }
        .joinToString("; ") { "${it.action.label} (${x + it.x},${y + it.y}) ${it.w}x${it.h}" }

    companion object {
        const val MIN = 80
        const val MIN_ZONE = 50

        /**
         * First guess for the Waze + Spotify split, estimated from the photo of the 1280x720 unit:
         * the media card is the right ~third of the screen, its buttons sit along the bottom.
         * Expressed as fractions so it is sane on other resolutions; calibrate to make it exact.
         */
        fun default(screenW: Int, screenH: Int): Layout {
            val pw = (screenW * 0.33).toInt()
            val ph = (screenH * 0.88).toInt()
            val l = Layout(
                x = (screenW * 0.66).toInt(), y = (screenH * 0.10).toInt(), w = pw, h = ph,
                zones = listOf(
                    // Big NEXT: whole right ~40% of the card, lower 70%.
                    Zone(Action.NEXT, (pw * 0.58).toInt(), (ph * 0.30).toInt(), (pw * 0.42).toInt(), (ph * 0.70).toInt()),
                    Zone(Action.PLAY_PAUSE, 0, (ph * 0.72).toInt(), (pw * 0.30).toInt(), (ph * 0.28).toInt()),
                    Zone(Action.PREV, (pw * 0.31).toInt(), (ph * 0.72).toInt(), (pw * 0.25).toInt(), (ph * 0.28).toInt()),
                )
            )
            l.clamp(screenW, screenH)
            return l
        }

        fun fromJson(s: String?, screenW: Int, screenH: Int): Layout {
            if (s.isNullOrEmpty()) return default(screenW, screenH)
            return try {
                val o = JSONObject(s)
                val arr = o.getJSONArray("zones")
                val byAction = (0 until arr.length()).map { arr.getJSONObject(it) }
                    .associateBy { Action.valueOf(it.getString("action")) }
                val def = default(screenW, screenH)
                val zones = Action.values().map { a ->
                    val z = byAction[a] ?: return@map def.zone(a)
                    Zone(a, z.getInt("x"), z.getInt("y"), z.getInt("w"), z.getInt("h"), z.optBoolean("enabled", true))
                }
                Layout(o.getInt("x"), o.getInt("y"), o.getInt("w"), o.getInt("h"), zones).also { it.clamp(screenW, screenH) }
            } catch (e: Exception) {
                default(screenW, screenH)
            }
        }
    }
}
