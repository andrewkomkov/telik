package io.github.henry753951.airplayremote.launcher

import android.content.Context
import android.content.pm.PackageManager
import android.media.tv.TvContract
import android.net.Uri
import android.util.Log

/**
 * Probe of the TV home rows of other apps (005 T001). With READ_TV_LISTINGS, TvProvider gives
 * the searchable rows of all packages. The log has package names and counts only, no titles (FR-012).
 */
object TvListings {
    private const val TAG = "TelikProbe"
    /** Hidden in the SDK (SystemApi), but a plain runtime permission on the TV. */
    const val PERMISSION = "android.permission.READ_TV_LISTINGS"

    fun granted(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Rows for each package in each table, or null without the permission. */
    fun count(context: Context): Map<String, Map<String, Int>>? {
        if (!granted(context)) return null
        val tables = mapOf(
            "channels" to TvContract.Channels.CONTENT_URI,
            "preview" to TvContract.PreviewPrograms.CONTENT_URI,
            "watchNext" to TvContract.WatchNextPrograms.CONTENT_URI,
        )
        return tables.mapValues { (_, uri) -> countByPackage(context, uri) }
    }

    fun log(context: Context) {
        val result = count(context)
        if (result == null) {
            Log.i(TAG, "READ_TV_LISTINGS not granted")
            return
        }
        result.forEach { (table, byPkg) -> Log.i(TAG, "$table: ${if (byPkg.isEmpty()) "no rows" else byPkg}") }
    }

    private fun countByPackage(context: Context, uri: Uri): Map<String, Int> = try {
        // A caller without ACCESS_ALL_EPG_DATA may not pass a selection, so the filter runs here.
        context.contentResolver.query(uri, arrayOf("package_name"), null, null, null)?.use { c ->
            val out = HashMap<String, Int>()
            while (c.moveToNext()) {
                val pkg = c.getString(0) ?: "?"
                out[pkg] = (out[pkg] ?: 0) + 1
            }
            out
        } ?: emptyMap()
    } catch (e: Exception) {
        Log.w(TAG, "query $uri failed: ${e.javaClass.simpleName}: ${e.message}")
        emptyMap()
    }
}
