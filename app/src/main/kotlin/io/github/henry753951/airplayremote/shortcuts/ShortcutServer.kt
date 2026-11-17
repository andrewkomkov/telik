package io.github.henry753951.airplayremote.shortcuts

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import dev.tvhomekit.MessageBanner
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors

/**
 * Local HTTP endpoint for Siri Shortcuts (spec 006 US7). Off by default (FR-010). One thread takes
 * the requests in order. The log has the path and the status only, never the token or a message.
 */
class ShortcutServer(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "ShortcutServer") }
    @Volatile private var socket: ServerSocket? = null

    @Synchronized
    fun start() {
        if (socket != null) return
        val server = runCatching { ServerSocket(ShortcutApi.PORT) }.getOrElse {
            Log.w(TAG, "port ${ShortcutApi.PORT} busy: ${it.javaClass.simpleName}")
            return
        }
        socket = server
        Thread({
            while (!server.isClosed) {
                val client = try { server.accept() } catch (_: SocketException) { break }
                worker.execute { client.use(::handle) }
            }
        }, "ShortcutAccept").start()
        Log.i(TAG, "listening on ${ShortcutApi.PORT}")
    }

    @Synchronized
    fun stop() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun handle(client: Socket) {
        client.soTimeout = 5_000
        val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8), 8192)
        val line = reader.readLine() ?: return
        val parts = line.split(' ')
        if (parts.size < 2) return reply(client, 400, "bad request")
        var auth: String? = null
        for (i in 0 until 50) {
            val header = reader.readLine() ?: break
            if (header.isEmpty()) break
            if (header.startsWith("Authorization:", ignoreCase = true)) auth = header.substringAfter(':').trim()
        }
        val result = ShortcutApi.parse(parts[0], parts[1], auth, ShortcutToken.get(context))
        val path = parts[1].substringBefore('?')
        when (result) {
            is ShortcutApi.Result.Error -> {
                Log.i(TAG, "$path -> ${result.status}")
                reply(client, result.status, result.reason)
            }
            is ShortcutApi.Result.Ok -> {
                Log.i(TAG, "$path -> 200")
                main.post { run(result.action) }
                reply(client, 200, "ok")
            }
        }
    }

    private fun reply(client: Socket, status: Int, text: String) {
        val reason = when (status) { 200 -> "OK"; 401 -> "Unauthorized"; 404 -> "Not Found"; 405 -> "Method Not Allowed"; else -> "Bad Request" }
        val body = "{\"status\":$status,\"result\":\"$text\"}"
        val head = "HTTP/1.1 $status $reason\r\nContent-Type: application/json\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n"
        runCatching { client.getOutputStream().apply { write((head + body).toByteArray()); flush() } }
    }

    private fun run(action: ShortcutAction) {
        when (action) {
            is ShortcutAction.Open -> {
                val pm = context.packageManager
                val intent = pm.getLeanbackLaunchIntentForPackage(action.packageName) ?: pm.getLaunchIntentForPackage(action.packageName)
                intent?.let { start(it.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)) }
            }
            is ShortcutAction.Input -> {
                val tv = context.getSystemService(TvInputManager::class.java)
                val hdmi = runCatching { tv.tvInputList }.getOrDefault(emptyList())
                    .filter { it.isPassthroughInput && it.type == TvInputInfo.TYPE_HDMI }
                    .sortedBy { it.loadLabel(context).toString() }
                hdmi.getOrNull(action.number - 1)?.let {
                    start(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(it.id)))
                }
            }
            is ShortcutAction.Message -> MessageBanner.show(context, action.text, 8_000)
            is ShortcutAction.Key -> when (action.name) {
                "home" -> start(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                "back" -> key(KeyEvent.KEYCODE_BACK, media = false)
                "play" -> key(KeyEvent.KEYCODE_MEDIA_PLAY)
                "pause" -> key(KeyEvent.KEYCODE_MEDIA_PAUSE)
                "next" -> key(KeyEvent.KEYCODE_MEDIA_NEXT)
                "previous" -> key(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
        }
    }

    private fun start(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Log.w(TAG, "start failed: ${it.javaClass.simpleName}") }
    }

    private fun key(code: Int, media: Boolean = true) {
        if (!media) {
            io.github.henry753951.airplayremote.remote.input.RemoteAccessibilityService.instance
                ?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            return
        }
        val audio = context.getSystemService(AudioManager::class.java)
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    private companion object {
        const val TAG = "ShortcutServer"
    }
}

/** The token lives outside the main settings, so the settings list of adb does not show it. */
object ShortcutToken {
    private const val FILE = "shortcuts"
    private const val KEY = "token"

    fun get(context: Context): String {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { return it }
        return renew(context)
    }

    fun renew(context: Context): String {
        val token = ShortcutApi.newToken()
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, token).apply()
        return token
    }
}
