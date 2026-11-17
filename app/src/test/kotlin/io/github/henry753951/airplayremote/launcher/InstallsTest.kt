package io.github.henry753951.airplayremote.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class InstallsTest {
    private fun i(id: Int, pkg: String, p: Float = 0.5f) = Install(id, pkg, null, null, p)

    @Test fun dropsOwnAppAndDuplicates() {
        val out = Installs.pick(listOf(i(1, "a"), i(2, "a"), i(3, "dev.telik.tv"), i(4, "")), "dev.telik.tv")
        assertEquals(listOf(1), out.map { it.sessionId })
    }

    @Test fun progressStaysInRange() {
        assertEquals(1f, Installs.pick(listOf(i(1, "a", 1.4f)), "x")[0].progress, 0f)
        assertEquals(0f, Installs.pick(listOf(i(1, "a", -0.2f)), "x")[0].progress, 0f)
    }
}
