package io.github.henry753951.airplayremote.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local UI state; pairing secrets never leave app storage or the pairing screen. */
object RemoteState {
    internal val mutableStatus = MutableStateFlow("stopped")
    val status = mutableStatus.asStateFlow()
    internal val mutablePin = MutableStateFlow<String?>(null)
    val pin = mutablePin.asStateFlow()
    internal val mutablePaired = MutableStateFlow(false)
    val paired = mutablePaired.asStateFlow()
    internal val mutableConnected = MutableStateFlow(false)
    val connected = mutableConnected.asStateFlow()
    fun dismissPin() { mutablePin.value = null }
    /** 008: the package of the window in front, from the accessibility service. */
    internal val foreground = MutableStateFlow<String?>(null)
}
