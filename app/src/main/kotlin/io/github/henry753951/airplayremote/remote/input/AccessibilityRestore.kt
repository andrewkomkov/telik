package io.github.henry753951.airplayremote.remote.input

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log

/**
 * The remote sends keys only through the accessibility service, so a TV that drops the service
 * leaves the remote without control. Telik holds WRITE_SECURE_SETTINGS and puts it back.
 */
object AccessibilityRestore {
    private const val TAG = "AccessibilityRestore"

    /** The enabled list with [ours] added, or null when it is there already. Other services stay. */
    fun merged(current: String?, ours: String): String? {
        val entries = current.orEmpty().split(':').map { it.trim() }.filter { it.isNotEmpty() }
        if (entries.any { full(it) == full(ours) }) return null
        return (entries + ours).joinToString(":")
    }

    /** "pkg/.Cls" is short for "pkg/pkg.Cls". */
    private fun full(name: String): String {
        val pkg = name.substringBefore('/')
        val cls = name.substringAfter('/', "")
        return if (cls.startsWith(".")) "$pkg/$pkg$cls" else name
    }

    fun ensure(context: Context) {
        val ours = ComponentName(context, "com.corvofeng.fakeatv.AtvAccessibilityService").flattenToString()
        val resolver = context.contentResolver
        val next = merged(Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), ours)
            ?: return
        try {
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next)
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            Log.i(TAG, "remote accessibility service restored")
        } catch (e: SecurityException) {
            Log.w(TAG, "no WRITE_SECURE_SETTINGS, the user must turn on the service", e)
        }
    }
}
