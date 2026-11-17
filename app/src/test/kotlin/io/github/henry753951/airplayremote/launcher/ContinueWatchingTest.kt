package io.github.henry753951.airplayremote.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContinueWatchingTest {
    private fun item(
        id: Long, pkg: String = "a", title: String = "T$id", at: Long = id,
        link: String? = "intent:#Intent;end", pos: Long = 0, len: Long = 0,
    ) = WatchNextItem(id, pkg, title, null, null, null, null, link, at, pos, len)

    @Test fun lastUsedFirst() {
        val out = ContinueWatching.pick(listOf(item(1, at = 10), item(2, at = 30), item(3, at = 20)), setOf("a"))
        assertEquals(listOf(2L, 3L, 1L), out.map { it.id })
    }

    @Test fun dropsUninstalledAppsAndItemsWithoutLink() {
        val out = ContinueWatching.pick(listOf(item(1, pkg = "gone"), item(2, link = null), item(3, link = " "), item(4)), setOf("a"))
        assertEquals(listOf(4L), out.map { it.id })
    }

    @Test fun oneCardForEachTitleOfAnApp() {
        val out = ContinueWatching.pick(
            listOf(item(1, title = "Show", at = 5), item(2, title = "show ", at = 9), item(3, pkg = "b", title = "Show", at = 1)),
            setOf("a", "b"),
        )
        assertEquals(listOf(2L, 3L), out.map { it.id })
    }

    @Test fun keepsTheLimit() {
        val out = ContinueWatching.pick((1L..20L).map { item(it) }, setOf("a"), limit = 5)
        assertEquals(5, out.size)
    }

    @Test fun progressNeedsPositionAndLength() {
        assertNull(item(1, pos = 100, len = 0).progress)
        assertNull(item(1, pos = 0, len = 100).progress)
        assertEquals(0.25f, item(1, pos = 25, len = 100).progress!!, 0.001f)
        assertEquals(1f, item(1, pos = 500, len = 100).progress!!, 0.001f)
    }
}
