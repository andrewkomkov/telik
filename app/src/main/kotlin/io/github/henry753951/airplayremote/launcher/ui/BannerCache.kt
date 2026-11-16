package io.github.henry753951.airplayremote.launcher.ui

import android.content.ComponentName
import android.graphics.Bitmap
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import io.github.henry753951.airplayremote.launcher.Ambient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Small own cache for tile art (FR-013, no image library). Decoding runs on the IO
 * dispatcher, so the main thread never waits for an icon (FR-018).
 */
object BannerCache {
    private const val W = 320
    private const val H = 180

    /** [color] is the main color of the art (US8), null for gray art. */
    class Art(val image: ImageBitmap, val isBanner: Boolean, val color: Int?)
    private val arts = HashMap<String, Art>()

    fun peek(pkg: String): Art? = synchronized(arts) { arts[pkg] }

    fun load(context: Context, pkg: String, activity: String): Art? {
        peek(pkg)?.let { return it }
        val pm = context.packageManager
        return try {
            val component = ComponentName(pkg, activity)
            val banner = pm.getActivityBanner(component) ?: pm.getApplicationBanner(pkg)
            val art = if (banner != null) {
                val bmp = banner.toBitmap(W, H)
                Art(bmp.asImageBitmap(), true, mainColor(bmp))
            } else {
                val bmp = pm.getActivityIcon(component).toBitmap(H, H)
                Art(bmp.asImageBitmap(), false, mainColor(bmp))
            }
            synchronized(arts) {
                if (arts.size >= 48) arts.remove(arts.keys.first())
                arts[pkg] = art
            }
            art
        } catch (_: Exception) {
            null
        }
    }

    private val heroes = HashMap<String, ImageBitmap>()

    fun peekHero(pkg: String): ImageBitmap? = synchronized(heroes) { heroes[pkg] }

    /** The banner at twice the tile size, for the Top Shelf (US8). Null for an app without a banner. */
    fun loadHero(context: Context, pkg: String, activity: String): ImageBitmap? {
        peekHero(pkg)?.let { return it }
        val pm = context.packageManager
        return try {
            val banner = pm.getActivityBanner(ComponentName(pkg, activity)) ?: pm.getApplicationBanner(pkg) ?: return null
            val image = banner.toBitmap(W * 2, H * 2).asImageBitmap()
            synchronized(heroes) {
                if (heroes.size >= 8) heroes.remove(heroes.keys.first())
                heroes[pkg] = image
            }
            image
        } catch (_: Exception) {
            null
        }
    }

    fun mainColor(bmp: Bitmap): Int? {
        val small = Bitmap.createScaledBitmap(bmp, 24, 14, true)
        val px = IntArray(24 * 14)
        small.getPixels(px, 0, 24, 0, 0, 24, 14)
        if (small !== bmp) small.recycle()
        return Ambient.pick(px)
    }
}

@Composable
fun rememberArt(pkg: String, activity: String): State<BannerCache.Art?> {
    val ctx = LocalContext.current.applicationContext
    return produceState(BannerCache.peek(pkg), pkg, activity) {
        if (value == null) value = withContext(Dispatchers.IO) { BannerCache.load(ctx, pkg, activity) }
    }
}

@Composable
fun rememberHero(pkg: String, activity: String): State<ImageBitmap?> {
    val ctx = LocalContext.current.applicationContext
    return produceState(BannerCache.peekHero(pkg), pkg, activity) {
        if (value == null) value = withContext(Dispatchers.IO) { BannerCache.loadHero(ctx, pkg, activity) }
    }
}
