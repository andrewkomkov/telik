package io.github.henry753951.airplayremote.remote

import android.media.session.PlaybackState

/**
 * Media control flags of the iPhone remote (spec 006 US2), from the actions of the active media
 * session. Values follow pyatv `MediaControlFlags`, as `atv-core/src/tv_state.rs` does.
 */
object MediaFlags {
    const val PLAY = 0x0001L
    const val PAUSE = 0x0002L
    const val NEXT = 0x0004L
    const val PREVIOUS = 0x0008L
    const val FAST_FORWARD = 0x0010L
    const val REWIND = 0x0020L
    const val VOLUME = 0x0100L

    /** Volume only without a session. Play and pause always with one: many apps do not declare them. */
    fun of(actions: Long, hasSession: Boolean): Long {
        if (!hasSession) return VOLUME
        var f = VOLUME or PLAY or PAUSE
        if (actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L) f = f or NEXT
        if (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L) f = f or PREVIOUS
        if (actions and PlaybackState.ACTION_FAST_FORWARD != 0L) f = f or FAST_FORWARD
        if (actions and PlaybackState.ACTION_REWIND != 0L) f = f or REWIND
        return f
    }
}
