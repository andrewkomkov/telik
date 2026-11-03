package io.github.henry753951.airplayremote.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesTest {
    @Test fun toggleAddsAndRemoves() {
        val one = Favorites.toggle(emptyList(), "app:a")
        assertEquals(listOf("app:a"), one)
        assertEquals(emptyList<String>(), Favorites.toggle(one, "app:a"))
    }

    @Test fun moveKeepsOtherOrder() {
        assertEquals(listOf("b", "a", "c"), Favorites.move(listOf("a", "b", "c"), "a", 1))
    }

    @Test fun presentDropsMissingTargets() {
        val keys = listOf("app:a", "app:gone", "input:hdmi1", "input:off", "telik:CAST", "bad")
        assertEquals(
            listOf("app:a", "input:hdmi1", "telik:CAST"),
            Favorites.present(keys, setOf("a"), setOf("hdmi1"), setOf("CAST")),
        )
    }

    @Test fun suggestedByCountThenRecency() {
        val log = listOf(
            Launch("a", 10, 1), Launch("b", 20, 3), Launch("c", 30, 3), Launch("gone", 40, 9),
        )
        assertEquals(listOf("app:c", "app:b", "app:a"), Favorites.suggested(log, setOf("a", "b", "c")))
    }

    @Test fun recordCountsAndCaps() {
        var log = emptyList<Launch>()
        log = LaunchLog.record(log, "a", 1)
        log = LaunchLog.record(log, "b", 2)
        log = LaunchLog.record(log, "a", 3)
        assertEquals(listOf(Launch("a", 3, 2), Launch("b", 2, 1)), log)
        repeat(LaunchLog.LIMIT + 5) { log = LaunchLog.record(log, "p$it", 10L + it) }
        assertEquals(LaunchLog.LIMIT, log.size)
    }

    @Test fun formatParseRoundTrip() {
        val log = listOf(Launch("a", 3, 2), Launch("b", 2, 1))
        assertEquals(log, LaunchLog.parse(LaunchLog.format(log)))
        assertEquals(emptyList<Launch>(), LaunchLog.parse("broken\nline"))
    }
}
