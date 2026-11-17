package io.github.henry753951.airplayremote.remote

import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.henry753951.airplayremote.remote.input.RemoteAccessibilityService

/**
 * Starts an activity from a background service. Android 14 blocks such starts (BAL_BLOCK) unless
 * the caller has a visible window. The accessibility service of Telik is exempt, as HomeKit uses.
 */
object TvLaunch {
    fun start(context: Context, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { (RemoteAccessibilityService.instance ?: context).startActivity(intent) }
            .onFailure { Log.w("TvLaunch", "start failed: ${it.javaClass.simpleName}") }
            .isSuccess
    }
}
