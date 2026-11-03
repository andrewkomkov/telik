package io.github.henry753951.airplayremote.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * FR-017: if Home fails to start three times in a row, Telik drops the Home role
 * by disabling its Home alias, so the system falls back to the maker's Home screen.
 * A start counts as failed until the first frame is drawn.
 */
class CrashGuard(private val context: Context) {
    private val prefs = context.getSharedPreferences("launcher_guard", Context.MODE_PRIVATE)

    /** Call first in onCreate. Returns true when Home must not start. */
    fun onStart(): Boolean {
        val failures = prefs.getInt(KEY, 0)
        if (shouldDrop(failures)) {
            prefs.edit().putInt(KEY, 0).commit()
            setHomeEnabled(false)
            return true
        }
        prefs.edit().putInt(KEY, failures + 1).commit()
        return false
    }

    /** Call after the first frame. */
    fun onFirstFrame() { prefs.edit().putInt(KEY, 0).apply() }

    fun setHomeEnabled(enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context.packageName, "io.github.henry753951.airplayremote.launcher.HomeAlias"),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    companion object {
        private const val KEY = "failed_starts"
        const val MAX_FAILURES = 3
        fun shouldDrop(failures: Int) = failures >= MAX_FAILURES
    }
}
