package io.github.henry753951.airplayremote.launcher

import android.Manifest
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.henry753951.airplayremote.MainActivity
import io.github.henry753951.airplayremote.Prefs
import io.github.henry753951.airplayremote.appSettingsIntent
import io.github.henry753951.airplayremote.get
import io.github.henry753951.airplayremote.hasLocalNetwork
import io.github.henry753951.airplayremote.runtime.AirPlayService
import io.github.henry753951.airplayremote.R
import io.github.henry753951.airplayremote.launcher.ui.HomeScreen
import io.github.henry753951.airplayremote.launcher.ui.HomeState
import io.github.henry753951.airplayremote.launcher.ui.InputEntry
import io.github.henry753951.airplayremote.ui.TvHomeEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.henry753951.airplayremote.headphones.LocalShell
import io.github.henry753951.airplayremote.headphones.ShellResult

/**
 * Telik Home. Small on purpose (FR-013): no Hilt, no view model, no image library.
 * Plain state in the activity, one Compose screen.
 */
class LauncherActivity : ComponentActivity() {
    private lateinit var guard: CrashGuard
    private lateinit var store: LayoutStore
    private lateinit var repo: AppRepository
    private lateinit var telik: TelikWatcher

    private var state by mutableStateOf(HomeState())
    private var layout = HomeLayout()
    private var apps: List<AppTile> = emptyList()
    private var favorites: List<String> = emptyList()
    private var launches: List<Launch> = emptyList()
    private var watchNext: List<WatchNextItem> = emptyList()
    private var previewItems: List<PreviewItem> = emptyList()
    private lateinit var nowPlaying: NowPlayingWatcher

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshDefault()
    }

    // FR-019: the Home screen asks for what MainActivity asked for before, and starts AirPlay.
    private var askedLocalNetwork = false
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        syncLocalNetwork()
        autoStart()
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
                if (state.editing != null || state.menuOpen || state.editingFavorite != null || state.appMenu != null) {
                    state = state.copy(editing = null, menuOpen = false, editingFavorite = null, appMenu = null)
                }
            }
        })
        store = LayoutStore(this)
        repo = AppRepository(this)
        telik = TelikWatcher(this, lifecycleScope)
        nowPlaying = NowPlayingWatcher(this)
        state = state.copy(
            inputs = loadInputs(), isDefault = isDefaultHome(), glass = prefs().getBoolean(GLASS, true),
            localNetwork = hasLocalNetwork(),
        )
        // Start once per launch, as MainActivity did. Home comes back often, and a user who
        // turned AirPlay off does not want it back on each visit.
        if (savedInstanceState == null) {
            askMissingPermissions()
            autoStart()
        }

        setContent {
            HomeScreen(
                state = state,
                onFirstFrame = guard::onFirstFrame,
                onLaunch = ::launchApp,
                onWatchNext = ::openWatchNext,
                onTelik = { openTelikPage(MainActivity.PAGE_SETTINGS) },
                onTelikEntry = ::openTelikEntry,
                onAttention = ::fixAttention,
                onToggleFavorite = ::toggleFavorite,
                onNowPlaying = { state.nowPlaying?.let { openPackage(it.packageName) } },
                onPlayPause = { nowPlaying.playPause() },
                onNext = { nowPlaying.next() },
                onAllowNowPlaying = { open(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) },
                onDismissNowPlaying = {
                    prefs().edit().putBoolean(NP_DISMISSED, true).apply()
                    state = state.copy(nowPlayingHint = false)
                },
                onAllowListings = ::askListings,
                onDismissListings = {
                    prefs().edit().putBoolean(LISTINGS_DISMISSED, true).apply()
                    state = state.copy(listingsHint = false)
                },
                onEditFavorite = { state = state.copy(editingFavorite = it) },
                onMoveFavorite = { key, d -> saveFavorites(Favorites.move(favorites, key, d)) },
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
                onAppMenu = { pkg -> state = state.copy(appMenu = pkg, menuShortcuts = pkg?.let(::shortcutsOf).orEmpty()) },
                onShortcut = ::startShortcut,
                onStopApp = ::stopApp,
                onUninstall = { pkg -> open(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null))) },
                onInfo = { pkg ->
                    open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)))
                },
            )
        }

        lifecycleScope.launch { store.layout.collect { layout = it; rebuild() } }
        lifecycleScope.launch { repo.apps.collect { apps = it; rebuild() } }
        lifecycleScope.launch { store.favorites.collect { favorites = it; rebuild() } }
        lifecycleScope.launch { nowPlaying.current.collect { state = state.copy(nowPlaying = it) } }
        lifecycleScope.launch {
            nowPlaying.granted.collect { state = state.copy(nowPlayingHint = !it && !prefs().getBoolean(NP_DISMISSED, false)) }
        }
        lifecycleScope.launch { store.launches.collect { launches = it; rebuild() } }
        lifecycleScope.launch { Installs.flow(this@LauncherActivity).collect { state = state.copy(installs = it) } }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { telik.status.collect { state = state.copy(telik = it) } }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::telik.isInitialized) telik.bind()
        if (::nowPlaying.isInitialized) nowPlaying.start()
        syncLocalNetwork()
    }

    private fun askMissingPermissions() {
        val missing = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this@LauncherActivity, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
            if (!hasLocalNetwork()) add(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
        if (missing.isNotEmpty()) {
            if (Manifest.permission.ACCESS_LOCAL_NETWORK in missing) askedLocalNetwork = true
            permissions.launch(missing.toTypedArray())
        }
    }

    private fun syncLocalNetwork() { state = state.copy(localNetwork = hasLocalNetwork()) }

    /** Start AirPlay when auto start is on. A running server ignores the start. */
    private fun autoStart() {
        if (!hasLocalNetwork()) return
        if (getSharedPreferences(Prefs.NAME, MODE_PRIVATE)[Prefs.AUTO_START]) startAirPlay()
    }

    private fun startAirPlay() {
        val intent = Intent(this, AirPlayService::class.java).setAction(AirPlayService.ACTION_START_SERVER)
        runCatching { ContextCompat.startForegroundService(this, intent) }
    }

    private fun fixAttention(attention: Attention) {
        when (attention) {
            // after one refusal the system dialog does not come back; the app settings do
            Attention.LOCAL_NETWORK ->
                if (askedLocalNetwork && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_LOCAL_NETWORK)) {
                    open(appSettingsIntent())
                } else {
                    askedLocalNetwork = true
                    permissions.launch(arrayOf(Manifest.permission.ACCESS_LOCAL_NETWORK))
                }
            Attention.AIRPLAY_ERROR, Attention.AIRPLAY_OFF -> startAirPlay()
            Attention.NETWORK -> open(Intent(Settings.ACTION_WIFI_SETTINGS))
            Attention.REMOTE_ERROR -> openTelikPage(MainActivity.PAGE_REMOTE_SETTINGS)
        }
    }

    private fun openTelikPage(page: String) =
        open(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_PAGE, page))

    override fun onStop() {
        if (::telik.isInitialized) telik.unbind()
        if (::nowPlaying.isInitialized) nowPlaying.stop()
        super.onStop()
    }

    /** Telik cards open the matching page of the Telik screen; Back there returns here. */
    private fun openTelikEntry(entry: TvHomeEntry) {
        val page = when (entry) {
            TvHomeEntry.CAST -> MainActivity.PAGE_CAST
            TvHomeEntry.REMOTE -> MainActivity.PAGE_REMOTE
            TvHomeEntry.SPEAKERS -> MainActivity.PAGE_SPEAKERS
            TvHomeEntry.HEADPHONES -> return telik.toggleHeadphones()
            TvHomeEntry.HOMEKIT -> return open(Intent(this, dev.tvhomekit.HomeKitActivity::class.java))
        }
        openTelikPage(page)
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) refreshDefault()
        refreshListingsHint()
        // US5: Watch Next can change while another app is open, so read it on each return.
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { ContinueWatching.read(applicationContext) }
            val previews = withContext(Dispatchers.IO) { AppPreviews.read(applicationContext) }
            if (items != watchNext || previews != previewItems) { watchNext = items; previewItems = previews; rebuild() }
        }
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
        val tiles = TileLayout.arrange(apps, layout)
        val own = Favorites.present(
            favorites, apps.mapTo(HashSet()) { it.packageName }, state.inputs.mapTo(HashSet()) { it.id },
            TvHomeEntry.entries.mapTo(HashSet()) { it.name },
        )
        val visible = tiles.mapTo(HashSet()) { it.packageName }
        val shown = own.ifEmpty { Favorites.suggested(launches, visible) }
        val now = System.currentTimeMillis()
        val lastLaunch = launches.associate { it.packageName to it.lastMillis }
        state = state.copy(
            continueWatching = ContinueWatching.pick(watchNext, apps.mapTo(HashSet()) { it.packageName }),
            previews = AppPreviews.pick(previewItems, apps.mapTo(HashSet()) { it.packageName }),
            recent = Recent.pick(launches, visible, shown.filter { it.startsWith(Favorites.APP) }.mapTo(HashSet()) { it.removePrefix(Favorites.APP) }),
            badges = apps.mapNotNull { a ->
                Recent.badge(a.installedMillis, a.updatedMillis, lastLaunch[a.packageName], now)?.let { a.packageName to it }
            }.toMap(),
            tiles = tiles,
            editTiles = TileLayout.arrange(apps, layout, includeHidden = true),
            hidden = layout.hidden,
            favorites = shown,
            favoritesSuggested = own.isEmpty(),
            favoriteKeys = favorites.toSet(),
            editingFavorite = state.editingFavorite?.takeIf { it in shown },
        )
    }

    private fun toggleFavorite(key: String) {
        val added = key !in favorites
        saveFavorites(Favorites.toggle(favorites, key))
        Toast.makeText(this, if (added) R.string.home_fav_added else R.string.home_fav_removed, Toast.LENGTH_SHORT).show()
    }

    private fun saveFavorites(keys: List<String>) {
        favorites = keys
        rebuild()
        lifecycleScope.launch { store.saveFavorites(keys) }
    }

    private fun launchApp(tile: AppTile) {
        lifecycleScope.launch { store.recordLaunch(tile.packageName, System.currentTimeMillis()) }
        val intent = Intent(Intent.ACTION_MAIN)
            .setComponent(ComponentName(tile.packageName, tile.activity))
            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        open(intent)
    }

    private fun refreshListingsHint() {
        state = state.copy(listingsHint = !TvListings.granted(this) && !prefs().getBoolean(LISTINGS_DISMISSED, false))
    }

    /**
     * 005 T057: the system dialog for READ_TV_LISTINGS. After "Don't ask again" the system shows no
     * dialog, so the hint opens the app screen of the system instead.
     */
    private fun askListings() {
        val asked = prefs().getBoolean(LISTINGS_ASKED, false)
        if (asked && !shouldShowRequestPermissionRationale(TvListings.PERMISSION)) {
            open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", packageName, null)))
            return
        }
        prefs().edit().putBoolean(LISTINGS_ASKED, true).apply()
        listingsRequest.launch(TvListings.PERMISSION)
    }

    private val listingsRequest = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) {
        refreshListingsHint()
        lifecycleScope.launch {
            watchNext = withContext(Dispatchers.IO) { ContinueWatching.read(applicationContext) }
            previewItems = withContext(Dispatchers.IO) { AppPreviews.read(applicationContext) }
            rebuild()
        }
    }

    /**
     * Opens a Watch Next program in its app (US5). The link comes from another app, so it may only
     * open that app: a link to another package opens the app of the program instead.
     */
    private fun openWatchNext(item: WatchNextItem) {
        lifecycleScope.launch { store.recordLaunch(item.packageName, System.currentTimeMillis()) }
        val link = runCatching { Intent.parseUri(item.intentUri, Intent.URI_INTENT_SCHEME) }.getOrNull()
        val target = link?.component?.packageName ?: link?.`package`
        if (link != null && (target == null || target == item.packageName)) {
            link.selector = null
            if (target == null) link.`package` = item.packageName
            link.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { startActivity(link) }.isSuccess) return
        }
        apps.firstOrNull { it.packageName == item.packageName }?.let(::launchApp)
            ?: Toast.makeText(this, R.string.home_cannot_open, Toast.LENGTH_SHORT).show()
    }

    /** 005 T051: the shortcuts of an app. Only the default Home app may read them. */
    private fun shortcutsOf(pkg: String): List<Pair<String, String>> = runCatching {
        val la = getSystemService(android.content.pm.LauncherApps::class.java)
        if (!la.hasShortcutHostPermission()) return emptyList()
        val q = android.content.pm.LauncherApps.ShortcutQuery().setPackage(pkg).setQueryFlags(
            android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
        )
        la.getShortcuts(q, android.os.Process.myUserHandle()).orEmpty()
            .filter { it.isEnabled }
            .sortedBy { it.rank }
            .mapNotNull { s -> (s.shortLabel ?: s.longLabel)?.toString()?.let { s.id to it } }
            .distinctBy { it.second }
            .take(4)
    }.getOrDefault(emptyList())

    private fun startShortcut(pkg: String, id: String) {
        runCatching {
            getSystemService(android.content.pm.LauncherApps::class.java).startShortcut(pkg, id, null, null, android.os.Process.myUserHandle())
        }.onFailure { Toast.makeText(this, R.string.home_cannot_open, Toast.LENGTH_SHORT).show() }
    }

    /** Force stop through the adb port of the TV, as for the headphones. Without it, the app screen of the system. */
    private fun stopApp(pkg: String) {
        if (!PACKAGE_NAME.matches(pkg)) return
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { LocalShell(this@LauncherActivity).run("am force-stop $pkg") }
            if (result is ShellResult.Done && result.exitCode == 0) {
                Toast.makeText(this@LauncherActivity, R.string.home_stopped, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@LauncherActivity, R.string.home_stop_failed, Toast.LENGTH_LONG).show()
                open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)))
            }
        }
    }

    private fun openPackage(pkg: String) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) open(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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

    private companion object {
        val PACKAGE_NAME = Regex("[A-Za-z0-9_.]+")
        const val GLASS = "glass"
        const val NP_DISMISSED = "now_playing_hint_dismissed"
        const val LISTINGS_DISMISSED = "listings_hint_dismissed"
        const val LISTINGS_ASKED = "listings_asked"
    }
}
