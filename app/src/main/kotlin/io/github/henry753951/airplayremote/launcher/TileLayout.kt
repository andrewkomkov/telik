package io.github.henry753951.airplayremote.launcher

/** One launchable app. */
data class AppTile(val packageName: String, val label: String, val activity: String)

/** Order and hidden flags as stored on the TV. */
data class HomeLayout(val order: List<String> = emptyList(), val hidden: Set<String> = emptySet())

/** Pure ordering rules, kept free of Android types so they run in plain unit tests. */
object TileLayout {
    /**
     * Saved order first (apps that are gone are skipped), then new apps by label.
     * Hidden apps are removed unless [includeHidden] is set (edit mode).
     */
    fun arrange(apps: List<AppTile>, layout: HomeLayout, includeHidden: Boolean = false): List<AppTile> {
        val byPackage = apps.associateBy { it.packageName }
        val known = layout.order.mapNotNull { byPackage[it] }
        val knownSet = known.mapTo(HashSet()) { it.packageName }
        val fresh = apps.filter { it.packageName !in knownSet }
            .sortedBy { it.label.lowercase() }
        val all = known + fresh
        return if (includeHidden) all else all.filter { it.packageName !in layout.hidden }
    }

    /** Moves [pkg] by [delta] places inside the full (hidden included) order. */
    fun move(order: List<String>, pkg: String, delta: Int): List<String> {
        val from = order.indexOf(pkg)
        if (from < 0) return order
        val to = (from + delta).coerceIn(0, order.lastIndex)
        if (to == from) return order
        return order.toMutableList().also { it.add(to, it.removeAt(from)) }
    }

    fun setHidden(layout: HomeLayout, pkg: String, hidden: Boolean): HomeLayout =
        layout.copy(hidden = if (hidden) layout.hidden + pkg else layout.hidden - pkg)
}
