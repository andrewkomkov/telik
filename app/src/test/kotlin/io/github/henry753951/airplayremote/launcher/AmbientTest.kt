package io.github.henry753951.airplayremote.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientTest {
    private fun hue(argb: Int): Float = FloatArray(3).also { Ambient.toHsv(argb, it) }[0]

    private fun angleDiff(a: Float, b: Float): Float {
        val d = kotlin.math.abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }

    @Test fun grayLogoHasNoColor() {
        val px = IntArray(400) { if (it % 2 == 0) 0xFF202020.toInt() else 0xFFFFFFFF.toInt() }
        assertNull(Ambient.pick(px))
    }

    @Test fun transparentArtHasNoColor() {
        assertNull(Ambient.pick(IntArray(100) { 0x00FF0000 }))
    }

    @Test fun cyanTextOnDarkGivesCyan() {
        // A kino.watch banner: dark face, white text, a cyan word.
        val px = IntArray(400) {
            when {
                it < 40 -> 0xFF1FB6E8.toInt()
                it < 100 -> 0xFFFFFFFF.toInt()
                else -> 0xFF2A2A2A.toInt()
            }
        }
        val c = Ambient.pick(px)
        assertNotNull(c)
        assertTrue(angleDiff(hue(c!!), hue(0xFF1FB6E8.toInt())) < 8f)
    }

    @Test fun strongColorWinsOverPale() {
        val px = IntArray(300) { if (it < 100) 0xFFFF8A00.toInt() else 0xFFE8D8D0.toInt() }
        val c = Ambient.pick(px)!!
        assertTrue(angleDiff(hue(c), 32f) < 8f)
    }

    @Test fun redAcrossZeroDoesNotTurnCyan() {
        val px = IntArray(200) { if (it % 2 == 0) 0xFFFF0020.toInt() else 0xFFFF2000.toInt() }
        val c = Ambient.pick(px)!!
        assertTrue(angleDiff(hue(c), 0f) < 8f)
    }

    @Test fun hsvRoundTrip() {
        val c = Ambient.fromHsv(200f, 0.5f, 0.8f)
        val out = FloatArray(3)
        Ambient.toHsv(c, out)
        assertEquals(200f, out[0], 1.5f)
        assertEquals(0.5f, out[1], 0.01f)
        assertEquals(0.8f, out[2], 0.01f)
    }

    @Test fun shadeIsDarker() {
        val out = FloatArray(3)
        Ambient.toHsv(Ambient.shade(0xFF1FB6E8.toInt()), out)
        assertEquals(0.32f, out[2], 0.01f)
    }
}
