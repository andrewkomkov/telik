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
import android.net.ConnectivityManager
import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Base64
import android.util.Log
import java.security.MessageDigest

private const val TAG = "TvHomeKit"
private const val CHANNEL = "homekit"
private const val PORT = 51826
private const val CATEGORY_TELEVISION = 31
private const val RETRY_MS = 5_000L

/** Держит сервер HomeKit живым: стартует с системой, переживает сон экрана и смену сети. */
class HomeKitService : Service() {
    private lateinit var store: Store
    private var server: HapServer? = null
    private var configIndex = 1
    private var nsd: NsdManager? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var advertisePending = false
    private val main = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            tv?.onScreenChanged()
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
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
        nsd = getSystemService(NsdManager::class.java)

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

    /** NsdManager не даёт повторно зарегистрировать службу, пока старая не снята. */
    @Synchronized
    private fun advertise() {
        if (server == null) return
        val current = registration
        if (current == null) {
            register()
        } else if (!advertisePending) {
            advertisePending = true
            runCatching { nsd?.unregisterService(current) }.onFailure {
                advertisePending = false
                registration = null
                register()
            }
        }
    }

    private fun register() {
        val info = NsdServiceInfo().apply {
            serviceName = "Телик"
            serviceType = "_hap._tcp"
            port = PORT
            setAttribute("c#", configIndex.toString())
            setAttribute("ff", "0")
            setAttribute("id", store.mac)
            setAttribute("md", "Telik")
            setAttribute("pv", "1.1")
            setAttribute("s#", "1")
            setAttribute("sf", if (store.hasUser()) "0" else "1")
            setAttribute("ci", CATEGORY_TELEVISION.toString())
            setAttribute("sh", setupHash())
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "Объявлен в сети как ${info.serviceName}")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) {
                Log.w(TAG, "Не удалось объявить службу: $code")
                synchronized(this@HomeKitService) { registration = null }
                main.postDelayed({ advertise() }, RETRY_MS)
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                synchronized(this@HomeKitService) {
                    registration = null
                    if (advertisePending) {
                        advertisePending = false
                        register()
                    }
                }
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {
                Log.w(TAG, "Не удалось снять службу: $code")
                synchronized(this@HomeKitService) {
                    advertisePending = false
                    registration = null
                    register()
                }
            }
        }
        registration = listener
        nsd?.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
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
