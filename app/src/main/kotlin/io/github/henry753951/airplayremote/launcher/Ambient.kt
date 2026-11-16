package io.github.henry753951.airplayremote.launcher

import kotlin.math.max
import kotlin.math.min

/**
 * Main color of tile art (005 US8, FR-019): hue buckets weighted by saturation and brightness.
 * Gray, white, black and transparent pixels do not count.
 */
object Ambient {
    private const val BUCKETS = 12
    /** Below this weight per opaque pixel, the art counts as gray. */
    private const val MIN_WEIGHT = 0.004f

    /** Returns an opaque ARGB color, or null when the art has no clear color (a gray logo). */
    fun pick(pixels: IntArray): Int? {
        val weight = FloatArray(BUCKETS)
        val hueX = FloatArray(BUCKETS)
        val hueY = FloatArray(BUCKETS)
        var opaque = 0
        val hsv = FloatArray(3)
        for (p in pixels) {
            if ((p ushr 24) < 128) continue
            opaque++
            toHsv(p, hsv)
            val h = hsv[0]
            val s = hsv[1]
            val v = hsv[2]
            if (s < 0.25f || v < 0.2f) continue
            val w = s * s * v
            val b = ((h / 360f) * BUCKETS).toInt().coerceIn(0, BUCKETS - 1)
            weight[b] += w
            // Sum unit vectors: the mean of 350 and 10 is 0, not 180.
            val rad = Math.toRadians(h.toDouble())
            hueX[b] += (kotlin.math.cos(rad) * w).toFloat()
            hueY[b] += (kotlin.math.sin(rad) * w).toFloat()
        }
        if (opaque == 0) return null
        // Neighbors count half, so a hue on a bucket edge is not split in two.
        var best = -1
        var bestW = 0f
        for (i in 0 until BUCKETS) {
            val w = weight[i] + 0.5f * (weight[(i + 1) % BUCKETS] + weight[(i + BUCKETS - 1) % BUCKETS])
            if (w > bestW) { bestW = w; best = i }
        }
        if (best < 0 || bestW < MIN_WEIGHT * opaque) return null
        var x = 0f
        var y = 0f
        for (d in -1..1) {
            val i = (best + d + BUCKETS) % BUCKETS
            val f = if (d == 0) 1f else 0.5f
            x += hueX[i] * f; y += hueY[i] * f
        }
        var hue = Math.toDegrees(kotlin.math.atan2(y.toDouble(), x.toDouble())).toFloat()
        if (hue < 0f) hue += 360f
        return fromHsv(hue, 0.72f, 0.78f)
    }

    /** A darker shade of [color] for a tile face. */
    fun shade(color: Int, value: Float = 0.32f): Int {
        val hsv = FloatArray(3)
        toHsv(color, hsv)
        return fromHsv(hsv[0], min(hsv[1], 0.6f), value)
    }

    internal fun toHsv(argb: Int, out: FloatArray) {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = mx - mn
        val h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }
        out[0] = if (h < 0f) h + 360f else h
        out[1] = if (mx == 0f) 0f else d / mx
        out[2] = mx
    }

    internal fun fromHsv(h: Float, s: Float, v: Float): Int {
        val c = v * s
        val hp = ((h % 360f) + 360f) % 360f / 60f
        val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
        val (r1, g1, b1) = when (hp.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = v - c
        fun ch(f: Float) = ((f + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
    }
}
