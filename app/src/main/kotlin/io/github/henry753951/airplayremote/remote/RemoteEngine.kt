package io.github.henry753951.airplayremote.remote

import dev.tvhomekit.HomeKitService
import io.github.henry753951.airplayremote.remote.identity.RemoteIdentityStore
import io.github.henry753951.airplayremote.platform.LocalNetworkAddress
import io.github.henry753951.airplayremote.remote.input.KeyDispatcher
import io.github.henry753951.airplayremote.remote.input.LocalAdbDriver
import io.github.henry753951.airplayremote.remote.input.AccessibilityRestore
import io.github.henry753951.airplayremote.remote.input.RemoteAccessibilityService
import io.github.henry753951.airplayremote.remote.protocol.RemoteNative

import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log

/** Shares the AirPlay foreground service and power policy; native lifecycle is serialized. */
class RemoteEngine(
    context: Context,
    private val onActivity: (Boolean) -> Unit,
    private val onPairingRequested: () -> Unit,
) : ContextWrapper(context), RemoteNative.Callback {
    private val thread = HandlerThread("RemoteLifecycle").apply { start() }
    private val worker = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private val cm = getSystemService(ConnectivityManager::class.java)
    @Volatile private var enabled = false
    @Volatile private var closed = false
    private var advertised: Pair<String, String>? = null
    private var multicast: WifiManager.MulticastLock? = null
    private var clients = 0
    private var attempts = 0
    private val retry = Runnable { refresh() }
    private val clearPairing = Runnable { RemoteState.mutablePin.value = null }
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { scheduleRefresh() }
        override fun onLost(network: Network) { scheduleRefresh() }
        override fun onLinkPropertiesChanged(network: Network, props: LinkProperties) { scheduleRefresh() }
    }
    init {
        try { cm.registerDefaultNetworkCallback(callback) }
        catch (e: RuntimeException) { Log.w("RemoteEngine", "Network monitoring unavailable", e) }
    }
    private fun scheduleRefresh() {
        if (!closed) worker.post { attempts = 0; refresh() }
    }
    fun start() {
        if (closed) return
        enabled = true
        scheduleRefresh()
    }
    fun stop() {
        enabled = false
        worker.post { stopNative() }
    }
    private fun refresh() {
        worker.removeCallbacks(retry)
        if (!enabled || closed) return
        val ip = LocalNetworkAddress.get(this)
        if (ip == null) {
            stopNative()
            RemoteState.mutableStatus.value = "waiting"
            return // Connectivity callbacks resume startup when the network is ready.
        }
        val identity = RemoteIdentityStore.getDeviceName(this) to ip
        if (identity == advertised && RemoteNative.nativeIsRunning()) return
        stopNative()
        RemoteState.mutableStatus.value = "starting"
        try {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicast = wifi.createMulticastLock("ReceiverRemoteDiscovery").apply {
                setReferenceCounted(false); acquire()
            }
            val id = RemoteIdentityStore.getDeviceId(this)
            val ok = RemoteNative.nativeStartServer(identity.first, RemoteIdentityStore.getPairingPin(this), id,
                RemoteIdentityStore.getServerIdentifier(this, id), ip, this, RemoteIdentityStore.getPrivateKey(this),
                createDeviceProtectedStorageContext().filesDir.absolutePath)
            if (!ok) error("Native receiver startup failed")
            advertised = identity
            attempts = 0
            KeyDispatcher.init(this)
            AccessibilityRestore.ensure(this)
            RemoteState.mutableStatus.value = "running"
        } catch (e: Exception) {
            Log.e("RemoteEngine", "Unable to start remote receiver", e)
            stopNative()
            RemoteState.mutableStatus.value = "error"
            if (++attempts <= 3) worker.postDelayed(retry, attempts * 5000L)
        }
    }
    private fun stopNative() {
        worker.removeCallbacks(retry)
        RemoteNative.nativeStopServer()
        advertised = null
        LocalAdbDriver.shutdown()
        multicast?.let { if (it.isHeld) it.release() }
        multicast = null
        clients = 0
        RemoteState.mutableConnected.value = false
        main.post { onActivity(false) }
        main.removeCallbacks(clearPairing)
        RemoteState.mutablePin.value = null
        RemoteState.mutablePaired.value = false
        RemoteState.mutableStatus.value = "stopped"
    }
    override fun onButton(name: String) {
        if (!enabled || closed) return
        Log.i("RemoteEngine", "Button: $name")
        if (!handlePowerButton(name)) KeyDispatcher.dispatchButton(name)
    }

    /** The first press on a sleeping TV only wakes it, as on an Apple TV. Volume does not wake it. */
    private fun handlePowerButton(name: String): Boolean {
        val tv = HomeKitService.tv ?: return false
        if (tv.isAsleep) {
            if (name in VOLUME_BUTTONS) return false
            if (name != "sleep") tv.setActive(true)
            return true
        }
        if (name == "sleep" || name == "power") {
            tv.setActive(false)
            return true
        }
        return name == "wake"
    }

    private fun wakeIfAsleep() {
        HomeKitService.tv?.takeIf { it.isAsleep }?.setActive(true)
    }

    override fun onTouch(dx: Double, dy: Double, phase: Int) {
        if (phase == TOUCH_BEGAN) wakeIfAsleep()
        RemoteAccessibilityService.instance?.handleTouch(dx, dy, phase)
    }
    override fun onAudio(volume: Double, muted: Boolean) = Unit
    override fun onEvent(kind: String, detail: String) {
        if (!enabled || closed) return
        logEvent(kind, detail)
        when (kind) {
            "RemoteReady" -> wakeIfAsleep()
            "ClientConnected", "ClientDisconnected" -> worker.post {
                clients = (clients + if (kind == "ClientConnected") 1 else -1).coerceAtLeast(0)
                RemoteState.mutableConnected.value = clients > 0
                if (kind == "ClientConnected") {
                    KeyDispatcher.init(this)
                    AccessibilityRestore.ensure(this)
                }
                main.post { onActivity(clients > 0) }
            }
            "PairingRequested" -> {
                RemoteState.mutablePaired.value = false
                RemoteState.mutablePin.value = RemoteIdentityStore.getPairingPin(this).toString()
                main.removeCallbacks(clearPairing)
                main.postDelayed(clearPairing, 120_000)
                main.post {
                    if (!enabled || closed) return@post
                    onPairingRequested()
                }
            }
            "Paired", "Verified" -> {
                RemoteState.mutablePaired.value = true
                RemoteState.mutablePin.value = null
            }
            "MouseClick" -> RemoteAccessibilityService.instance?.handleMouseClick()
            "TextInsert" -> main.post {
                android.util.Log.i("AtvText", "insert: ${detail.length} chars")
                RemoteAccessibilityService.instance?.insertText(detail)
            }
            "TextDelete" -> main.post {
                RemoteAccessibilityService.instance?.deleteBackward(detail.toIntOrNull() ?: 0)
            }
            "TextReplace" -> main.post {
                android.util.Log.i("AtvText", "replace: ${detail.length} chars")
                RemoteAccessibilityService.instance?.replaceText(detail)
            }
        }
    }
    /** Logs names only. Typed text stays out of the log. */
    private fun logEvent(kind: String, detail: String) {
        val name = when (kind) {
            "ControlMessage" -> CONTROL_NAME.find(detail)?.groupValues?.get(1)?.let { name ->
                when (name) {
                    "_hidC" -> "$name ${HID_CODE.find(detail)?.groupValues?.get(1)}/${HID_STATE.find(detail)?.groupValues?.get(1)}"
                    // the device model tells a watch from a phone; message names start with "_"
                    "_systemInfo" -> "$name " + MODEL.findAll(detail).map { it.groupValues[1] }
                        .filterNot { it.startsWith("_") }.distinct().joinToString(",")
                    else -> name
                }
            }
            "ClientDisconnected" -> detail.substringAfter("reason=", "")
            else -> null
        }
        Log.i("RemoteEngine", if (name == null) "Event: $kind" else "Event: $kind $name")
    }

    fun close() {
        closed = true
        enabled = false
        try { cm.unregisterNetworkCallback(callback) } catch (_: IllegalArgumentException) {}
        worker.post { stopNative(); thread.quitSafely() }
    }

    private companion object {
        const val TOUCH_BEGAN = 1
        val VOLUME_BUTTONS = setOf("volume_up", "volume_down", "mute")
        val CONTROL_NAME = Regex("\"(?:_i|model)\": \"([^\"]+)\"")
        val MODEL = Regex("\"model\": \"([^\"]+)\"")
        val HID_CODE = Regex("\"_hidC\": (\\d+)")
        val HID_STATE = Regex("\"_hBtS\": (\\d+)")
    }
}
