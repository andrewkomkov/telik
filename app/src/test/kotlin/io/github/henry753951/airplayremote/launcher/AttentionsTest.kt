package io.github.henry753951.airplayremote.launcher

import io.github.henry753951.airplayremote.runtime.AirPlayService.ServerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttentionsTest {
    @Test fun missingLocalNetworkComesFirst() {
        assertEquals(Attention.LOCAL_NETWORK, Attentions.pick(false, ServerState.ERROR, true, "error"))
    }

    @Test fun runningServerHidesLocalNetwork() {
        assertNull(Attentions.pick(false, ServerState.RUNNING, true, "ready"))
    }

    @Test fun serverErrorAndStop() {
        assertEquals(Attention.AIRPLAY_ERROR, Attentions.pick(true, ServerState.ERROR, true, ""))
        assertEquals(Attention.AIRPLAY_OFF, Attentions.pick(true, ServerState.STOPPED, true, ""))
    }

    @Test fun remoteProblemsOnlyWhenRemoteIsOn() {
        assertEquals(Attention.NETWORK, Attentions.pick(true, ServerState.RUNNING, true, "waiting"))
        assertEquals(Attention.REMOTE_ERROR, Attentions.pick(true, ServerState.RUNNING, true, "error"))
        assertNull(Attentions.pick(true, ServerState.RUNNING, false, "error"))
    }
}
