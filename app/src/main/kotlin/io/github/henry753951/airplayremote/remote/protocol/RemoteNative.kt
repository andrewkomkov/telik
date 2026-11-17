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
    /** Spec 006 US1: the launchable apps for the iPhone remote. */
    external fun nativeSetApps(ids: Array<String>, names: Array<String>)
    /** Spec 006 US2: the media control flags of the active media session. */
    external fun nativeSetMedia(flags: Long)
    /** Spec 008: the now playing card of the iOS remote. A null title clears it. */
    external fun nativeSetNowPlaying(
        title: String?, subtitle: String?, bundle: String?, durationMs: Long, positionMs: Long, playing: Boolean, jpeg: ByteArray?,
    )
    external fun nativeAdvertiseHomeKit(name: String, ip: String, port: Int, txt: String): Boolean
}
