package io.github.henry753951.airplayremote.launcher.ui

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Small own cache for tile art (FR-013, no image library). Decoding runs on the IO
 * dispatcher, so the main thread never waits for an icon (FR-018).
 */
object BannerCache {
    private const val W = 320
    private const val H = 180

    class Art(val image: ImageBitmap, val isBanner: Boolean)
    private val arts = HashMap<String, Art>()

    fun peek(pkg: String): Art? = synchronized(arts) { arts[pkg] }

    fun load(context: Context, pkg: String, activity: String): Art? {
        peek(pkg)?.let { return it }
        val pm = context.packageManager
        return try {
            val component = ComponentName(pkg, activity)
            val banner = pm.getActivityBanner(component) ?: pm.getApplicationBanner(pkg)
            val art = if (banner != null) {
                Art(banner.toBitmap(W, H).asImageBitmap(), true)
            } else {
                val icon = pm.getActivityIcon(component)
                Art(icon.toBitmap(H, H).asImageBitmap(), false)
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
}

@Composable
fun rememberArt(pkg: String, activity: String): State<BannerCache.Art?> {
    val ctx = LocalContext.current.applicationContext
    return produceState(BannerCache.peek(pkg), pkg, activity) {
        if (value == null) value = withContext(Dispatchers.IO) { BannerCache.load(ctx, pkg, activity) }
    }
}
