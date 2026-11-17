package io.github.henry753951.airplayremote.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Art of TV programs (005 T055): `content:`, `android.resource:` and `https:` links,
 * decoded off the main thread near [W] x [H], kept in memory with a byte limit. No image library.
 */
object PosterCache {
    /** Card size; [loadLarge] gives the Top Shelf size. */
    private const val W = 640
    private const val H = 360
    private const val LARGE_W = 1280
    private const val LARGE_H = 720

    class Poster(val bitmap: Bitmap, val color: Int?)

    private val cache = object : LruCache<String, Poster>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Poster) = value.bitmap.byteCount
    }
    /** Links that failed, with the time: a slow network gets a new try after [RETRY_MS]. */
    private val failed = HashMap<String, Long>()
    private const val RETRY_MS = 60_000L

    fun peek(uri: String): Poster? = cache.get(uri)

    /** A few large images only: each one is about 3.5 MB. */
    private val large = object : LruCache<String, Bitmap>(3) {}

    fun peekLarge(uri: String): Bitmap? = large.get(uri)

    /** The art at the Top Shelf size. For YouTube, the largest thumbnail first. */
    fun loadLarge(context: Context, uri: String): Bitmap? {
        peekLarge(uri)?.let { return it }
        val bmp = sequenceOf(maxRes(uri), uri).filterNotNull().distinct().firstNotNullOfOrNull { u ->
            runCatching { decode(context, u, LARGE_W, LARGE_H) }.getOrNull()
        } ?: return null
        return bmp.also { large.put(uri, it) }
    }

    /** `i.ytimg.com/vi/<id>/hqdefault.jpg` has a 1280x720 sibling `maxresdefault.jpg` for most videos. */
    internal fun maxRes(uri: String): String? {
        val u = Uri.parse(uri)
        if (u.scheme != "https" || u.host?.endsWith("ytimg.com") != true) return null
        val seg = u.pathSegments
        if (seg.size != 3 || seg[0] != "vi" || !seg[2].endsWith(".jpg") || seg[2] == "maxresdefault.jpg") return null
        return u.buildUpon().path("/vi/${seg[1]}/maxresdefault.jpg").clearQuery().build().toString()
    }

    fun load(context: Context, uri: String): Poster? {
        peek(uri)?.let { return it }
        val now = System.currentTimeMillis()
        if (synchronized(failed) { failed[uri]?.let { now - it < RETRY_MS } == true }) return null
        val bmp = try {
            decode(context, uri, W, H)
        } catch (e: Exception) {
            // Scheme and host only: the path can name the title (FR-012).
            val u = Uri.parse(uri)
            android.util.Log.w("PosterCache", "load failed: ${u.scheme}://${u.host} ${e.javaClass.simpleName}")
            null
        }
        if (bmp == null) {
            synchronized(failed) { failed[uri] = now }
            return null
        }
        return Poster(bmp, BannerCache.mainColor(bmp)).also { cache.put(uri, it) }
    }

    private fun decode(context: Context, uri: String, w: Int, h: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // With inJustDecodeBounds the decode gives null by design; only the sizes count.
        (open(context, uri) ?: return null).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return open(context, uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun open(context: Context, uri: String): InputStream? = when (Uri.parse(uri).scheme?.lowercase()) {
        "https" -> (URL(uri).openConnection() as HttpURLConnection).run {
            connectTimeout = 5_000
            readTimeout = 8_000
            instanceFollowRedirects = true
            if (responseCode !in 200..299) { disconnect(); null } else inputStream
        }
        "content", "android.resource" -> context.contentResolver.openInputStream(Uri.parse(uri))
        else -> null
    }
}

@Composable
fun rememberPoster(uri: String?): State<PosterCache.Poster?> {
    val ctx = LocalContext.current.applicationContext
    // produceState keeps its value when the key changes, so the value carries its own link:
    // a card that moves to another item never shows the art of the earlier one.
    val state = produceState<Pair<String, PosterCache.Poster>?>(uri?.let { u -> PosterCache.peek(u)?.let { u to it } }, uri) {
        value = uri?.let { u -> (PosterCache.peek(u) ?: withContext(Dispatchers.IO) { PosterCache.load(ctx, u) })?.let { u to it } }
    }
    return androidx.compose.runtime.remember(uri) {
        androidx.compose.runtime.derivedStateOf { state.value?.takeIf { it.first == uri }?.second }
    }
}

/** The large art of [uri], or null while it loads. It never gives the art of an earlier link. */
@Composable
fun rememberLargePoster(uri: String?): Bitmap? {
    val ctx = LocalContext.current.applicationContext
    // produceState keeps its value when the key changes, so the value carries its own link.
    val state = produceState<Pair<String, Bitmap>?>(uri?.let { u -> PosterCache.peekLarge(u)?.let { u to it } }, uri) {
        value = uri?.let { u -> (PosterCache.peekLarge(u) ?: withContext(Dispatchers.IO) { PosterCache.loadLarge(ctx, u) })?.let { u to it } }
    }
    return state.value?.takeIf { it.first == uri }?.second
}
