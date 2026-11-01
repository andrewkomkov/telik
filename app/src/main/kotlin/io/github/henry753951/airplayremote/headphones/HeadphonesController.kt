package io.github.henry753951.airplayremote.headphones

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import io.github.henry753951.airplayremote.Prefs
import io.github.henry753951.airplayremote.get
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HeadphonesStatus { IDLE, WAITING, NEEDS_APPROVAL, NO_DEBUGGING, NOT_PAIRED, CONNECTING, CONNECTED, NOT_REACHED }

/** Connects paired AirPods when the screen turns on, when they go on the head, or on request. */
@SuppressLint("MissingPermission")
class HeadphonesController private constructor(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
    private val worker = Handler(HandlerThread("Headphones").apply { start() }.looper)
    private val shell = LocalShell(app)
    private val trigger = WornTrigger()
    private val adapter = app.getSystemService(BluetoothManager::class.java).adapter
    private var a2dp: BluetoothA2dp? = null
    private var scanning = false
    private var lastAttempt = 0L
    private val checkAttempt = Runnable { if (mutableStatus.value == HeadphonesStatus.CONNECTING) mutableStatus.value = HeadphonesStatus.NOT_REACHED }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Prefs.HEADPHONES_AUTO_CONNECT.key) worker.post { onSettingChanged() }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            worker.post {
                if (intent.action == Intent.ACTION_SCREEN_ON && autoConnect) attempt(force = false) else refresh()
            }
        }
    }

    private val a2dpReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            worker.post { refresh() }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (result.rssi < MIN_RSSI) return
            val data = result.scanRecord?.getManufacturerSpecificData(AirPodsBeacon.APPLE_COMPANY_ID)
            val beacon = AirPodsBeacon.parse(data) ?: return
            worker.post { onBeacon(beacon) }
        }
    }

    private val autoConnect get() = prefs[Prefs.HEADPHONES_AUTO_CONNECT]

    private val hasPermissions get() = PERMISSIONS.all { app.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun start() {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        app.registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) })
        worker.post { onSettingChanged() }
    }

    private fun onSettingChanged() {
        if (autoConnect && !ensurePermissions()) return
        refresh()
    }

    private fun connectNow() {
        worker.post { attempt(force = true) }
    }

    /** Asks adb for the Bluetooth permissions. The system approval dialog appears on first use. */
    private fun ensurePermissions(): Boolean {
        if (hasPermissions) return true
        val grants = PERMISSIONS.joinToString(" && ") { "pm grant ${app.packageName} $it" }
        return handleShell(shell.run(grants)) != null && hasPermissions
    }

    private fun handleShell(result: ShellResult): String? = when (result) {
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
        if (!hasPermissions) {
            mutableStatus.value = HeadphonesStatus.IDLE
            return
        }
        attachBluetooth()
        val device = target()
        val connected = device != null && isConnected(device)
        when {
            connected -> {
                worker.removeCallbacks(checkAttempt)
                mutableStatus.value = HeadphonesStatus.CONNECTED
            }
            mutableStatus.value == HeadphonesStatus.CONNECTING -> Unit
            device == null -> mutableStatus.value = HeadphonesStatus.NOT_PAIRED
            mutableStatus.value != HeadphonesStatus.NOT_REACHED || autoConnect ->
                mutableStatus.value = if (autoConnect) HeadphonesStatus.WAITING else HeadphonesStatus.IDLE
        }
        val interactive = app.getSystemService(PowerManager::class.java).isInteractive
        setScanning(autoConnect && interactive && device != null && !connected)
    }

    private fun attempt(force: Boolean) {
        if (!ensurePermissions()) return
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
        val command = "CLASSPATH='${app.applicationInfo.sourceDir}' app_process /system/bin ${HeadphonesTool::class.java.name} '${device.address}'"
        val output = handleShell(shell.run(command)) ?: return
        Log.i(TAG, "Connection request: ${output.trim()}")
        if (output.contains("connect=0")) {
            worker.removeCallbacks(checkAttempt)
            worker.postDelayed(checkAttempt, CONNECT_TIMEOUT_MS)
        } else {
            mutableStatus.value = HeadphonesStatus.NOT_REACHED
        }
    }

    private fun onBeacon(beacon: AirPodsBeacon) {
        beacon.batteryPercent?.let { mutableBattery.value = it }
        if (trigger.onBeacon(beacon.worn, SystemClock.elapsedRealtime()) && autoConnect) attempt(force = false)
    }

    private fun setScanning(on: Boolean) {
        if (on == scanning) return
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return
        try {
            if (on) {
                val filter = ScanFilter.Builder()
                    .setManufacturerData(AirPodsBeacon.APPLE_COMPANY_ID, AirPodsBeacon.PREFIX, AirPodsBeacon.PREFIX_MASK)
                    .build()
                val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
                scanner.startScan(listOf(filter), settings, scanCallback)
            } else {
                scanner.stopScan(scanCallback)
            }
            scanning = on
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Bluetooth scan is not available", e)
        }
    }

    companion object {
        private const val TAG = "Headphones"
        private const val AIRPODS_NAME = "AirPods"
        private const val MIN_RSSI = -70
        private const val ATTEMPT_COOLDOWN_MS = 20_000L
        private const val CONNECT_TIMEOUT_MS = 15_000L
        private val PERMISSIONS = listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)

        private val mutableStatus = MutableStateFlow(HeadphonesStatus.IDLE)
        private val mutableBattery = MutableStateFlow<Int?>(null)
        val status: StateFlow<HeadphonesStatus> = mutableStatus.asStateFlow()
        val batteryPercent: StateFlow<Int?> = mutableBattery.asStateFlow()

        @Volatile
        private var instance: HeadphonesController? = null

        @Synchronized
        fun start(context: Context) {
            if (instance == null) instance = HeadphonesController(context).also { it.start() }
        }

        fun connectNow() {
            instance?.connectNow()
        }
    }
}
