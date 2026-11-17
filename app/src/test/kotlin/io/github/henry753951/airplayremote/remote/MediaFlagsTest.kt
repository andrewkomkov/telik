package io.github.henry753951.airplayremote.remote

import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaFlagsTest {
    @Test fun volumeOnlyWithoutSession() {
        assertEquals(MediaFlags.VOLUME, MediaFlags.of(PlaybackState.ACTION_SKIP_TO_NEXT, hasSession = false))
    }

    @Test fun playAndPauseWithAnySession() {
        assertEquals(MediaFlags.VOLUME or MediaFlags.PLAY or MediaFlags.PAUSE, MediaFlags.of(0, hasSession = true))
    }

    @Test fun skipAndSeekFollowTheActions() {
        val actions = PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND
        val f = MediaFlags.of(actions, hasSession = true)
        assertEquals(
            MediaFlags.VOLUME or MediaFlags.PLAY or MediaFlags.PAUSE or MediaFlags.NEXT or MediaFlags.PREVIOUS or
                MediaFlags.FAST_FORWARD or MediaFlags.REWIND,
            f,
        )
    }

    @Test fun valuesMatchPyatv() {
        // pyatv MediaControlFlags: Play 0x1, Pause 0x2, NextTrack 0x4, PreviousTrack 0x8, Volume 0x100.
        assertEquals(0x1L, MediaFlags.PLAY)
        assertEquals(0x2L, MediaFlags.PAUSE)
        assertEquals(0x4L, MediaFlags.NEXT)
        assertEquals(0x8L, MediaFlags.PREVIOUS)
        assertEquals(0x100L, MediaFlags.VOLUME)
    }
}
