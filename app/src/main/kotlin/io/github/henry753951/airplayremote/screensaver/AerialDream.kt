package io.github.henry753951.airplayremote.screensaver

import android.graphics.Color
import android.graphics.Typeface
import android.service.dreams.DreamService
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Telik screensaver (006 US4): the aerial videos of Apple TV with the clock and the place.
 * One player and one view: a fade through black between videos keeps the memory low on the TV.
 * Without the list (no network, no cache) it shows the clock only.
 */
class AerialDream : DreamService() {
    private var scope: CoroutineScope? = null
    private var player: ExoPlayer? = null
    private lateinit var video: TextureView
    private lateinit var place: TextView
    private var queue: List<Aerial> = emptyList()
    private var index = 0

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        video = TextureView(this).apply { alpha = 0f }
        root.addView(video, FrameLayout.LayoutParams(-1, -1))
        val clock = TextClock(this).apply {
            format24Hour = "H:mm"
            format12Hour = "h:mm"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setShadowLayer(12f, 0f, 2f, 0x99000000.toInt())
        }
        root.addView(clock, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, 54, 80, 0) })
        place = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setShadowLayer(10f, 0f, 2f, 0x99000000.toInt())
            maxWidth = 900
            alpha = 0f
        }
        root.addView(place, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(80, 0, 0, 54) })
        setContentView(root)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        s.launch {
            val list = withContext(Dispatchers.IO) { runCatching { AerialCatalog.load(applicationContext) }.getOrDefault(emptyList()) }
            Log.i(TAG, "aerial videos: ${list.size}")
            if (list.isEmpty()) return@launch
            queue = list.shuffled()
            index = 0
            player = ExoPlayer.Builder(this@AerialDream).build().apply {
                volume = 0f
                setVideoTextureView(video)
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_READY -> fadeIn()
                            Player.STATE_ENDED -> next()
                        }
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        Log.w(TAG, "video failed: ${error.errorCodeName}")
                        next()
                    }
                })
            }
            play()
        }
    }

    private fun play() {
        val p = player ?: return
        val item = queue[index % queue.size]
        place.text = item.place
        p.setMediaItem(MediaItem.fromUri(item.url))
        p.prepare()
        p.playWhenReady = true
    }

    private fun fadeIn() {
        video.animate().alpha(1f).setDuration(FADE_MS).start()
        place.animate().alpha(1f).setStartDelay(FADE_MS).setDuration(FADE_MS).start()
        // The place name shows for a short time only, as on Apple TV.
        place.postDelayed({ place.animate().alpha(0f).setDuration(FADE_MS).start() }, 8_000)
    }

    private fun next() {
        index++
        video.animate().alpha(0f).setDuration(FADE_MS).withEndAction { play() }.start()
        place.animate().alpha(0f).setDuration(FADE_MS).start()
    }

    override fun onDreamingStopped() {
        scope?.cancel()
        scope = null
        player?.release()
        player = null
        if (::video.isInitialized) video.animate().cancel()
        super.onDreamingStopped()
    }

    private companion object {
        const val TAG = "AerialDream"
        const val FADE_MS = 900L
    }
}
