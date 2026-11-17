package io.github.henry753951.airplayremote.launcher

import android.content.Context
import android.media.tv.TvContract.WatchNextPrograms
import android.util.Log

/** One Watch Next program of another app (005 US5). */
data class WatchNextItem(
    val id: Long,
    val packageName: String,
    val title: String,
    val episodeTitle: String?,
    val season: String?,
    val episode: String?,
    val artUri: String?,
    val intentUri: String?,
    val lastEngagedMillis: Long,
    val positionMillis: Long,
    val durationMillis: Long,
) {
    /** 0..1, or null when the app gives no position or no length. */
    val progress: Float?
        get() = if (positionMillis > 0 && durationMillis > 0) (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f) else null
}

/** The "Continue watching" row (005 T054): pure rules plus the read of TvProvider. */
object ContinueWatching {
    const val LIMIT = 12

    /** Items of installed apps with a link, last used first, one card for each title of an app. */
    fun pick(items: List<WatchNextItem>, installed: Set<String>, limit: Int = LIMIT): List<WatchNextItem> =
        items.asSequence()
            .filter { it.packageName in installed && !it.intentUri.isNullOrBlank() && it.title.isNotBlank() }
            .sortedByDescending { it.lastEngagedMillis }
            .distinctBy { it.packageName to it.title.trim().lowercase() }
            .take(limit)
            .toList()

    /** Reads all searchable Watch Next rows. Needs READ_TV_LISTINGS; gives an empty list without it. */
    fun read(context: Context): List<WatchNextItem> {
        if (!TvListings.granted(context)) return emptyList()
        val cols = arrayOf(
            WatchNextPrograms._ID,
            WatchNextPrograms.COLUMN_PACKAGE_NAME,
            WatchNextPrograms.COLUMN_TITLE,
            WatchNextPrograms.COLUMN_EPISODE_TITLE,
            WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
            WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
            WatchNextPrograms.COLUMN_THUMBNAIL_URI,
            WatchNextPrograms.COLUMN_POSTER_ART_URI,
            WatchNextPrograms.COLUMN_INTENT_URI,
            WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
            WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
            WatchNextPrograms.COLUMN_DURATION_MILLIS,
            WatchNextPrograms.COLUMN_BROWSABLE,
        )
        return try {
            context.contentResolver.query(WatchNextPrograms.CONTENT_URI, cols, null, null, null)?.use { c ->
                val out = ArrayList<WatchNextItem>()
                fun str(i: Int) = if (c.isNull(i)) null else c.getString(i)
                while (c.moveToNext()) {
                    if (!c.isNull(12) && c.getInt(12) == 0) continue
                    out += WatchNextItem(
                        id = c.getLong(0),
                        packageName = str(1) ?: continue,
                        title = str(2) ?: continue,
                        episodeTitle = str(3),
                        season = str(4),
                        episode = str(5),
                        // A wide thumbnail fits the 16:9 card; the poster is the fallback.
                        artUri = str(6) ?: str(7),
                        intentUri = str(8),
                        lastEngagedMillis = if (c.isNull(9)) 0 else c.getLong(9),
                        positionMillis = if (c.isNull(10)) 0 else c.getLong(10),
                        durationMillis = if (c.isNull(11)) 0 else c.getLong(11),
                    )
                }
                out
            } ?: emptyList()
        } catch (e: Exception) {
            // No titles in the log (FR-012).
            Log.w("ContinueWatching", "read failed: ${e.javaClass.simpleName}")
            emptyList()
        }
    }
}
