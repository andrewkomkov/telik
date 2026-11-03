package io.github.henry753951.airplayremote.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileLayoutTest {
    private fun t(p: String, l: String = p) = AppTile(p, l, "$p.Main")
    private val apps = listOf(t("c", "Cat"), t("a", "apple"), t("b", "Bee"))

    @Test fun newAppsSortedByLabel() {
        assertEquals(listOf("a", "b", "c"), TileLayout.arrange(apps, HomeLayout()).map { it.packageName })
    }

    @Test fun savedOrderWinsAndMissingAppsAreSkipped() {
        val layout = HomeLayout(order = listOf("c", "gone", "a"))
        assertEquals(listOf("c", "a", "b"), TileLayout.arrange(apps, layout).map { it.packageName })
    }

    @Test fun hiddenAppsRemovedExceptInEditMode() {
        val layout = HomeLayout(hidden = setOf("a"))
        assertEquals(listOf("b", "c"), TileLayout.arrange(apps, layout).map { it.packageName })
        assertEquals(3, TileLayout.arrange(apps, layout, includeHidden = true).size)
    }

    @Test fun moveClampsAtEdges() {
        val o = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), TileLayout.move(o, "a", 1))
        assertEquals(o, TileLayout.move(o, "a", -1))
        assertEquals(listOf("a", "c", "b"), TileLayout.move(o, "c", -1))
        assertEquals(o, TileLayout.move(o, "zzz", 1))
    }

    @Test fun setHiddenToggles() {
        val l = TileLayout.setHidden(HomeLayout(), "a", true)
        assertTrue("a" in l.hidden)
        assertFalse("a" in TileLayout.setHidden(l, "a", false).hidden)
    }

    @Test fun crashGuardDropsAtThree() {
        assertFalse(CrashGuard.shouldDrop(2))
        assertTrue(CrashGuard.shouldDrop(3))
    }
}
