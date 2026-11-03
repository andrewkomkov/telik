package io.github.henry753951.airplayremote.launcher

import io.github.henry753951.airplayremote.runtime.AirPlayService.ServerState

/**
 * A problem that the Home screen shows with one action to fix it (FR-019).
 * The order is the order of the old Telik start page: the first match wins.
 */
enum class Attention { LOCAL_NETWORK, AIRPLAY_ERROR, AIRPLAY_OFF, NETWORK, REMOTE_ERROR }

object Attentions {
    /** [remote] is the status text of the remote service: "waiting" has no network, "error" failed. */
    fun pick(localNetwork: Boolean, server: ServerState, remoteEnabled: Boolean, remote: String): Attention? = when {
        server != ServerState.RUNNING && !localNetwork -> Attention.LOCAL_NETWORK
        server == ServerState.ERROR -> Attention.AIRPLAY_ERROR
        server == ServerState.STOPPED -> Attention.AIRPLAY_OFF
        // the remote is the only part that reports the network; without one AirPlay can't be found either
        remoteEnabled && remote == "waiting" -> Attention.NETWORK
        remoteEnabled && remote == "error" -> Attention.REMOTE_ERROR
        else -> null
    }
}
