package io.github.henry753951.airplayremote.remote

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.UserHandle
import android.util.Log
import android.view.KeyEvent
import io.github.henry753951.airplayremote.launcher.NowPlayingWatcher
import io.github.henry753951.airplayremote.remote.protocol.RemoteNative
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The TV side of the iPhone remote for spec 006: the app list (US1) and the media state (US2) go to
 * the native sessions, and the app and media commands of the iPhone come back here.
 */
class RemoteTvBridge(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val media = NowPlayingWatcher(context)
    private var scope: CoroutineScope? = null

    private val appsChanged = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = pushApps()
        override fun onPackageAdded(packageName: String, user: UserHandle) = pushApps()
        override fun onPackageChanged(packageName: String, user: UserHandle) = pushApps()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = pushApps()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = pushApps()
    }

    fun start() = main.post {
        if (scope != null) return@post
        pushApps()
        runCatching { launcherApps.registerCallback(appsChanged, main) }
        media.start()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { s ->
            s.launch {
                // An app with no media session, as kino.watch, still gets the media panel while it is
                // in front: its buttons go as keys (see mediaKey).
                kotlinx.coroutines.flow.combine(media.current, RemoteState.foreground) { np, front ->
                    MediaFlags.of(np?.actions ?: 0L, np != null || isPlayer(front))
                }
                    .distinctUntilChanged()
                    .collect { RemoteNative.nativeSetMedia(it) }
            }
            // 008: the now playing card. The art goes as a small JPEG, made off the main thread.
            s.launch {
                media.current.collect { np ->
                    if (np == null) {
                        RemoteNative.nativeSetNowPlaying(null, null, null, 0, 0, false, null)
                        return@collect
                    }
                    val jpeg = np.art?.let { art -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { jpeg(art) } }
                    RemoteNative.nativeSetNowPlaying(np.title, np.subtitle, np.packageName, np.durationMs, np.positionMs, np.playing, jpeg)
                }
            }
        }
    }

    fun stop() = main.post {
        scope?.cancel()
        scope = null
        runCatching { launcherApps.unregisterCallback(appsChanged) }
        media.stop()
        RemoteNative.nativeSetMedia(MediaFlags.VOLUME)
        RemoteNative.nativeSetNowPlaying(null, null, null, 0, 0, false, null)
    }

    private fun jpeg(art: android.graphics.Bitmap): ByteArray? = runCatching {
        val scale = minOf(1f, 600f / maxOf(art.width, art.height))
        val small = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(art, (art.width * scale).toInt(), (art.height * scale).toInt(), true) else art
        java.io.ByteArrayOutputStream().use { out ->
            small.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    }.getOrNull()

    /** The apps of the Home screen: the TV launcher category, one entry for each package. */
    private fun launchable(): List<Pair<String, String>> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .distinctBy { it.activityInfo.packageName }
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .sortedBy { it.second.lowercase() }
    }

    private fun pushApps() {
        val apps = runCatching { launchable() }.getOrDefault(emptyList())
        RemoteNative.nativeSetApps(apps.map { it.first }.toTypedArray(), apps.map { it.second }.toTypedArray())
        Log.i(TAG, "apps for the iPhone remote: ${apps.size}")
    }

    /** `_launchApp`: the native side checked that [packageName] is in the list. */
    fun launch(packageName: String) = main.post {
        val intent = context.packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Log.w(TAG, "no launch intent for $packageName")
            return@post
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        TvLaunch.start(context, intent)
    }

    /**
     * Play, pause, next and previous from the iPhone. The audio service gives the key to the
     * active media session, so the bridge needs no session access for the commands.
     */
    /** True for an app in front that is not a Home screen: it may play media with no session. */
    private fun isPlayer(pkg: String?): Boolean {
        if (pkg == null || pkg == context.packageName || pkg == "com.android.systemui") return false
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.queryIntentActivities(home, 0).none { it.activityInfo.packageName == pkg }
    }

    fun mediaKey(name: String): Boolean {
        val hasSession = media.current.value != null
        if (name.startsWith("media_skip:")) {
            val seconds = name.removePrefix("media_skip:").toDoubleOrNull() ?: return true
            // With no session, the arrows: most TV players skip on them.
            if (hasSession) main.post { media.skipBy(seconds) }
            else io.github.henry753951.airplayremote.remote.input.KeyDispatcher.dispatchButton(if (seconds > 0) "right" else "left")
            return true
        }
        if (!hasSession && (name == "media_play" || name == "media_pause")) {
            io.github.henry753951.airplayremote.remote.input.KeyDispatcher.dispatchButton("select")
            return true
        }
        val code = when (name) {
            "media_play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "media_pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "media_next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "media_previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return false
        }
        val audio = context.getSystemService(AudioManager::class.java)
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
        return true
    }

    private companion object {
        const val TAG = "RemoteTvBridge"
    }
}
