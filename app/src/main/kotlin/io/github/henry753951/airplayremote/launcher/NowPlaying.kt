package io.github.henry753951.airplayremote.launcher

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Only a key for MediaSessionManager.getActiveSessions: the system gives the sessions of all
 * apps to an enabled notification listener. Telik reads no notifications (005 FR-012).
 */
class NowPlayingListener : NotificationListenerService()

/** What the now playing card shows. The title never goes to the log (005 FR-012). */
data class NowPlaying(
    val packageName: String,
    val title: String,
    val subtitle: String,
    val art: Bitmap?,
    val playing: Boolean,
    val canNext: Boolean,
)

class NowPlayingWatcher(private val context: Context) {
    private val listener = ComponentName(context, NowPlayingListener::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private var controllers: List<MediaController> = emptyList()
    private val _current = MutableStateFlow<NowPlaying?>(null)
    val current: StateFlow<NowPlaying?> = _current
    /** The system gives the sessions only to a listener that the user allowed. */
    private val _granted = MutableStateFlow(true)
    val granted: StateFlow<Boolean> = _granted

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onSessionDestroyed() = refresh()
    }
    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list -> watch(list.orEmpty()) }

    fun start() {
        _granted.value = context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(listener)
        if (!_granted.value) return
        try {
            manager.addOnActiveSessionsChangedListener(sessionsChanged, listener, main)
            watch(manager.getActiveSessions(listener))
        } catch (e: SecurityException) {
            _granted.value = false
        }
    }

    fun stop() {
        runCatching { manager.removeOnActiveSessionsChangedListener(sessionsChanged) }
        controllers.forEach { it.unregisterCallback(callback) }
        controllers = emptyList()
        _current.value = null
    }

    fun playPause() {
        val c = active() ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() { active()?.transportControls?.skipToNext() }

    private fun watch(list: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(callback) }
        controllers = list
        list.forEach { it.registerCallback(callback, main) }
        refresh()
    }

    /** The system lists the sessions with the last active first. */
    private fun active(): MediaController? = controllers.firstOrNull {
        val s = it.playbackState?.state
        (s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_PAUSED || s == PlaybackState.STATE_BUFFERING) &&
            !it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank()
    }

    private fun refresh() {
        val c = active()
        val m = c?.metadata
        _current.value = if (c == null || m == null) null else NowPlaying(
            packageName = c.packageName,
            title = m.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            subtitle = m.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty(),
            art = m.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            canNext = (c.playbackState?.actions ?: 0L) and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
        )
    }

}
