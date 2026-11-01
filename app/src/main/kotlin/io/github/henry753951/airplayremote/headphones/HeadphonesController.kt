package io.github.henry753951.airplayremote.headphones

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import io.github.henry753951.airplayremote.Prefs
import io.github.henry753951.airplayremote.get
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HeadphonesStatus { IDLE, NEEDS_APPROVAL, NO_DEBUGGING, NOT_PAIRED, CONNECTING, CONNECTED, NOT_REACHED }

/** Connects paired AirPods when the screen turns on, or on request. */
@SuppressLint("MissingPermission")
class HeadphonesController private constructor(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
    private val worker = Handler(HandlerThread("Headphones").apply { start() }.looper)
    private val shell = LocalShell(app)
    private val adapter = app.getSystemService(BluetoothManager::class.java).adapter
    private var a2dp: BluetoothA2dp? = null
    private var lastAttempt = 0L
    private val checkAttempt = Runnable {
        if (mutableStatus.value == HeadphonesStatus.CONNECTING) mutableStatus.value = HeadphonesStatus.NOT_REACHED
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Prefs.HEADPHONES_AUTO_CONNECT.key) worker.post { onSettingChanged() }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            worker.post { if (prefs[Prefs.HEADPHONES_AUTO_CONNECT]) connect(force = false) }
        }
    }

    private val a2dpReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            worker.post { refresh() }
        }
    }

    private val hasPermission get() = app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun start() {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        app.registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        worker.post { onSettingChanged() }
    }

    private fun onSettingChanged() {
        if (prefs[Prefs.HEADPHONES_AUTO_CONNECT] && !ensurePermission()) return
        refresh()
    }

    private fun toggle() {
        worker.post {
            if (mutableStatus.value == HeadphonesStatus.CONNECTING) return@post
            val device = if (hasPermission) target() else null
            if (device != null && isConnected(device)) runTool("disconnect", device) else connect(force = true)
        }
    }

    /** Asks adb for the Bluetooth permission. The system approval dialog appears on first use. */
    private fun ensurePermission(): Boolean {
        if (hasPermission) return true
        val grant = "pm grant ${app.packageName} ${Manifest.permission.BLUETOOTH_CONNECT}"
        return outputOf(shell.run(grant)) != null && hasPermission
    }

    private fun outputOf(result: ShellResult): String? = when (result) {
        is ShellResult.Done -> result.output
        ShellResult.NoDebugging -> { mutableStatus.value = HeadphonesStatus.NO_DEBUGGING; null }
        ShellResult.NeedsApproval -> { mutableStatus.value = HeadphonesStatus.NEEDS_APPROVAL; null }
    }

    private fun attachBluetooth() {
        if (a2dp != null || adapter == null) return
        adapter.getProfileProxy(app, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                a2dp = proxy as BluetoothA2dp
                worker.post { refresh() }
            }
            override fun onServiceDisconnected(profile: Int) {
                a2dp = null
            }
        }, BluetoothProfile.A2DP)
        app.registerReceiver(a2dpReceiver, IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED), Context.RECEIVER_EXPORTED)
    }

    private fun target(): BluetoothDevice? =
        adapter?.bondedDevices?.firstOrNull { it.name?.contains(AIRPODS_NAME, ignoreCase = true) == true }

    private fun isConnected(device: BluetoothDevice) =
        a2dp?.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED

    private fun refresh() {
        if (!hasPermission) return
        attachBluetooth()
        val device = target()
        when {
            device == null -> mutableStatus.value = HeadphonesStatus.NOT_PAIRED
            isConnected(device) -> {
                worker.removeCallbacks(checkAttempt)
                mutableStatus.value = HeadphonesStatus.CONNECTED
            }
            mutableStatus.value == HeadphonesStatus.CONNECTED || mutableStatus.value == HeadphonesStatus.NOT_PAIRED ->
                mutableStatus.value = HeadphonesStatus.IDLE
        }
    }

    private fun connect(force: Boolean) {
        if (!ensurePermission()) return
        attachBluetooth()
        val device = target()
        if (device == null) {
            mutableStatus.value = HeadphonesStatus.NOT_PAIRED
            return
        }
        if (isConnected(device)) {
            refresh()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastAttempt < ATTEMPT_COOLDOWN_MS) return
        lastAttempt = now
        mutableStatus.value = HeadphonesStatus.CONNECTING
        if (runTool("connect", device)) {
            worker.removeCallbacks(checkAttempt)
            worker.postDelayed(checkAttempt, CONNECT_TIMEOUT_MS)
        } else if (mutableStatus.value == HeadphonesStatus.CONNECTING) {
            mutableStatus.value = HeadphonesStatus.NOT_REACHED
        }
    }

    private fun runTool(method: String, device: BluetoothDevice): Boolean {
        val command = "CLASSPATH='${app.applicationInfo.sourceDir}' app_process /system/bin " +
            "${HeadphonesTool::class.java.name} $method '${device.address}'"
        val output = outputOf(shell.run(command)) ?: return false
        Log.i(TAG, "$method: ${output.trim()}")
        return output.contains("result=0")
    }

    companion object {
        private const val TAG = "Headphones"
        private const val AIRPODS_NAME = "AirPods"
        private const val ATTEMPT_COOLDOWN_MS = 20_000L
        private const val CONNECT_TIMEOUT_MS = 15_000L

        private val mutableStatus = MutableStateFlow(HeadphonesStatus.IDLE)
        val status: StateFlow<HeadphonesStatus> = mutableStatus.asStateFlow()

        @Volatile
        private var instance: HeadphonesController? = null

        @Synchronized
        fun start(context: Context) {
            if (instance == null) instance = HeadphonesController(context).also { it.start() }
        }

        fun toggleConnection() {
            instance?.toggle()
        }
    }
}
