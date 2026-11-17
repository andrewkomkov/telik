package io.github.henry753951.airplayremote.launcher

import android.content.Context
import android.content.pm.PackageInstaller
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** One app install in progress (005 T032). */
data class Install(val sessionId: Int, val packageName: String, val label: String?, val icon: Bitmap?, val progress: Float)

/** Install sessions of other apps from PackageInstaller. Reading them needs no permission. */
object Installs {
    /** Active sessions with a package, not Telik itself, one tile for each package. */
    fun pick(sessions: List<Install>, own: String): List<Install> =
        sessions.filter { it.packageName.isNotBlank() && it.packageName != own }
            .distinctBy { it.packageName }
            .map { it.copy(progress = it.progress.coerceIn(0f, 1f)) }

    fun flow(context: Context): Flow<List<Install>> = callbackFlow {
        val installer = context.packageManager.packageInstaller
        fun send() {
            val list = runCatching { installer.allSessions }.getOrDefault(emptyList())
                .filter { it.isActive }
                .mapNotNull { s ->
                    val pkg = s.appPackageName ?: return@mapNotNull null
                    Install(s.sessionId, pkg, s.appLabel?.toString(), s.appIcon, s.progress)
                }
            trySend(pick(list, context.packageName))
        }
        val callback = object : PackageInstaller.SessionCallback() {
            override fun onCreated(sessionId: Int) = send()
            override fun onBadgingChanged(sessionId: Int) = send()
            override fun onActiveChanged(sessionId: Int, active: Boolean) = send()
            override fun onProgressChanged(sessionId: Int, progress: Float) = send()
            override fun onFinished(sessionId: Int, success: Boolean) = send()
        }
        installer.registerSessionCallback(callback, Handler(Looper.getMainLooper()))
        send()
        awaitClose { installer.unregisterSessionCallback(callback) }
    }
}
