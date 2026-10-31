package dev.tvhomekit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.Network
import android.net.LinkProperties
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Base64
import android.util.Log
import io.github.henry753951.airplayremote.platform.LocalNetworkAddress
import io.github.henry753951.airplayremote.remote.protocol.RemoteNative
import java.security.MessageDigest

private const val TAG = "TvHomeKit"
private const val CHANNEL = "homekit"
private const val PORT = 51826
private const val CATEGORY_TELEVISION = 31
private const val RETRY_MS = 5_000L
private const val ADB_WIFI = "adb_wifi_enabled"

/** Включается только вручную: adb shell settings put global telik_keep_wireless_debugging 1 */
private const val KEEP_DEBUGGING = "telik_keep_wireless_debugging"

/** Держит сервер HomeKit живым: стартует с системой, переживает сон экрана и смену сети. */
class HomeKitService : Service() {
    private lateinit var store: Store
    private var server: HapServer? = null
    private var configIndex = 1
    private var advertised: String? = null
    private val retry = Runnable { advertise() }
    private val restoreDebugging = Runnable { keepWirelessDebugging() }
    private val main = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            tv?.onScreenChanged()
        }
    }

    private val debuggingObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            scheduleDebuggingCheck()
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            advertise()
            scheduleDebuggingCheck()
        }

        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
            advertise()
            scheduleDebuggingCheck()
        }

        override fun onLost(network: Network) {
            advertise()
        }
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "HomeKit", NotificationManager.IMPORTANCE_MIN),
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("HomeKit TV")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tvhomekit:server").apply { acquire() }
        @Suppress("DEPRECATION")
        wifiLock = getSystemService(WifiManager::class.java)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "tvhomekit:wifi").apply { acquire() }

        registerReceiver(
            screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) },
        )
        keepNetworkInStandby()
        contentResolver.registerContentObserver(Settings.Global.getUriFor(ADB_WIFI), false, debuggingObserver)
        contentResolver.registerContentObserver(Settings.Global.getUriFor(KEEP_DEBUGGING), false, debuggingObserver)
        scheduleDebuggingCheck()

        status = "Запуск"
        try {
            store = Store(this)
            val accessory = TvAccessory(this, store)
            configIndex = store.configurationIndex(accessory.fingerprint())
            val hap = HapServer(store, accessory.database()) { advertise() }
            accessory.notifier = hap::notifyChanged
            hap.start(PORT)
            server = hap
            tv = accessory
            status = "Работает"
            Log.i(TAG, "Сервер запущен на порту $PORT")
        } catch (e: Exception) {
            status = "Ошибка: $e"
            Log.e(TAG, "Сервер не запустился", e)
        }
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback)
    }

    /** С включённым режимом телевизор пропадает из сети примерно через минуту сна. Разрешение выдаётся через adb. */
    private fun keepNetworkInStandby() {
        try {
            Settings.Global.putInt(contentResolver, "low_power_standby_enabled", 0)
        } catch (e: SecurityException) {
            Log.w(TAG, "Нет разрешения WRITE_SECURE_SETTINGS")
        }
    }

    private fun scheduleDebuggingCheck() {
        main.removeCallbacks(restoreDebugging)
        main.postDelayed(restoreDebugging, RETRY_MS)
    }

    /** Android выключает беспроводную отладку при обрыве Wi-Fi и перезагрузке. Сопряжение при этом сохраняется. */
    private fun keepWirelessDebugging() {
        if (Settings.Global.getInt(contentResolver, KEEP_DEBUGGING, 0) != 1) return
        if (LocalNetworkAddress.get(this) == null) return
        if (Settings.Global.getInt(contentResolver, ADB_WIFI, 0) == 1) return
        try {
            Settings.Global.putInt(contentResolver, ADB_WIFI, 1)
            Log.i(TAG, "Беспроводная отладка включена снова")
        } catch (e: SecurityException) {
            Log.w(TAG, "Нет разрешения WRITE_SECURE_SETTINGS")
        }
    }

    /** Тот же mdns-sd, что и у пульта: он восстанавливается после обрыва Wi-Fi. */
    @Synchronized
    private fun advertise(force: Boolean = false) {
        if (server == null) return
        main.removeCallbacks(retry)
        val ip = LocalNetworkAddress.get(this)
        if (ip == null) {
            advertised = null
            return
        }
        val txt = listOf(
            "c#=$configIndex",
            "ff=0",
            "id=${store.mac}",
            "md=Telik",
            "pv=1.1",
            "s#=1",
            "sf=${if (store.hasUser()) "0" else "1"}",
            "ci=$CATEGORY_TELEVISION",
            "sh=${setupHash()}",
        ).joinToString("\n")
        val state = "$ip\n$txt"
        if (!force && state == advertised) return
        if (RemoteNative.nativeAdvertiseHomeKit("Телик", ip, PORT, txt)) {
            advertised = state
            Log.i(TAG, "Объявлен в сети на $ip")
        } else {
            advertised = null
            main.postDelayed(retry, RETRY_MS)
        }
    }

    private fun setupHash(): String {
        val hash = MessageDigest.getInstance("SHA-512").digest((store.setupId + store.mac).toByteArray())
        return Base64.encodeToString(hash.copyOf(4), Base64.NO_WRAP)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @JvmField
        @Volatile
        var status: String = "Запуск"

        @Volatile
        var tv: TvAccessory? = null

        @JvmStatic
        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, HomeKitService::class.java))
            } catch (e: RuntimeException) {
                Log.e(TAG, "Сервис не запущен", e)
            }
        }
    }
}
