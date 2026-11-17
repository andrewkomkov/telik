package io.github.henry753951.airplayremote.screensaver

import android.graphics.Color
import android.graphics.Typeface
import android.service.dreams.DreamService
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
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
 * One player: a fade through black between videos keeps the memory low on the TV.
 * Without the list (no network, no cache) it shows the clock only.
 */
class AerialDream : DreamService() {
    private var scope: CoroutineScope? = null
    private var player: ExoPlayer? = null
    private lateinit var video: SurfaceView
    /** Black cover over the video: a SurfaceView is a hardware layer and does not fade itself. */
    private lateinit var cover: View
    private lateinit var place: TextView
    private var queue: List<Aerial> = emptyList()
    private var index = 0
    /** 006 US5: photos of the shared album, shown in breaks between the videos. */
    private lateinit var photo: ImageView
    private var photos: List<String> = emptyList()
    private var photoIndex = 0

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        // A SurfaceView goes to a hardware video layer at the panel size (4K), past the 1080p UI and the GPU.
        video = SurfaceView(this)
        root.addView(video, FrameLayout.LayoutParams(-1, -1))
        photo = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; visibility = View.GONE }
        root.addView(photo, FrameLayout.LayoutParams(-1, -1))
        cover = View(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(cover, FrameLayout.LayoutParams(-1, -1))
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
            photos = withContext(Dispatchers.IO) {
                ScreensaverStore.albumLink(applicationContext)?.let { SharedAlbum.load(it) }.orEmpty()
            }.shuffled()
            Log.i(TAG, "aerial videos: ${list.size}, album photos: ${photos.size}")
            if (list.isEmpty()) {
                // Without the videos, the album alone; without both, the clock alone.
                if (photos.isNotEmpty()) showPhotos(Int.MAX_VALUE)
                return@launch
            }
            queue = list.shuffled()
            index = 0
            player = ExoPlayer.Builder(this@AerialDream).build().apply {
                volume = 0f
                setVideoSurfaceView(video)
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
        cover.animate().alpha(0f).setDuration(FADE_MS).start()
        place.animate().alpha(1f).setStartDelay(FADE_MS).setDuration(FADE_MS).start()
        // The place name shows for a short time only, as on Apple TV.
        place.postDelayed({ place.animate().alpha(0f).setDuration(FADE_MS).start() }, 8_000)
    }

    private fun next() {
        index++
        place.animate().alpha(0f).setDuration(FADE_MS).start()
        cover.animate().alpha(1f).setDuration(FADE_MS).withEndAction {
            if (photos.isNotEmpty()) showPhotos(PHOTOS_PER_BREAK) else play()
        }.start()
    }

    /** Shows [count] album photos, 10 seconds each, then the next video. */
    private fun showPhotos(count: Int) {
        val s = scope ?: return
        val url = photos[photoIndex++ % photos.size]
        s.launch {
            val bmp = withContext(Dispatchers.IO) {
                io.github.henry753951.airplayremote.launcher.ui.PosterCache.loadLarge(applicationContext, url)
            }
            if (bmp != null) {
                photo.setImageBitmap(bmp)
                photo.visibility = View.VISIBLE
                cover.animate().alpha(0f).setDuration(FADE_MS).start()
            }
            kotlinx.coroutines.delay(if (bmp != null) PHOTO_MS else 0L)
            cover.animate().alpha(1f).setDuration(FADE_MS).withEndAction {
                when {
                    count > 1 -> showPhotos(count - 1)
                    player != null -> { photo.visibility = View.GONE; photo.setImageDrawable(null); play() }
                    else -> showPhotos(Int.MAX_VALUE)
                }
            }.start()
        }
    }

    override fun onDreamingStopped() {
        scope?.cancel()
        scope = null
        player?.release()
        player = null
        if (::cover.isInitialized) cover.animate().cancel()
        super.onDreamingStopped()
    }

    private companion object {
        const val TAG = "AerialDream"
        const val FADE_MS = 900L
        const val PHOTO_MS = 10_000L
        const val PHOTOS_PER_BREAK = 6
    }
}
