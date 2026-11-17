package io.github.henry753951.airplayremote.launcher

import io.github.henry753951.airplayremote.launcher.Recent.Badge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    @Test fun lastUsedFirstWithoutFavoritesAndGoneApps() {
        val log = listOf(Launch("a", 10, 9), Launch("b", 30, 1), Launch("c", 20, 1), Launch("gone", 40, 1))
        assertEquals(listOf("c", "a"), Recent.pick(log, setOf("a", "b", "c"), exclude = setOf("b")))
    }

    @Test fun pickKeepsTheLimit() {
        val log = (1..9).map { Launch("p$it", it.toLong(), 1) }
        assertEquals(listOf("p9", "p8", "p7"), Recent.pick(log, log.map { it.packageName }.toSet(), emptySet(), limit = 3))
    }

    @Test fun newUntilFirstLaunch() {
        val installed = now - 2 * day
        assertEquals(Badge.NEW, Recent.badge(installed, installed, null, now))
        assertEquals(Badge.NEW, Recent.badge(installed, installed, installed - day, now))
        assertNull(Recent.badge(installed, installed, installed + 1, now))
    }

    @Test fun updatedUntilLaunchAfterUpdate() {
        val installed = now - 50 * day
        val updated = now - day
        assertEquals(Badge.UPDATED, Recent.badge(installed, updated, now - 3 * day, now))
        assertNull(Recent.badge(installed, updated, updated + 5, now))
    }

    @Test fun oldInstallHasNoBadge() {
        assertNull(Recent.badge(now - 30 * day, now - 20 * day, null, now))
    }
}
