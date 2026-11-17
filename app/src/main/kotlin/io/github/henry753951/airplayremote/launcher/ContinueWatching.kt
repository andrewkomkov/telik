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
                // Counts for each app, no titles (FR-012): items, items with no link, items with no art.
                out.groupBy { it.packageName }.forEach { (pkg, l) ->
                    Log.i("ContinueWatching", "$pkg: ${l.size} items, ${l.count { it.intentUri.isNullOrBlank() }} no link, ${l.count { it.artUri.isNullOrBlank() }} no art")
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

/** One preview program of an app channel (005 T058): what the Top Shelf shows for a focused app. */
data class PreviewItem(
    val packageName: String,
    val title: String,
    val artUri: String?,
    val intentUri: String?,
    val weight: Int,
    /** The channel shows on the stock Home screen. Google TV has no screen to show or hide a channel. */
    val channelShown: Boolean = true,
)

/** Preview programs of browsable channels, best weight first, at most [PER_APP] for each app. */
object AppPreviews {
    const val PER_APP = 8

    /**
     * Programs for the shelf of each app. An app with no preview programs, as Apple TV on the test
     * TV, gets its Watch Next items instead.
     */
    fun withWatchNext(previews: Map<String, List<PreviewItem>>, watchNext: List<WatchNextItem>): Map<String, List<PreviewItem>> {
        val extra = watchNext.filter { it.packageName !in previews && !it.artUri.isNullOrBlank() }
            .groupBy { it.packageName }
            .mapValues { (_, list) ->
                list.take(PER_APP).map { w ->
                    PreviewItem(w.packageName, listOfNotNull(w.title, w.episodeTitle).joinToString(" · "), w.artUri, w.intentUri, 0)
                }
            }
        return previews + extra
    }

    fun pick(items: List<PreviewItem>, installed: Set<String>): Map<String, List<PreviewItem>> =
        items.filter { it.packageName in installed && !it.artUri.isNullOrBlank() && it.title.isNotBlank() }
            .groupBy { it.packageName }
            .mapValues { (_, list) ->
                list.sortedWith(compareByDescending<PreviewItem> { it.channelShown }.thenByDescending { it.weight })
                    .distinctBy { it.title.trim().lowercase() }.take(PER_APP)
            }

    fun read(context: Context): List<PreviewItem> {
        if (!TvListings.granted(context)) return emptyList()
        val cr = context.contentResolver
        return try {
            // Programs of a shown channel come first. Google TV leaves the channels of most apps
            // not browsable and has no screen to change it, so the others also count.
            val browsable = HashSet<Long>()
            cr.query(
                android.media.tv.TvContract.Channels.CONTENT_URI,
                arrayOf(android.media.tv.TvContract.Channels._ID, android.media.tv.TvContract.Channels.COLUMN_BROWSABLE),
                null, null, null,
            )?.use { c -> while (c.moveToNext()) if (c.isNull(1) || c.getInt(1) != 0) browsable += c.getLong(0) }
            val cols = arrayOf(
                android.media.tv.TvContract.PreviewPrograms.COLUMN_CHANNEL_ID,
                android.media.tv.TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME,
                android.media.tv.TvContract.PreviewPrograms.COLUMN_TITLE,
                android.media.tv.TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
                android.media.tv.TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI,
                android.media.tv.TvContract.PreviewPrograms.COLUMN_INTENT_URI,
                android.media.tv.TvContract.PreviewPrograms.COLUMN_WEIGHT,
                android.media.tv.TvContract.PreviewPrograms.COLUMN_BROWSABLE,
            )
            cr.query(android.media.tv.TvContract.PreviewPrograms.CONTENT_URI, cols, null, null, null)?.use { c ->
                val out = ArrayList<PreviewItem>()
                fun str(i: Int) = if (c.isNull(i)) null else c.getString(i)
                while (c.moveToNext()) {
                    // A program that the user removed stays out.
                    if (!c.isNull(7) && c.getInt(7) == 0) continue
                    out += PreviewItem(
                        packageName = str(1) ?: continue,
                        title = str(2) ?: continue,
                        // A wide image suits the shelf: the poster first, then the thumbnail.
                        artUri = str(3) ?: str(4),
                        intentUri = str(5),
                        weight = if (c.isNull(6)) 0 else c.getInt(6),
                        channelShown = c.getLong(0) in browsable,
                    )
                }
                out
            } ?: emptyList()
        } catch (e: Exception) {
            Log.w("AppPreviews", "read failed: ${e.javaClass.simpleName}")
            emptyList()
        }
    }
}
