package dev.tvhomekit

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import io.github.henry753951.airplayremote.remote.input.RemoteAccessibilityService

private const val TAG = "TvHomeKit"
private const val HOME_INPUT = 1
private const val MAX_INPUTS = 40
private val HIDDEN = setOf("com.mediatek.androidbox", "com.android.vending", "com.google.android.play.games")

private const val ACTIVE = 10
private const val ACTIVE_INPUT = 11
private const val TV_NAME = 12
private const val REMOTE_KEY = 13
private const val SLEEP_DISCOVERY = 14
private const val SPEAKER = 20
private const val MUTE = 21
private const val SPEAKER_ACTIVE = 22
private const val VOLUME = 23
private const val VOLUME_TYPE = 24
private const val VOLUME_SELECTOR = 25
private const val TV_SERVICE = 9
private const val MESSAGE = 30
private const val MESSAGE_ON = 31
private const val MESSAGE_NAME = 32
private const val HDMI_PREFIX = "hdmi:"
private const val MESSAGE_MS = 8_000L

/** Телевизор глазами HomeKit: питание, входы-приложения, пульт и громкость. */
class TvAccessory internal constructor(context: Context, private val store: Store) {
    private val context = context.applicationContext
    private val audio = context.getSystemService(AudioManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val homePackages = hashSetOf("com.spocky.projengmenu")
    private val inputs = LinkedHashMap<Int, String>()
    private val labels = LinkedHashMap<Int, String>()
    private var currentInput = HOME_INPUT

    @Volatile
    internal var notifier: (Int) -> Unit = {}

    init {
        findInputs()
    }

    private fun findInputs() {
        val packages = context.packageManager
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packages.queryIntentActivities(home, 0).forEach { homePackages += it.activityInfo.packageName }
        labels[HOME_INPUT] = "Главный экран"
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val found = sortedMapOf<Int, Pair<String, String>>()
        for (info in packages.queryIntentActivities(launcher, 0)) {
            val name = info.activityInfo.packageName
            if (name in HIDDEN || name in homePackages || name == context.packageName) continue
            found[store.inputId(name)] = name to cleanLabel(info.loadLabel(packages), name)
        }
        found.entries.take(MAX_INPUTS).forEach { (id, app) ->
            inputs[id] = app.first
            labels[id] = app.second
        }
        // Входы HDMI после приложений (спека 006, US6): Дом и Siri переключают их как на Apple TV.
        val tv = context.getSystemService(TvInputManager::class.java)
        runCatching { tv.tvInputList }.getOrDefault(emptyList())
            .filter { it.isPassthroughInput && it.type == TvInputInfo.TYPE_HDMI }
            .forEach { info ->
                val id = store.inputId(HDMI_PREFIX + info.id)
                inputs[id] = HDMI_PREFIX + info.id
                labels[id] = cleanLabel(info.loadLabel(context), "HDMI")
            }
    }

    private fun cleanLabel(label: CharSequence, fallback: String): String {
        val clean = label.toString().replace(Regex("[^\\p{L}\\p{N} ]"), " ").trim().replace(Regex(" +"), " ")
        return clean.ifEmpty { fallback }
    }

    // v2: входы HDMI и выключатель сообщения. Новый отпечаток заставляет iPhone перечитать базу.
    internal fun fingerprint() = "v2" + inputs.toString()

    internal fun database(): HapDatabase {
        val inputServices = labels.entries.map { (id, label) -> inputService(id, label) }
        val speaker = speakerService()
        val television = HapService(
            TV_SERVICE,
            "D8",
            listOf(
                Characteristic(ACTIVE, "B0", "uint8", PR_PW_EV, { if (power.isInteractive) 1 else 0 }, { setActive(toInt(it) != 0) }),
                Characteristic(ACTIVE_INPUT, "E7", "uint32", PR_PW_EV, { currentInput }, { setInput(toInt(it)) }),
                Characteristic(TV_NAME, "E3", "string", PR_PW_EV, { store.name("tv", "Телик") }, { store.setName("tv", it.toString()) }),
                Characteristic(REMOTE_KEY, "E1", "uint8", listOf("pw"), write = { press(toInt(it)) }),
                Characteristic(SLEEP_DISCOVERY, "E8", "uint8", PR_EV, { 1 }),
            ),
            primary = true,
            linked = listOf(speaker.iid) + inputServices.map { it.iid },
        )
        return HapDatabase(listOf(information(), protocolInformation(), television, speaker, messageService()) + inputServices)
    }

    private fun information() = HapService(
        1,
        "3E",
        listOf(
            Characteristic(2, "14", "bool", listOf("pw"), write = {}),
            Characteristic(3, "20", "string", listOf("pr"), { "Dahua" }),
            Characteristic(4, "21", "string", listOf("pr"), { Build.MODEL }),
            Characteristic(5, "23", "string", listOf("pr"), { "Телик" }),
            Characteristic(6, "30", "string", listOf("pr"), { store.mac.replace(":", "") }),
            Characteristic(7, "52", "string", listOf("pr"), { "2.0" }),
        ),
    )

    private fun protocolInformation() = HapService(
        8,
        "A2",
        listOf(Characteristic(15, "37", "string", listOf("pr"), { "1.1.0" })),
    )

    private fun speakerService() = HapService(
        SPEAKER,
        "113",
        listOf(
            Characteristic(MUTE, "11A", "bool", PR_PW_EV, { audio.isStreamMute(AudioManager.STREAM_MUSIC) }, { setMute(toBoolean(it)) }),
            Characteristic(SPEAKER_ACTIVE, "B0", "uint8", PR_PW_EV, { 1 }, {}),
            Characteristic(VOLUME, "119", "uint8", PR_PW_EV, { volumePercent() }, { setVolume(toInt(it)) }),
            Characteristic(VOLUME_TYPE, "E9", "uint8", PR_EV, { 3 }),
            Characteristic(VOLUME_SELECTOR, "EA", "uint8", listOf("pw"), write = { adjustVolume(toInt(it)) }),
        ),
    )

    // Номера привязаны к постоянному id входа, чтобы новые приложения не сдвигали старые.
    private fun inputService(id: Int, label: String): HapService {
        val base = 1000 + id * 10
        return HapService(
            base,
            "D9",
            listOf(
                Characteristic(base + 1, "E3", "string", PR_PW_EV, { store.name("input$id", label) }, { store.setName("input$id", it.toString()) }),
                // InputSourceType: 1 главный экран, 3 HDMI, 10 приложение.
                Characteristic(base + 2, "DB", "uint8", PR_EV, { inputType(id) }),
                Characteristic(base + 3, "D6", "uint8", PR_PW_EV, { 1 }, {}),
                Characteristic(base + 4, "135", "uint8", PR_EV, { 0 }),
                Characteristic(base + 5, "E6", "uint32", listOf("pr"), { id }),
                Characteristic(base + 6, "23", "string", listOf("pr"), { label }),
            ),
        )
    }

    private fun inputType(id: Int): Int = when {
        id == HOME_INPUT -> 1
        inputs[id]?.startsWith(HDMI_PREFIX) == true -> 3
        else -> 10
    }

    // Выключатель «Сообщение Телика» (спека 006, US6): автоматизация Дома включает его, телевизор
    // показывает баннер с его именем из приложения Дом и сам выключает его.
    @Volatile
    private var messageOn = false

    private fun messageService() = HapService(
        MESSAGE,
        "49",
        listOf(
            Characteristic(MESSAGE_ON, "25", "bool", PR_PW_EV, { messageOn }, { if (toBoolean(it)) showMessage() }),
            Characteristic(MESSAGE_NAME, "E3", "string", PR_PW_EV, { messageText() }, { store.setName("message", it.toString()) }),
            Characteristic(MESSAGE_NAME + 1, "23", "string", listOf("pr"), { "Telik message" }),
        ),
    )

    private fun messageText() = store.name("message", "Сообщение Телика")

    private fun showMessage() {
        messageOn = true
        notifier(MESSAGE_ON)
        main.post {
            if (!power.isInteractive) wakeScreen()
            MessageBanner.show(context, messageText(), MESSAGE_MS)
        }
        main.postDelayed({
            messageOn = false
            notifier(MESSAGE_ON)
        }, MESSAGE_MS)
    }

    val isAsleep: Boolean get() = !power.isInteractive

    fun setActive(on: Boolean) {
        Log.i(TAG, "Питание: $on")
        main.post { if (on) wakeScreen() else global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        power.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "tvhomekit:wake",
        ).acquire(3000)
        main.postDelayed({
            if (!power.isInteractive) {
                Log.i(TAG, "Блокировка не включила экран, запускаю WakeActivity")
                startActivity(Intent(context, WakeActivity::class.java))
            }
        }, 500)
    }

    fun onScreenChanged() = notifier(ACTIVE)

    private fun setInput(id: Int) {
        Log.i(TAG, "Вход: $id")
        main.post {
            if (!power.isInteractive) wakeScreen()
            if (id == HOME_INPUT) {
                global(AccessibilityService.GLOBAL_ACTION_HOME)
            } else {
                inputs[id]?.let { target ->
                    if (target.startsWith(HDMI_PREFIX)) {
                        val uri = TvContract.buildChannelUriForPassthroughInput(target.removePrefix(HDMI_PREFIX))
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } else {
                        launch(target)
                    }
                }
            }
            currentInput = id
            notifier(ACTIVE_INPUT)
        }
    }

    private fun launch(packageName: String) {
        val packages = context.packageManager
        val intent = packages.getLeanbackLaunchIntentForPackage(packageName)
            ?: packages.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Log.w(TAG, "Нечего запускать: $packageName")
            return
        }
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
    }

    private fun startActivity(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            (RemoteAccessibilityService.instance ?: context).startActivity(intent)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Экран не открылся: ${intent.component}", e)
        }
    }

    fun onForegroundPackage(packageName: String) {
        val input = when {
            packageName in homePackages -> HOME_INPUT
            else -> inputs.entries.firstOrNull { it.value == packageName }?.key ?: return
        }
        if (input != currentInput) {
            currentInput = input
            notifier(ACTIVE_INPUT)
        }
    }

    private fun press(key: Int) {
        Log.i(TAG, "Кнопка: $key")
        main.post {
            when (key) {
                4 -> global(AccessibilityService.GLOBAL_ACTION_DPAD_UP)
                5 -> global(AccessibilityService.GLOBAL_ACTION_DPAD_DOWN)
                6 -> global(AccessibilityService.GLOBAL_ACTION_DPAD_LEFT)
                7 -> global(AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT)
                8 -> global(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER)
                9 -> global(AccessibilityService.GLOBAL_ACTION_BACK)
                10 -> global(AccessibilityService.GLOBAL_ACTION_HOME)
                11 -> global(AccessibilityService.GLOBAL_ACTION_KEYCODE_HEADSETHOOK)
                15 -> startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    private fun global(action: Int) {
        val service = RemoteAccessibilityService.instance
        if (service == null) {
            Log.w(TAG, "Служба специальных возможностей выключена")
        } else if (!service.performGlobalAction(action)) {
            Log.w(TAG, "Система отклонила действие $action")
        }
    }

    private fun volumePercent(): Int {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max == 0) 0 else audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
    }

    private fun setVolume(percent: Int) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(percent * max / 100f), AudioManager.FLAG_SHOW_UI)
        notifier(VOLUME)
    }

    private fun adjustVolume(selector: Int) {
        val direction = if (selector == 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        notifier(VOLUME)
    }

    private fun setMute(mute: Boolean) {
        val direction = if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        notifier(MUTE)
    }

    private companion object {
        val PR_PW_EV = listOf("pr", "pw", "ev")
        val PR_EV = listOf("pr", "ev")
    }
}
