package io.github.henry753951.airplayremote.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers

/** Launchable apps (leanback or normal launcher), refreshed when packages change. */
class AppRepository(private val context: Context) {

    fun load(): List<AppTile> {
        val pm = context.packageManager
        val seen = HashSet<String>()
        val out = ArrayList<AppTile>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)) {
                val a = info.activityInfo ?: continue
                if (a.packageName == context.packageName) continue
                if (seen.add(a.packageName)) {
                    out += AppTile(a.packageName, info.loadLabel(pm).toString(), a.name)
                }
            }
        }
        return out
    }

    val apps: Flow<List<AppTile>> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { trySend(Unit) }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
        trySend(Unit)
        awaitClose { context.unregisterReceiver(receiver) }
    }.conflate().map { load() }.flowOn(Dispatchers.IO)
}
