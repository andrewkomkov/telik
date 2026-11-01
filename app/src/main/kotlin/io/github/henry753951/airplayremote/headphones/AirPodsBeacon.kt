package io.github.henry753951.airplayremote.headphones

/** Apple proximity-pairing advertisement. The layout comes from the GreenPods project. */
internal data class AirPodsBeacon(val worn: Boolean, val batteryPercent: Int?) {
    companion object {
        const val APPLE_COMPANY_ID = 0x004C
        val PREFIX = byteArrayOf(0x07, 0x19)
        val PREFIX_MASK = byteArrayOf(0xFF.toByte(), 0xFF.toByte())

        private const val LENGTH = 27
        private const val WORN_BITS = 0x0A
        private const val UNKNOWN_LEVEL = 0x0F
        private const val MAX_LEVEL = 10

        fun parse(data: ByteArray?): AirPodsBeacon? {
            if (data == null || data.size != LENGTH) return null
            if (data[0] != PREFIX[0] || data[1] != PREFIX[1]) return null
            val status = data[5].toInt() and 0xFF
            val levels = data[6].toInt() and 0xFF
            val level = listOf(levels shr 4, levels and 0x0F).firstOrNull { it != UNKNOWN_LEVEL }
            return AirPodsBeacon(status and WORN_BITS != 0, level?.let { it.coerceAtMost(MAX_LEVEL) * 10 })
        }
    }
}

/** Allows one connection each time the headphones go on the head. */
internal class WornTrigger(private val rearmAfterMs: Long = 120_000) {
    private var armed = true
    private var lastSeen: Long? = null

    fun onBeacon(worn: Boolean, now: Long): Boolean {
        if (lastSeen?.let { now - it > rearmAfterMs } == true) armed = true
        lastSeen = now
        if (!worn) {
            armed = true
            return false
        }
        if (!armed) return false
        armed = false
        return true
    }
}
