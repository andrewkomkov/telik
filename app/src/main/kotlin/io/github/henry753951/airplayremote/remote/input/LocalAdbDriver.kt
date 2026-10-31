package io.github.henry753951.airplayremote.remote.input

import android.content.Context

/** Локальный adb убран: после перезагрузки он выключается, кнопки идут через службу специальных возможностей. */
object LocalAdbDriver {
    object Readiness {
        fun get() = RemoteAccessibilityService.isServiceRunning
    }

    val isConnected = Readiness

    fun init(context: Context) = Unit

    fun shutdown() = Unit

    fun sendKey(buttonName: String, isMappedToMenu: Boolean = false) = false
}
