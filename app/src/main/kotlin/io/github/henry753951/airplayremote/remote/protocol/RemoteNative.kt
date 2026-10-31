package io.github.henry753951.airplayremote.remote.protocol

object RemoteNative {
    init {
        try {
            System.loadLibrary("atv_android")
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
        }
    }

    interface Callback {
        fun onButton(name: String)
        fun onTouch(dx: Double, dy: Double, phase: Int)
        fun onAudio(volume: Double, muted: Boolean)
        fun onEvent(kind: String, detail: String)
    }

    external fun nativeStartServer(
        name: String,
        pin: Int,
        deviceId: String,
        serverId: String,
        localIp: String,
        callback: Callback,
        privateKey: ByteArray,
        storageDir: String
    ): Boolean

    external fun nativeStopServer()
    external fun nativeIsRunning(): Boolean
    external fun nativeSetTextFocus(focused: Boolean, text: String)
}
