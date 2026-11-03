package io.github.henry753951.airplayremote.launcher

/**
 * Favorites are keys: "app:<package>", "input:<TV input id>" or "telik:<TvHomeEntry name>".
 * Pure rules, free of Android types, so they run in plain unit tests.
 */
object Favorites {
    const val APP = "app:"
    const val INPUT = "input:"
    const val TELIK = "telik:"
    /** Default favorites when the user has none yet. */
    const val SUGGESTED = 5

    fun toggle(keys: List<String>, key: String): List<String> = if (key in keys) keys - key else keys + key

    fun move(keys: List<String>, key: String, delta: Int): List<String> = TileLayout.move(keys, key, delta)

    /** Keeps the keys whose target exists now. An input that comes back shows again. */
    fun present(keys: List<String>, apps: Set<String>, inputs: Set<String>, telik: Set<String>): List<String> =
        keys.filter {
            when {
                it.startsWith(APP) -> it.removePrefix(APP) in apps
                it.startsWith(INPUT) -> it.removePrefix(INPUT) in inputs
                it.startsWith(TELIK) -> it.removePrefix(TELIK) in telik
                else -> false
            }
        }

    /** The apps opened most from Telik, the last used first when counts are equal. */
    fun suggested(launches: List<Launch>, apps: Set<String>, limit: Int = SUGGESTED): List<String> =
        launches.filter { it.packageName in apps }
            .sortedWith(compareByDescending<Launch> { it.count }.thenByDescending { it.lastMillis })
            .take(limit)
            .map { APP + it.packageName }
}

/** One app in the launch record. The record stays on the TV (FR-012). */
data class Launch(val packageName: String, val lastMillis: Long, val count: Int)

object LaunchLog {
    /** Enough for the recent row and the suggestions; older entries drop off. */
    const val LIMIT = 50

    fun record(log: List<Launch>, packageName: String, now: Long): List<Launch> {
        val old = log.firstOrNull { it.packageName == packageName }
        val entry = Launch(packageName, now, (old?.count ?: 0) + 1)
        return (listOf(entry) + log.filter { it.packageName != packageName })
            .sortedByDescending { it.lastMillis }
            .take(LIMIT)
    }

    fun format(log: List<Launch>): String =
        log.joinToString("\n") { "${it.packageName}\t${it.lastMillis}\t${it.count}" }

    fun parse(text: String?): List<Launch> = text.orEmpty().lines().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size != 3) return@mapNotNull null
        val last = parts[1].toLongOrNull() ?: return@mapNotNull null
        val count = parts[2].toIntOrNull() ?: return@mapNotNull null
        Launch(parts[0], last, count)
    }
}
