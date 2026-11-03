package io.github.henry753951.airplayremote.remote.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessibilityRestoreTest {
    private val ours = "dev.telik.tv/com.corvofeng.fakeatv.AtvAccessibilityService"

    @Test fun emptyListGetsOurs() {
        assertEquals(ours, AccessibilityRestore.merged(null, ours))
        assertEquals(ours, AccessibilityRestore.merged("", ours))
    }

    @Test fun otherServicesStay() {
        assertEquals("a.b/.X:$ours", AccessibilityRestore.merged("a.b/.X", ours))
    }

    @Test fun presentServiceChangesNothing() {
        assertNull(AccessibilityRestore.merged("a.b/.X:$ours", ours))
        assertNull(AccessibilityRestore.merged("x.y/.Z", "x.y/x.y.Z"))
    }
}
