package io.github.henry753951.airplayremote.launcher

/** 005 US3: the Recent row and the "New" and "Updated" badges. Pure rules for unit tests. */
object Recent {
    const val LIMIT = 5
    const val BADGE_DAYS = 7
    private const val DAY = 24L * 60 * 60 * 1000

    /** Apps opened from Telik, last used first. Apps in the Favorites row do not show twice. */
    fun pick(launches: List<Launch>, apps: Set<String>, exclude: Set<String>, limit: Int = LIMIT): List<String> =
        launches.asSequence()
            .filter { it.packageName in apps && it.packageName !in exclude }
            .sortedByDescending { it.lastMillis }
            .take(limit)
            .map { it.packageName }
            .toList()

    enum class Badge { NEW, UPDATED }

    /** The badge stays until the first launch after the install or the update. */
    fun badge(installed: Long, updated: Long, lastLaunch: Long?, now: Long): Badge? {
        val since = now - BADGE_DAYS * DAY
        val launchedAfter = { t: Long -> lastLaunch != null && lastLaunch >= t }
        return when {
            installed in since..now && !launchedAfter(installed) -> Badge.NEW
            updated > installed && updated in since..now && !launchedAfter(updated) -> Badge.UPDATED
            else -> null
        }
    }
}
