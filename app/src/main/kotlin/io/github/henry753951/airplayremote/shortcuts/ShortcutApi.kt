package io.github.henry753951.airplayremote.shortcuts

import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom

/** The allowed actions of the Shortcuts API (spec 006 FR-011). */
sealed interface ShortcutAction {
    data class Open(val packageName: String) : ShortcutAction
    data class Input(val number: Int) : ShortcutAction
    data class Message(val text: String) : ShortcutAction
    data class Key(val name: String) : ShortcutAction
}

/** Request parsing of the Shortcuts API: pure, so it has unit tests (spec 006 US7). */
object ShortcutApi {
    const val PORT = 8765
    val KEYS = setOf("play", "pause", "next", "previous", "home", "back")
    private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
    private const val MAX_MESSAGE = 200

    sealed interface Result {
        data class Ok(val action: ShortcutAction) : Result
        data class Error(val status: Int, val reason: String) : Result
    }

    /** 128 random bits in base32, 26 characters, in groups of 4 for typing. */
    fun newToken(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(16).also(random::nextBytes)
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        var bits = 0
        var value = 0
        val out = StringBuilder()
        for (b in bytes) {
            value = (value shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                out.append(alphabet[(value shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) out.append(alphabet[(value shl (5 - bits)) and 31])
        return out.chunked(4).joinToString("-")
    }

    /** Dashes and case do not count: the user types the token on an iPhone. */
    private fun normal(token: String) = token.replace("-", "").replace(" ", "").uppercase()

    fun authorized(header: String?, token: String): Boolean {
        val given = header?.removePrefix("Bearer ")?.trim() ?: return false
        if (token.isBlank()) return false
        return MessageDigest.isEqual(normal(given).toByteArray(), normal(token).toByteArray())
    }

    /** [target] is the request target, for example `/v1/open?app=com.example`. */
    fun parse(method: String, target: String, authorization: String?, token: String): Result {
        if (!authorized(authorization, token)) return Result.Error(401, "unauthorized")
        if (method != "POST" && method != "GET") return Result.Error(405, "method")
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate { part ->
            val k = part.substringBefore('=')
            val v = runCatching { URLDecoder.decode(part.substringAfter('=', ""), "UTF-8") }.getOrDefault("")
            k to v
        }
        return when (path) {
            "/v1/open" -> query["app"]?.takeIf { PACKAGE.matches(it) }
                ?.let { Result.Ok(ShortcutAction.Open(it)) } ?: Result.Error(400, "app")
            "/v1/input" -> query["hdmi"]?.toIntOrNull()?.takeIf { it in 1..9 }
                ?.let { Result.Ok(ShortcutAction.Input(it)) } ?: Result.Error(400, "hdmi")
            "/v1/message" -> query["text"]?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { Result.Ok(ShortcutAction.Message(it.take(MAX_MESSAGE))) } ?: Result.Error(400, "text")
            "/v1/key" -> query["name"]?.takeIf { it in KEYS }
                ?.let { Result.Ok(ShortcutAction.Key(it)) } ?: Result.Error(400, "name")
            else -> Result.Error(404, "path")
        }
    }
}
