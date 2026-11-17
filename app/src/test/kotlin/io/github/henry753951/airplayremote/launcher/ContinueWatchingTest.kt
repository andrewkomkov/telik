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

class AppPreviewsTest {
    private fun p(pkg: String, title: String, w: Int, art: String? = "https://x/a.jpg") = PreviewItem(pkg, title, art, "intent:#Intent;end", w)

    @Test fun groupsByAppBestWeightFirst() {
        val out = AppPreviews.pick(listOf(p("yt", "A", 1), p("yt", "B", 9), p("nf", "C", 5), p("gone", "D", 9)), setOf("yt", "nf"))
        assertEquals(listOf("B", "A"), out["yt"]!!.map { it.title })
        assertEquals(listOf("C"), out["nf"]!!.map { it.title })
        assertEquals(null, out["gone"])
    }

    @Test fun dropsItemsWithoutArtAndDuplicates() {
        val out = AppPreviews.pick(listOf(p("yt", "A", 1, art = null), p("yt", "Show", 3), p("yt", "show ", 2)), setOf("yt"))
        assertEquals(listOf("Show"), out["yt"]!!.map { it.title })
    }

    @Test fun shownChannelsFirst() {
        val hidden = p("yt", "Hidden", 99).copy(channelShown = false)
        val out = AppPreviews.pick(listOf(hidden, p("yt", "Shown", 1)), setOf("yt"))
        assertEquals(listOf("Shown", "Hidden"), out["yt"]!!.map { it.title })
    }

    @Test fun keepsTheLimit() {
        val out = AppPreviews.pick((1..20).map { p("yt", "T$it", it) }, setOf("yt"))
        assertEquals(AppPreviews.PER_APP, out["yt"]!!.size)
    }
}
