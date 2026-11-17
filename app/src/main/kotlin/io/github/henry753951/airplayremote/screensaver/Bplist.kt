package io.github.henry753951.airplayremote.screensaver

import java.nio.ByteBuffer

/**
 * Reader for the one binary property list that the screensaver needs (006 US4): the place names of
 * the aerial videos, a top-level dictionary of strings. Other value types are skipped.
 */
object Bplist {
    fun strings(data: ByteArray): Map<String, String> {
        require(data.size >= 40 && String(data, 0, 8, Charsets.US_ASCII) == "bplist00") { "not a bplist00" }
        val t = ByteBuffer.wrap(data, data.size - 32, 32)
        t.position(data.size - 26)
        val offsetSize = t.get().toInt()
        val refSize = t.get().toInt()
        val count = t.long.toInt()
        val top = t.long.toInt()
        val tableAt = t.long.toInt()
        fun uint(at: Int, size: Int): Int {
            var v = 0L
            for (i in 0 until size) v = (v shl 8) or (data[at + i].toLong() and 0xFF)
            return v.toInt()
        }
        fun offset(ref: Int): Int {
            require(ref in 0 until count) { "bad object ref" }
            return uint(tableAt + ref * offsetSize, offsetSize)
        }
        // Count of a container or string: the low nibble, or an int object after the marker.
        fun lengthAt(at: Int): Pair<Int, Int> {
            val low = data[at].toInt() and 0x0F
            if (low != 0x0F) return low to at + 1
            val intMarker = data[at + 1].toInt() and 0xFF
            require(intMarker shr 4 == 0x1) { "bad length" }
            val size = 1 shl (intMarker and 0x0F)
            return uint(at + 2, size) to at + 2 + size
        }
        fun string(ref: Int): String? {
            val at = offset(ref)
            val type = (data[at].toInt() and 0xF0) shr 4
            val (len, start) = lengthAt(at)
            return when (type) {
                0x5 -> String(data, start, len, Charsets.US_ASCII)
                0x6 -> String(data, start, len * 2, Charsets.UTF_16BE)
                else -> null
            }
        }
        val at = offset(top)
        require((data[at].toInt() and 0xF0) == 0xD0) { "top object is not a dictionary" }
        val (n, refs) = lengthAt(at)
        val out = HashMap<String, String>(n)
        for (i in 0 until n) {
            val key = string(uint(refs + i * refSize, refSize)) ?: continue
            val value = string(uint(refs + (n + i) * refSize, refSize)) ?: continue
            out[key] = value
        }
        return out
    }
}
