package io.github.henry753951.airplayremote.launcher

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.github.henry753951.airplayremote.MainActivity
import io.github.henry753951.airplayremote.R
import io.github.henry753951.airplayremote.launcher.ui.HomeScreen
import io.github.henry753951.airplayremote.launcher.ui.HomeState
import io.github.henry753951.airplayremote.launcher.ui.InputEntry
import kotlinx.coroutines.launch

/**
 * Telik Home. Small on purpose (FR-013): no Hilt, no view model, no image library.
 * Plain state in the activity, one Compose screen.
 */
class LauncherActivity : ComponentActivity() {
    private lateinit var guard: CrashGuard
    private lateinit var store: LayoutStore
    private lateinit var repo: AppRepository

    private var state by mutableStateOf(HomeState())
    private var layout = HomeLayout()
    private var apps: List<AppTile> = emptyList()

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshDefault()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        guard = CrashGuard(this)
        if (guard.onStart()) {
            Toast.makeText(this, R.string.home_crash_notice, Toast.LENGTH_LONG).show()
            finish()
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        // Home is the root: Back only closes edit mode or the menu.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (state.editing != null || state.menuOpen) state = state.copy(editing = null, menuOpen = false)
            }
        })
        store = LayoutStore(this)
        repo = AppRepository(this)
        state = state.copy(inputs = loadInputs(), isDefault = isDefaultHome(), glass = prefs().getBoolean(GLASS, true))

        setContent {
            HomeScreen(
                state = state,
                onFirstFrame = guard::onFirstFrame,
                onLaunch = ::launchApp,
                onTelik = { startActivity(Intent(this, MainActivity::class.java)) },
                onInput = ::openInput,
                onSettings = { open(Intent(Settings.ACTION_SETTINGS)) },
                onSearch = { open(Intent("android.search.action.GLOBAL_SEARCH")) },
                onMakeDefault = ::requestHome,
                onRestoreOld = ::restoreOldHome,
                onToggleGlass = ::toggleGlass,
                onEdit = { state = state.copy(editing = it) },
                onCloseEdit = { state = state.copy(editing = null) },
                onMenu = { state = state.copy(menuOpen = it) },
                onMove = { pkg, d -> update { it.copy(order = TileLayout.move(fullOrder(), pkg, d)) } },
                onHide = { pkg, h -> update { TileLayout.setHidden(it.copy(order = fullOrder()), pkg, h) } },
                onInfo = { pkg ->
                    open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)))
                },
            )
        }

        lifecycleScope.launch { store.layout.collect { layout = it; rebuild() } }
        lifecycleScope.launch { repo.apps.collect { apps = it; rebuild() } }
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) refreshDefault()
    }

    // Home pressed while Home is open: leave edit mode and go back to the first tile.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) state = state.copy(homeTick = state.homeTick + 1)
    }

    private fun fullOrder() = TileLayout.arrange(apps, layout, includeHidden = true).map { it.packageName }

    private fun update(block: (HomeLayout) -> HomeLayout) {
        layout = block(layout)
        rebuild()
        lifecycleScope.launch { store.save(layout) }
    }

    private fun rebuild() {
        state = state.copy(
            tiles = TileLayout.arrange(apps, layout),
            editTiles = TileLayout.arrange(apps, layout, includeHidden = true),
            hidden = layout.hidden,
        )
    }

    private fun launchApp(tile: AppTile) {
        val intent = Intent(Intent.ACTION_MAIN)
            .setComponent(ComponentName(tile.packageName, tile.activity))
            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        open(intent)
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.home_cannot_open, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadInputs(): List<InputEntry> = runCatching {
        val tv = getSystemService(TV_INPUT_SERVICE) as TvInputManager
        tv.tvInputList.filter { it.isPassthroughInput && it.type == TvInputInfo.TYPE_HDMI }
            .map { InputEntry(it.id, it.loadLabel(this).toString()) }
    }.getOrDefault(emptyList())

    private fun openInput(id: String) =
        open(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id)))

    private fun isDefaultHome(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == packageName
    }

    private fun refreshDefault() { state = state.copy(isDefault = isDefaultHome()) }

    /** Role dialog if the TV has one, then the system Home settings, then the chooser. */
    private fun requestHome() {
        guard.setHomeEnabled(true)
        val rm = getSystemService(RoleManager::class.java)
        val intent = when {
            rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME) ->
                rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
            packageManager.resolveActivity(Intent(Settings.ACTION_HOME_SETTINGS), 0) != null ->
                Intent(Settings.ACTION_HOME_SETTINGS)
            else -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        }
        try { roleRequest.launch(intent) } catch (_: Exception) {
            Toast.makeText(this, R.string.home_cannot_open, Toast.LENGTH_SHORT).show()
        }
    }

    /** FR-007: one path back. Switching the alias off hands Home to the maker screen. */
    private fun restoreOldHome() {
        guard.setHomeEnabled(false)
        Toast.makeText(this, R.string.home_restored, Toast.LENGTH_LONG).show()
        state = state.copy(menuOpen = false)
        refreshDefault()
    }

    private fun toggleGlass() {
        val on = !state.glass
        prefs().edit().putBoolean(GLASS, on).apply()
        state = state.copy(glass = on)
    }

    private fun prefs() = getSharedPreferences("launcher_ui", MODE_PRIVATE)

    private companion object { const val GLASS = "glass" }
}
