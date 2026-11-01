package io.github.henry753951.airplayremote.headphones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AirPodsBeaconTest {
    private fun frame(status: Int, levels: Int) = ByteArray(27).apply {
        this[0] = 0x07
        this[1] = 0x19
        this[5] = status.toByte()
        this[6] = levels.toByte()
    }

    @Test
    fun readsWornStateAndBattery() {
        assertEquals(AirPodsBeacon(worn = true, batteryPercent = 80), AirPodsBeacon.parse(frame(0x02, 0x8F)))
        assertEquals(AirPodsBeacon(worn = false, batteryPercent = 50), AirPodsBeacon.parse(frame(0x00, 0xF5)))
        assertEquals(AirPodsBeacon(worn = true, batteryPercent = null), AirPodsBeacon.parse(frame(0x08, 0xFF)))
    }

    @Test
    fun rejectsOtherFrames() {
        assertNull(AirPodsBeacon.parse(null))
        assertNull(AirPodsBeacon.parse(ByteArray(19)))
        assertNull(AirPodsBeacon.parse(frame(0, 0).apply { this[0] = 0x10 }))
    }

    @Test
    fun triggersOncePerWear() {
        val trigger = WornTrigger(rearmAfterMs = 100)
        assertTrue(trigger.onBeacon(worn = true, now = 0))
        assertFalse(trigger.onBeacon(worn = true, now = 50))
        assertFalse(trigger.onBeacon(worn = false, now = 60))
        assertTrue(trigger.onBeacon(worn = true, now = 70))
    }

    @Test
    fun rearmsAfterSilence() {
        val trigger = WornTrigger(rearmAfterMs = 100)
        assertTrue(trigger.onBeacon(worn = true, now = 0))
        assertTrue(trigger.onBeacon(worn = true, now = 200))
    }
}
