package io.github.henry753951.airplayremote.screensaver

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Photos of an iCloud shared album with a public website (006 US5). There is no official API: this
 * follows the calls of the public album web page. The link and the album id stay out of the log
 * (FR-008, FR-013).
 */
object SharedAlbum {
    private const val TAG = "SharedAlbum"
    private const val MAX_PHOTOS = 200
    private val TOKEN = Regex("[A-Za-z0-9]{8,40}")

    /** `https://www.icloud.com/sharedalbum/#B0aGWZuqDGKPsDJ` → `B0aGWZuqDGKPsDJ`. */
    fun token(link: String): String? = link.trim().substringAfterLast('#', "").takeIf { TOKEN.matches(it) }

    /** Another partition host, when the first answer names one. */
    fun redirectHost(stream: String): String? =
        runCatching { JSONObject(stream).optString("X-Apple-MMe-Host") }.getOrNull()
            ?.takeIf { it.endsWith(".icloud.com") && it.none { c -> c == '/' || c == ':' } }

    /** Photo GUID to the checksum of its largest version. Videos have no image version and stay out. */
    fun photos(stream: String): List<Pair<String, String>> {
        val list = JSONObject(stream).optJSONArray("photos") ?: return emptyList()
        return (0 until list.length()).mapNotNull { i ->
            val p = list.optJSONObject(i) ?: return@mapNotNull null
            if (p.optString("mediaAssetType") == "video") return@mapNotNull null
            val guid = p.optString("photoGuid").ifBlank { return@mapNotNull null }
            val d = p.optJSONObject("derivatives") ?: return@mapNotNull null
            val best = d.keys().asSequence().mapNotNull { d.optJSONObject(it) }
                .maxByOrNull { it.optString("fileSize").toLongOrNull() ?: it.optLong("fileSize") }
                ?.optString("checksum")?.ifBlank { null } ?: return@mapNotNull null
            guid to best
        }.take(MAX_PHOTOS)
    }

    /** Checksum to download link, from the answer of `webasseturls`. */
    fun urls(answer: String): Map<String, String> {
        val items = JSONObject(answer).optJSONObject("items") ?: return emptyMap()
        return items.keys().asSequence().mapNotNull { key ->
            val v = items.optJSONObject(key) ?: return@mapNotNull null
            val host = v.optString("url_location")
            val path = v.optString("url_path")
            if (host.isBlank() || !path.startsWith("/") || host.contains('/')) null else key to "https://$host$path"
        }.toMap()
    }

    /** Links of the photos, in album order. Empty when the album is not public or the network fails. */
    fun load(link: String): List<String> {
        val token = token(link) ?: return emptyList()
        return runCatching {
            var host = "p23-sharedstreams.icloud.com"
            var stream = post(host, token, "webstream", """{"streamCtag":null}""")
            redirectHost(stream)?.let { host = it; stream = post(host, token, "webstream", """{"streamCtag":null}""") }
            val photos = photos(stream)
            if (photos.isEmpty()) return@runCatching emptyList()
            val body = JSONObject().put("photoGuids", JSONArray(photos.map { it.first })).toString()
            val links = urls(post(host, token, "webasseturls", body))
            photos.mapNotNull { links[it.second] }
        }.onFailure { Log.w(TAG, "album load failed: ${it.javaClass.simpleName}") }.getOrDefault(emptyList())
    }

    private fun post(host: String, token: String, call: String, body: String): String {
        val conn = URL("https://$host/$token/sharedstreams/$call").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 8_000
        conn.readTimeout = 15_000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            // 330 is the partition redirect; its body names the host, as 200 does.
            val ok = conn.responseCode == 200 || conn.responseCode == 330
            check(ok) { "HTTP ${conn.responseCode}" }
            val input = if (conn.responseCode >= 300) conn.errorStream ?: conn.inputStream else conn.inputStream
            return input.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
