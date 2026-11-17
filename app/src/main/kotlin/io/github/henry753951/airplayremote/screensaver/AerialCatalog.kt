package io.github.henry753951.airplayremote.screensaver

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** One aerial video of Apple TV (006 US4). */
data class Aerial(val id: String, val url: String, val place: String)

/** The best file the TV can play: 4K HEVC on a 4K panel goes to a hardware layer at full size. */
enum class AerialQuality(val keys: List<String>) {
    UHD_HEVC(listOf("url-4K-SDR", "url-1080-SDR", "url-1080-H264")),
    HD_HEVC(listOf("url-1080-SDR", "url-1080-H264")),
    HD_H264(listOf("url-1080-H264")),
}

/**
 * The public list of the Apple TV aerial videos: `resources-16.tar` holds `entries.json` and the
 * place names for each language. Telik streams the videos and keeps only this list (FR-006).
 */
object AerialCatalog {
    private const val SOURCE = "https://sylvan.apple.com/Aerials/resources-16.tar"
    private const val MAX_AGE_MS = 7L * 24 * 3600 * 1000
    private const val TAG = "AerialCatalog"

    /** Videos from `entries.json`, with the place name in the language of [names] when it has one. */
    fun parse(entries: String, names: Map<String, String>, quality: AerialQuality): List<Aerial> {
        val assets = JSONObject(entries).optJSONArray("assets") ?: return emptyList()
        return (0 until assets.length()).mapNotNull { i ->
            val a = assets.optJSONObject(i) ?: return@mapNotNull null
            val url = quality.keys.map { a.optString(it) }.firstOrNull { it.isNotBlank() }.orEmpty()
            if (!url.startsWith("https://")) return@mapNotNull null
            val poi = a.optJSONObject("pointsOfInterest")?.optString("0").orEmpty()
            val place = names[poi] ?: a.optString("accessibilityLabel")
            Aerial(a.optString("id"), url, place)
        }
    }

    /** The bundle folder of the place names for [locale]: `ru`, `zh_TW`, `en` and so on. */
    fun stringsPath(locale: Locale): List<String> {
        val dir = "TVIdleScreenStrings.bundle/%s.lproj/Localizable.nocache.strings"
        val tagged = if (locale.country.isNotEmpty()) listOf(dir.format("${locale.language}_${locale.country}")) else emptyList()
        return tagged + dir.format(locale.language) + dir.format("en")
    }

    /** Reads the files with [names] from a tar stream. Other files are skipped. */
    fun untar(input: InputStream, names: Set<String>): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        val header = ByteArray(512)
        while (out.size < names.size) {
            if (input.readNBytes(header, 0, 512) < 512) break
            val name = String(header, 0, 100, Charsets.UTF_8).substringBefore('\u0000')
            if (name.isEmpty()) break
            val size = String(header, 124, 12, Charsets.US_ASCII).trim('\u0000', ' ').toLongOrNull(8) ?: 0L
            val padded = (size + 511) / 512 * 512
            if (name in names && size <= 8_000_000) {
                out[name] = input.readNBytes(size.toInt())
                input.skipNBytes(padded - size)
            } else {
                input.skipNBytes(padded)
            }
        }
        return out
    }

    /** The list, from the disk cache when it is younger than 7 days. Empty without network and cache. */
    fun load(context: Context): List<Aerial> {
        val cache = File(context.cacheDir, "aerial-resources-16.tar")
        if (!cache.exists() || System.currentTimeMillis() - cache.lastModified() > MAX_AGE_MS) {
            runCatching { download(cache) }.onFailure { Log.w(TAG, "list download failed: ${it.javaClass.simpleName}") }
        }
        if (!cache.exists()) return emptyList()
        val wanted = stringsPath(Locale.getDefault())
        val files = cache.inputStream().buffered().use { untar(it, (wanted + "entries.json").toSet()) }
        val entries = files["entries.json"] ?: return emptyList()
        val names = wanted.firstNotNullOfOrNull { files[it] }?.let { runCatching { Bplist.strings(it) }.getOrNull() }.orEmpty()
        return parse(String(entries, Charsets.UTF_8), names, quality(context))
    }

    private fun download(target: File) {
        val tmp = File(target.path + ".part")
        val conn = URL(SOURCE).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 15_000
        try {
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            check(tmp.renameTo(target)) { "rename failed" }
        } finally {
            conn.disconnect()
            tmp.delete()
        }
    }

    /** 4K only with a hardware HEVC decoder for 3840x2160 and a panel of that size. */
    fun quality(context: Context): AerialQuality {
        val hevc = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true) }
        }
        if (hevc.isEmpty()) return AerialQuality.HD_H264
        val uhdDecoder = hevc.any { info ->
            info.isHardwareAccelerated && runCatching {
                info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_HEVC).videoCapabilities?.isSizeSupported(3840, 2160) == true
            }.getOrDefault(false)
        }
        val mode = context.getSystemService(android.hardware.display.DisplayManager::class.java)
            .getDisplay(android.view.Display.DEFAULT_DISPLAY)?.mode
        val uhdPanel = (mode?.physicalWidth ?: 0) >= 3840
        return if (uhdDecoder && uhdPanel) AerialQuality.UHD_HEVC else AerialQuality.HD_HEVC
    }
}
