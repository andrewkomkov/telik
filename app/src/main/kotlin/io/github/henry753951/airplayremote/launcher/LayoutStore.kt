package io.github.henry753951.airplayremote.launcher

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.launcherStore by preferencesDataStore("launcher")

/** Order and hidden flags, stored on the TV only. Package names never contain a newline. */
class LayoutStore(private val context: Context) {
    private val orderKey = stringPreferencesKey("order")
    private val hiddenKey = stringPreferencesKey("hidden")

    val layout: Flow<HomeLayout> = context.launcherStore.data.map { p ->
        HomeLayout(
            order = p[orderKey].orEmpty().lines().filter { it.isNotBlank() },
            hidden = p[hiddenKey].orEmpty().lines().filter { it.isNotBlank() }.toSet(),
        )
    }

    private val favoritesKey = stringPreferencesKey("favorites")
    private val launchesKey = stringPreferencesKey("launches")

    val favorites: Flow<List<String>> = context.launcherStore.data.map { p ->
        p[favoritesKey].orEmpty().lines().filter { it.isNotBlank() }
    }

    val launches: Flow<List<Launch>> = context.launcherStore.data.map { LaunchLog.parse(it[launchesKey]) }

    suspend fun saveFavorites(keys: List<String>) {
        context.launcherStore.edit { it[favoritesKey] = keys.joinToString("\n") }
    }

    suspend fun recordLaunch(packageName: String, now: Long) {
        context.launcherStore.edit {
            it[launchesKey] = LaunchLog.format(LaunchLog.record(LaunchLog.parse(it[launchesKey]), packageName, now))
        }
    }

    suspend fun save(layout: HomeLayout) {
        context.launcherStore.edit {
            it[orderKey] = layout.order.joinToString("\n")
            it[hiddenKey] = layout.hidden.joinToString("\n")
        }
    }
}
