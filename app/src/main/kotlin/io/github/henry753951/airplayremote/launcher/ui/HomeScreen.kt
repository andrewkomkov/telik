package io.github.henry753951.airplayremote.launcher.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.animateColorAsState
import io.github.henry753951.airplayremote.launcher.WatchNextItem
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.State
import androidx.compose.foundation.ScrollState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.produceState
import androidx.compose.ui.geometry.Offset
import io.github.henry753951.airplayremote.launcher.Ambient
import io.github.henry753951.airplayremote.launcher.Recent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import io.github.henry753951.airplayremote.ui.accent
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.Image
import io.github.henry753951.airplayremote.R
import io.github.henry753951.airplayremote.launcher.AppTile
import io.github.henry753951.airplayremote.launcher.Attention
import io.github.henry753951.airplayremote.launcher.Attentions
import io.github.henry753951.airplayremote.launcher.Favorites
import io.github.henry753951.airplayremote.launcher.NowPlaying
import androidx.compose.ui.graphics.asImageBitmap
import io.github.henry753951.airplayremote.launcher.TelikStatus
import io.github.henry753951.airplayremote.audiooutput.SpeakerOutputState
import io.github.henry753951.airplayremote.headphones.HeadphonesStatus
import io.github.henry753951.airplayremote.runtime.AirPlayService.ServerState
import io.github.henry753951.airplayremote.ui.TelikGlyph
import io.github.henry753951.airplayremote.ui.TvHomeEntry
import io.github.henry753951.airplayremote.ui.requestFocusUntilLanded
import io.github.henry753951.airplayremote.ui.theme.AirPlayTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

data class InputEntry(val id: String, val label: String)

data class HomeState(
    val tiles: List<AppTile> = emptyList(),
    val editTiles: List<AppTile> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val inputs: List<InputEntry> = emptyList(),
    val isDefault: Boolean = true,
    val glass: Boolean = true,
    val editing: String? = null,
    val menuOpen: Boolean = false,
    /** Changes when Home is pressed while Home is already open. */
    val homeTick: Int = 0,
    val telik: TelikStatus = TelikStatus(),
    /** ACCESS_LOCAL_NETWORK is granted. */
    val localNetwork: Boolean = true,
    /** Keys of the Favorites row, in order: the user's favorites, or suggestions while there are none. */
    val favorites: List<String> = emptyList(),
    val favoritesSuggested: Boolean = false,
    /** The user's own favorites, for the add and remove labels. */
    val favoriteKeys: Set<String> = emptySet(),
    /** Favorite in edit mode: move or remove. */
    val editingFavorite: String? = null,
    /** 005 US2: the media session of any app that plays now. */
    val nowPlaying: NowPlaying? = null,
    /** Notification access is missing and the user did not say "not now". */
    val nowPlayingHint: Boolean = false,
    /** Package of the app menu that a long OK opened. */
    val appMenu: String? = null,
    /** 005 US3: packages of the Recent row, last used first. */
    val recent: List<String> = emptyList(),
    val badges: Map<String, Recent.Badge> = emptyMap(),
    /** 005 US5: Watch Next programs of other apps, last used first. */
    val continueWatching: List<WatchNextItem> = emptyList(),
)


@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun HomeScreen(
    state: HomeState,
    onFirstFrame: () -> Unit,
    onLaunch: (AppTile) -> Unit,
    onWatchNext: (WatchNextItem) -> Unit,
    onTelik: () -> Unit,
    onTelikEntry: (TvHomeEntry) -> Unit,
    onAttention: (Attention) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onNowPlaying: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onAllowNowPlaying: () -> Unit,
    onDismissNowPlaying: () -> Unit,
    onEditFavorite: (String?) -> Unit,
    onMoveFavorite: (String, Int) -> Unit,
    onInput: (String) -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    onMakeDefault: () -> Unit,
    onRestoreOld: () -> Unit,
    onToggleGlass: () -> Unit,
    onEdit: (String) -> Unit,
    onCloseEdit: () -> Unit,
    onMenu: (Boolean) -> Unit,
    onMove: (String, Int) -> Unit,
    onHide: (String, Boolean) -> Unit,
    onInfo: (String) -> Unit,
    onAppMenu: (String?) -> Unit,
    onStopApp: (String) -> Unit,
    onUninstall: (String) -> Unit,
) = AirPlayTheme {
    // Frame watch (FR-012): slow frames while focus moves switch the effects to plain.
    var slow by remember { mutableStateOf(false) }
    // Read only in snapshotFlow and in callbacks: a focus change does not recompose this scope (FR-022).
    val focusStamp = remember { mutableIntStateOf(0) }
    val fancy = state.glass && !slow

    LaunchedEffect(Unit) { withFrameNanos { }; onFirstFrame() }
    LaunchedEffect(Unit) {
        snapshotFlow { focusStamp.intValue }.collectLatest { stamp ->
            if (stamp == 0 || slow) return@collectLatest
            var last = 0L
            var sum = 0L
            var n = 0
            // The loop asks for each frame, so each gap is one frame, also a long one.
            withTimeoutOrNull(500) {
                while (true) {
                    withFrameNanos { t ->
                        if (last != 0L) { sum += t - last; n++ }
                        last = t
                    }
                }
            }
            if (n >= 3 && sum / n > 40_000_000L) slow = true
        }
    }

    val editing = state.editing
    val gridTiles = if (editing != null) state.editTiles else state.tiles
    val firstTile = remember { FocusRequester() }
    val moveFocus = remember { FocusRequester() }
    // A menu returns the focus to the tile that opened it, or to the first tile when that tile is gone.
    val originFocus = remember { FocusRequester() }
    var origin by remember { mutableStateOf<String?>(null) }
    val menuOpen = state.appMenu != null || state.editingFavorite != null
    LaunchedEffect(menuOpen) {
        if (menuOpen || origin == null) return@LaunchedEffect
        val back = runCatching { originFocus.requestFocus() }.getOrDefault(false)
        if (!back) firstTile.requestFocusUntilLanded()
        origin = null
    }
    // The moved tile changes its place in the grid; the focus goes with it.
    LaunchedEffect(editing, gridTiles.map { it.packageName }) { if (editing != null) moveFocus.requestFocusUntilLanded() }
    val scroll = rememberScrollState()
    val titleShown = remember { derivedStateOf { scroll.value == 0 } }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // The item in focus drives the backdrop and the title area, as on the Apple TV Home screen.
    val info = remember { mutableStateOf<FocusInfo?>(null) }
    val focusOn: (FocusInfo) -> Unit = remember { { i -> info.value = i; focusStamp.intValue++ } }
    val pillFocus: () -> Unit = remember { { info.value = null; focusStamp.intValue++ } }

    // Rows scroll as a whole (HIG: never half a row). The top rows keep the page at the top; a
    // lower row moves up to a fixed place under the safe area.
    val rowTops = remember { mutableStateMapOf<Int, Int>() }
    val onRowFocus = { row: Int ->
        val target = if (row <= 1) 0 else ((rowTops[row] ?: 0) - with(density) { RowPivot.roundToPx() }).coerceAtLeast(0)
        scope.launch { scroll.animateScrollTo(target, tween(320, easing = FastOutSlowInEasing)) }
        Unit
    }

    LaunchedEffect(state.homeTick) {
        if (state.homeTick > 0) {
            scroll.scrollTo(0)
            firstTile.requestFocusUntilLanded()
        }
    }
    LaunchedEffect(Unit) { firstTile.requestFocusUntilLanded() }
    // Favorites and apps load after the first frame. Until the user presses a key, the start focus
    // follows the first tile, so it never stays on a row that moved or on nothing.
    var userMoved by remember { mutableStateOf(false) }
    LaunchedEffect(state.favorites.firstOrNull(), gridTiles.firstOrNull()?.packageName) {
        if (!userMoved) firstTile.requestFocusUntilLanded()
    }

    val pending = Attentions.pick(state.localNetwork, state.telik.server, state.telik.remoteEnabled, state.telik.remote)
    var attention by remember { mutableStateOf<Attention?>(null) }
    LaunchedEffect(pending) { if (pending == null) attention = null else { delay(ATTENTION_DELAY_MS); attention = pending } }

    Box(Modifier.fillMaxSize().onPreviewKeyEvent { userMoved = true; false }) {
        Backdrop(info, state.nowPlaying, fancy, scroll)
      CompositionLocalProvider(LocalTitleShown provides titleShown) {

        val defaultSpec = LocalBringIntoViewSpec.current
        CompositionLocalProvider(LocalBringIntoViewSpec provides NoVerticalBringIntoView) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(vertical = SafeV)) {
                CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                    // Top bar: Telik settings, inputs, search, settings, menu, and the clock.
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = SafeH).onFocusChanged { if (it.hasFocus) onRowFocus(-1) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                      // The pills scroll. The clock keeps its place.
                      Row(
                        Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                      ) {
                        Pill(Icons.Default.Settings, stringResource(R.string.home_entry_telik), fancy, pillFocus, onClick = onTelik)
                        state.inputs.forEach { i ->
                            Pill(Icons.AutoMirrored.Filled.Input, i.label, fancy, pillFocus, { onToggleFavorite(Favorites.INPUT + i.id) }) {
                                onInput(i.id)
                            }
                        }
                        Pill(Icons.Default.Search, stringResource(R.string.home_entry_search), fancy, pillFocus, iconOnly = true, onClick = onSearch)
                        Pill(Icons.Default.Tune, stringResource(R.string.home_entry_settings), fancy, pillFocus, iconOnly = true, onClick = onSettings)
                        Pill(Icons.Default.Home, stringResource(R.string.home_entry_home), fancy, pillFocus, iconOnly = true) { onMenu(true) }
                      }
                        Clock()
                    }

                    // Title area (the Top Shelf place): a problem, the media that plays, or the item in focus.
                    Box(Modifier.fillMaxWidth().height(ShelfHeight).padding(horizontal = SafeH), contentAlignment = Alignment.BottomStart) {
                        val a = attention
                        val np = state.nowPlaying
                        when {
                            a != null -> AttentionRow(a, fancy) { onAttention(a) }
                            np != null -> NowPlayingCard(np, fancy, { focusStamp.intValue++; onRowFocus(-1) }, onNowPlaying, onPlayPause, onNext)
                            state.nowPlayingHint -> NowPlayingHint(fancy, onAllowNowPlaying, onDismissNowPlaying)
                            else -> FocusTitle(info, state.isDefault, state.telik)
                        }
                    }

                    // Row 0: favorites, the top row of the grid. It never gets a title, as the Apple TV dock.
                    val byPackage = state.editTiles.associateBy { it.packageName }
                    val inputs = state.inputs.associateBy { it.id }
                    var firstSet = false
                    fun firstMod(): Modifier = if (!firstSet) { firstSet = true; Modifier.focusRequester(firstTile) } else Modifier
                    // Move mode, as on Apple TV: the arrows move the tile, OK ends the mode.
                    fun moveMod(pkg: String): Modifier = Modifier.focusRequester(moveFocus).onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionLeft -> { onMove(pkg, -1); true }
                            Key.DirectionRight -> { onMove(pkg, 1); true }
                            Key.DirectionUp, Key.DirectionDown -> true
                            else -> false
                        }
                    }
                    if (editing == null && state.favorites.isNotEmpty()) {
                        RowTitle(if (state.favoritesSuggested) stringResource(R.string.home_fav_suggested) else null)
                        TileRow(0, rowTops, onRowFocus) {
                            state.favorites.forEach { key ->
                                val selected = state.editingFavorite == key
                                val tag = "fav:$key"
                                val back = if (origin == tag) Modifier.focusRequester(originFocus) else Modifier
                                val onLong = { origin = tag; onEditFavorite(key) }
                                when {
                                    key.startsWith(Favorites.APP) -> byPackage[key.removePrefix(Favorites.APP)]?.let { app ->
                                        AppTileView(
                                            app, dim = false, selected = selected, fancy = fancy, badge = null, modifier = firstMod().then(back),
                                            onFocused = focusOn,
                                            onClick = { if (state.editingFavorite != null) onEditFavorite(key) else onLaunch(app) },
                                            onLongClick = { origin = tag; onAppMenu(app.packageName) },
                                        )
                                    }
                                    key.startsWith(Favorites.INPUT) -> inputs[key.removePrefix(Favorites.INPUT)]?.let { i ->
                                        InputTile(i, fancy, firstMod().then(back), focusOn, onLong) { onInput(i.id) }
                                    }
                                    key.startsWith(Favorites.TELIK) ->
                                        TvHomeEntry.entries.firstOrNull { it.name == key.removePrefix(Favorites.TELIK) }?.let { entry ->
                                            val (title, subtitle, active) = telikCardText(entry, state.telik)
                                            TelikTile(entry, title, subtitle, active, fancy, firstMod().then(back), focusOn, onLong) { onTelikEntry(entry) }
                                        }
                                }
                            }
                        }
                    }

                    // Row 1: Continue watching (US5), Watch Next of other apps.
                    val watching = if (editing == null) state.continueWatching else emptyList()
                    val recentIndex = if (watching.isEmpty()) 1 else 2
                    if (watching.isNotEmpty()) {
                        RowTitle(stringResource(R.string.home_cw_title))
                        TileRow(1, rowTops, onRowFocus) {
                            watching.forEach { item ->
                                WatchNextCard(item, fancy, firstMod(), focusOn) { onWatchNext(item) }
                            }
                        }
                    }

                    // Then Recent (US3), only outside edit mode and only when there is something to show.
                    val recentApps = if (editing == null) state.recent.mapNotNull { byPackage[it] } else emptyList()
                    val gridStart = if (recentApps.isEmpty()) recentIndex else recentIndex + 1
                    if (recentApps.isNotEmpty()) {
                        RowTitle(stringResource(R.string.home_recent_title))
                        TileRow(recentIndex, rowTops, onRowFocus) {
                            recentApps.forEach { app ->
                                val tag = "recent:" + app.packageName
                                AppTileView(
                                    app, dim = false, selected = false, fancy = fancy, badge = null,
                                    modifier = firstMod().then(if (origin == tag) Modifier.focusRequester(originFocus) else Modifier),
                                    onFocused = focusOn,
                                    onClick = { onLaunch(app) },
                                    onLongClick = { origin = tag; onAppMenu(app.packageName) },
                                )
                            }
                        }
                    }

                    // Then all apps in a grid of five columns (HIG: 320 pt tiles, 40 pt gaps).
                    RowTitle(stringResource(R.string.home_apps_title))
                    val gridLines = gridTiles.chunked(Columns)
                    val gridRows = gridLines.size
                    gridLines.forEachIndexed { r, line ->
                        TileRow(gridStart + r, rowTops, onRowFocus, scrollable = false) {
                            line.forEach { app ->
                                val moving = editing == app.packageName
                                AppTileView(
                                    app,
                                    dim = editing != null && app.packageName in state.hidden,
                                    selected = moving,
                                    fancy = fancy,
                                    badge = state.badges[app.packageName],
                                    modifier = firstMod().then(if (moving) moveMod(app.packageName) else Modifier)
                                        .then(if (origin == "app:" + app.packageName) Modifier.focusRequester(originFocus) else Modifier),
                                    onFocused = focusOn,
                                    onClick = { if (moving) onCloseEdit() else if (editing == null) onLaunch(app) },
                                    onLongClick = { if (editing == null) { origin = "app:" + app.packageName; onAppMenu(app.packageName) } },
                                )
                            }
                        }
                    }
                    // Last row: Telik, under the apps, as the user asked. Its state shows in the title area on focus.
                    if (editing == null) {
                        RowTitle(stringResource(R.string.tv_home_title))
                        TileRow(gridStart + gridRows, rowTops, onRowFocus) {
                            TvHomeEntry.entries.forEach { entry ->
                                val (title, subtitle, active) = telikCardText(entry, state.telik)
                                TelikTile(entry, title, subtitle, active, fancy, firstMod(), focusOn, { onToggleFavorite(Favorites.TELIK + entry.name) }) {
                                    onTelikEntry(entry)
                                }
                            }
                        }
                    }

                    Box(Modifier.height(ShelfHeight))
                }
            }
        }

        // Edit panels float over the bottom edge, inside the safe area.
        val panelMod = Modifier.align(Alignment.BottomCenter).padding(horizontal = SafeH, vertical = SafeV)
        if (editing != null) {
            Text(
                stringResource(R.string.home_move_hint),
                panelMod.background(Color(0xF0101318), RoundedCornerShape(20.dp)).padding(horizontal = 24.dp, vertical = 12.dp),
                color = Color.White, fontSize = TvType.Body, fontWeight = FontWeight.Medium,
            )
        }
        state.editingFavorite?.let { key ->
            ModalMenu(favoriteTitle(key, state), fancy) { first ->
                if (!state.favoritesSuggested) {
                    MenuButton(stringResource(R.string.home_edit_left), fancy, first) { onMoveFavorite(key, -1) }
                    MenuButton(stringResource(R.string.home_edit_right), fancy) { onMoveFavorite(key, 1) }
                }
                MenuButton(
                    stringResource(if (key in state.favoriteKeys) R.string.home_fav_remove else R.string.home_fav_add), fancy,
                    if (state.favoritesSuggested) first else Modifier,
                ) { onEditFavorite(null); onToggleFavorite(key) }
                MenuButton(stringResource(R.string.home_edit_done), fancy) { onEditFavorite(null) }
            }
        }

        state.appMenu?.let { pkg ->
            val app = state.editTiles.firstOrNull { it.packageName == pkg }
            val favKey = Favorites.APP + pkg
            val ownApp = pkg == LocalContext.current.packageName
            ModalMenu(app?.label ?: pkg, fancy) { first ->
                MenuButton(stringResource(R.string.home_menu_open), fancy, first) { onAppMenu(null); app?.let(onLaunch) }
                if (!ownApp) MenuButton(stringResource(R.string.home_menu_stop), fancy) { onAppMenu(null); onStopApp(pkg) }
                MenuButton(stringResource(if (favKey in state.favoriteKeys) R.string.home_fav_remove else R.string.home_fav_add), fancy) {
                    onAppMenu(null); onToggleFavorite(favKey)
                }
                MenuButton(stringResource(R.string.home_menu_move), fancy) { onAppMenu(null); onEdit(pkg) }
                MenuButton(stringResource(if (pkg in state.hidden) R.string.home_edit_show else R.string.home_edit_hide), fancy) {
                    onAppMenu(null); onHide(pkg, pkg !in state.hidden)
                }
                MenuButton(stringResource(R.string.home_edit_info), fancy) { onAppMenu(null); onInfo(pkg) }
                if (!ownApp) MenuButton(stringResource(R.string.home_menu_uninstall), fancy) { onAppMenu(null); onUninstall(pkg) }
            }
        }

        if (state.menuOpen) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                val first = remember { FocusRequester() }
                LaunchedEffect(Unit) { first.requestFocusUntilLanded() }
                Column(
                    Modifier.width(360.dp).background(Color(0xF2161A22), RoundedCornerShape(24.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp)).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (!state.isDefault) {
                        MenuButton(stringResource(R.string.home_make_default), fancy, Modifier.focusRequester(first), onClick = onMakeDefault)
                    }
                    MenuButton(
                        stringResource(if (state.glass) R.string.home_glass_on else R.string.home_glass_off),
                        fancy,
                        if (state.isDefault) Modifier.focusRequester(first) else Modifier,
                        onClick = onToggleGlass,
                    )
                    MenuButton(stringResource(R.string.home_restore_old), fancy, onClick = onRestoreOld)
                    MenuButton(stringResource(R.string.home_edit_done), fancy) { onMenu(false) }
                }
            }
        }
      }
    }
}

// ---- layout from the tvOS Human Interface Guidelines; 1 pt on a 1080p TV is 0.5 dp here ----

/** Safe area: 80 pt at the sides, 60 pt at the top and bottom. */
private val SafeH = 40.dp
private val SafeV = 30.dp
/** Five-column grid: 320 pt tiles with 40 pt gaps. Tiles are 16:9, as Android TV banners. */
private const val Columns = 5
private val TileW = 160.dp
private val TileH = 90.dp
private val Gap = 20.dp
/** A focused tile grows into the gap and no further: 160 dp * 1.12 leaves 0.4 dp of each 10 dp. */
private const val FocusGrow = 1.12f
private val ShelfHeight = 124.dp
/** Where a focused lower row stops: its title stays just under the safe area. */
private val RowPivot = 40.dp
private val TileShape = RoundedCornerShape(10.dp)
private val ActiveDot = Color(0xFF34C759)
private val BadgeBlue = Color(0xFF0A84FF)

/** tvOS text styles (Medium weight) at 0.5 dp per pt. */
private object TvType {
    val Title1 = 38.sp
    val Title3 = 24.sp
    val Headline = 19.sp
    val Body = 15.sp
    val Caption = 13.sp
}

/** What the item in focus shows in the title area and the backdrop. */
private data class FocusInfo(
    val title: String,
    val subtitle: String,
    val pkg: String? = null,
    val activity: String? = null,
    val entry: TvHomeEntry? = null,
    /** Art of a focused program (US5): the Top Shelf shows it large. */
    val art: android.graphics.Bitmap? = null,
    val artColor: Int? = null,
    /** Link of the art, for the large version in the Top Shelf. */
    val artUri: String? = null,
)

@OptIn(ExperimentalFoundationApi::class)
private val NoVerticalBringIntoView = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

@Composable
private fun RowTitle(text: String?) {
    if (text == null) return
    Text(
        text, Modifier.padding(start = SafeH, top = 6.dp, bottom = 2.dp),
        color = Color.White.copy(alpha = 0.75f), fontSize = TvType.Headline, fontWeight = FontWeight.Medium,
    )
}

/** One row of tiles. Space above and below fits the focus growth and the label (HIG: 100 pt rows). */
@Composable
private fun TileRow(
    index: Int,
    tops: MutableMap<Int, Int>,
    onFocus: (Int) -> Unit,
    scrollable: Boolean = true,
    content: @Composable () -> Unit,
) {
    val mod = Modifier.fillMaxWidth()
        .onPlaced { tops[index] = it.positionInParent().y.toInt() }
        .onFocusChanged { if (it.hasFocus) onFocus(index) }
    val arrangement = Arrangement.spacedBy(Gap)
    if (scrollable) {
        Row(
            mod.horizontalScroll(rememberScrollState()).padding(horizontal = SafeH).padding(top = 12.dp, bottom = 26.dp),
            horizontalArrangement = arrangement,
        ) { content() }
    } else {
        Row(mod.padding(horizontal = SafeH).padding(top = 12.dp, bottom = 26.dp), horizontalArrangement = arrangement) { content() }
    }
}

/**
 * Backdrop (US8): a glow in the main color of the item in focus, and the art of the media that
 * plays large in the top right (the Top Shelf). It reads the focus here, so a focus change
 * recomposes only this function (FR-022).
 */
@Composable
private fun Backdrop(infoState: State<FocusInfo?>, np: NowPlaying?, fancy: Boolean, scroll: ScrollState) {
    val info = infoState.value
    val playing = np?.art
    val npColor by produceState<Int?>(null, playing) {
        value = playing?.let { withContext(Dispatchers.Default) { BannerCache.mainColor(it) } }
    }
    // The art of a focused program wins over the art of the media that plays. Its large version
    // replaces the card-size one when it arrives.
    val large = rememberLargePoster(info?.artUri)
    val npArt = info?.art?.let { large ?: it } ?: playing
    val pkg = info?.pkg
    val activity = info?.activity
    val appColor = if (pkg != null && activity != null) rememberArt(pkg, activity).value?.color else null
    val target = when {
        info?.art != null -> info.artColor?.let { Color(it) }
        playing != null -> npColor?.let { Color(it) }
        pkg != null -> appColor?.let { Color(it) }
        else -> info?.entry?.accent()?.first()
    } ?: IdleGlow
    val glow by animateColorAsState(target, tween(if (fancy) 600 else 0), label = "glow")
    val shelfAlpha = if (fancy) 0.8f else 0.6f

    Box(
        // One opaque pass over the whole screen (FR-022): near black, with the tone of the item in
        // focus at the top, as on Google TV. The color is read in the draw phase only.
        Modifier.fillMaxSize().drawBehind {
            drawRect(
                Brush.verticalGradient(
                    0f to lerp(BaseColor, glow, 0.30f), 0.5f to lerp(BaseColor, glow, 0.07f), 1f to BaseColor,
                ),
            )
        },
    ) {
        // The fade and the scrim are baked into one bitmap off the main thread, so each frame draws
        // one plain bitmap: the masked shader cost the GPU of the test TV ~110 ms a frame.
        // The value carries its source: produceState keeps the old value when the key changes.
        val bakedState = produceState<Pair<android.graphics.Bitmap, ImageBitmap>?>(npArt?.let { a -> ShelfBake.peek(a)?.let { a to it } }, npArt) {
            value = npArt?.let { art -> art to (ShelfBake.peek(art) ?: withContext(Dispatchers.Default) { ShelfBake.bake(art) }) }
        }
        bakedState.value?.takeIf { it.first === npArt }?.second?.let { image ->
            Box(
                Modifier.align(Alignment.TopEnd).fillMaxWidth(0.68f).fillMaxHeight(0.78f).drawBehind {
                    val fadeRange = ShelfHeight.toPx()
                    // The art goes away as the rows scroll up. The scroll is read in the draw phase only.
                    val a = shelfAlpha * (1f - scroll.value / fadeRange).coerceIn(0f, 1f)
                    if (a > 0f) {
                        drawImage(
                            image, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                            alpha = a, filterQuality = androidx.compose.ui.graphics.FilterQuality.Low,
                        )
                    }
                },
            )
        }
    }
}

/**
 * The Top Shelf art (US5, US8), cropped to the shelf shape and faded to the left (title) and to
 * the bottom (rows). It is made once for each art, on a background thread, in a software canvas.
 */
private object ShelfBake {
    private const val W = 1306 // the shelf box in px: 0.68 of 1920 by 0.78 of 1080
    private const val H = 842
    private val cache = object : android.util.LruCache<android.graphics.Bitmap, ImageBitmap>(3) {}

    fun peek(art: android.graphics.Bitmap?): ImageBitmap? = art?.let { cache.get(it) }

    fun bake(art: android.graphics.Bitmap): ImageBitmap {
        val out = android.graphics.Bitmap.createBitmap(W, H, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val s = maxOf(W.toFloat() / art.width, H.toFloat() / art.height)
        val m = android.graphics.Matrix().apply {
            setScale(s, s)
            postTranslate((W - art.width * s) / 2f, (H - art.height * s) / 2f)
        }
        canvas.drawBitmap(art, m, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        val clamp = android.graphics.Shader.TileMode.CLAMP
        val black = android.graphics.Color.BLACK
        val clear = android.graphics.Color.TRANSPARENT
        val mask = android.graphics.Paint().apply { blendMode = android.graphics.BlendMode.DST_IN }
        mask.shader = android.graphics.LinearGradient(0f, 0f, W * 0.55f, 0f, clear, black, clamp)
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), mask)
        mask.shader = android.graphics.LinearGradient(0f, H * 0.35f, 0f, H.toFloat(), black, clear, clamp)
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), mask)
        // The scrim, darker to the bottom, on the art only: white text stays readable.
        val scrim = android.graphics.Paint().apply {
            blendMode = android.graphics.BlendMode.SRC_ATOP
            shader = android.graphics.LinearGradient(
                0f, 0f, 0f, H.toFloat(), intArrayOf(0x33000000, 0x73000000, 0xB3000000.toInt()), floatArrayOf(0f, 0.55f, 1f), clamp,
            )
        }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), scrim)
        return out.asImageBitmap().also { cache.put(art, it) }
    }
}

private val IdleGlow = Color(0xFF2B4C7E)
private val BaseColor = Color(0xFF0B0D12)

/** True while the large title at the top is on screen (the page is not scrolled). */
private val LocalTitleShown = staticCompositionLocalOf<State<Boolean>> { mutableStateOf(false) }

@Composable
private fun FocusTitle(focusState: State<FocusInfo?>, isDefault: Boolean, telik: TelikStatus) {
    val focus = focusState.value
    // A Telik entry shows its live state, not the state at the moment of the focus.
    val info = focus?.entry?.let { e -> telikCardText(e, telik).let { (t, sub, _) -> focus.copy(title = t, subtitle = sub) } } ?: focus
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(
            info?.title ?: stringResource(R.string.tv_home_title), color = Color.White, fontSize = TvType.Title1,
            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        val sub = info?.subtitle?.takeIf { it.isNotBlank() } ?: if (!isDefault) stringResource(R.string.home_not_default) else ""
        if (sub.isNotBlank()) {
            Text(sub, color = Color.White.copy(alpha = 0.7f), fontSize = TvType.Body, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Clock() {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L)
        }
    }
    val text = remember(now) { android.text.format.DateFormat.getTimeFormat(ctx).format(java.util.Date(now)) }
    Text(text, color = Color.White, fontSize = TvType.Headline, fontWeight = FontWeight.Medium,
        maxLines = 1, softWrap = false,
    )
}

/** The name of a favorite for the title of its menu. */
@Composable
private fun favoriteTitle(key: String, state: HomeState): String = when {
    key.startsWith(Favorites.APP) -> state.editTiles.firstOrNull { it.packageName == key.removePrefix(Favorites.APP) }?.label
    key.startsWith(Favorites.INPUT) -> state.inputs.firstOrNull { it.id == key.removePrefix(Favorites.INPUT) }?.label
    key.startsWith(Favorites.TELIK) ->
        TvHomeEntry.entries.firstOrNull { it.name == key.removePrefix(Favorites.TELIK) }?.let { telikCardText(it, state.telik).first }
    else -> null
} ?: key

/** A menu row: all rows of a menu have one width. */
@Composable
private fun MenuButton(label: String, fancy: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) =
    ActionButton(label, fancy, modifier.fillMaxWidth(), wide = true, onClick = onClick)

/** Centered glass menu over a dim screen, with the title of the app. Back closes it (in the activity). */
@Composable
private fun ModalMenu(title: String, fancy: Boolean, content: @Composable (Modifier) -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { first.requestFocusUntilLanded() }
        Column(
            Modifier.width(320.dp).background(Color(0xF2161A22), RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                title, Modifier.padding(bottom = 6.dp), color = Color.White, fontSize = TvType.Title3,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            content(Modifier.focusRequester(first))
        }
    }
}

@Composable
private fun NowPlayingHint(fancy: Boolean, onAllow: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glassPanel(TileShape, fancy).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.home_np_hint), Modifier.weight(1f), color = Color.White, fontSize = TvType.Body,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        ActionButton(stringResource(R.string.btn_allow), fancy, onClick = onAllow)
        ActionButton(stringResource(R.string.home_np_not_now), fancy, onClick = onDismiss)
    }
}

/** Label under a tile: only the tile in focus shows it (Apple TV Home screen). */
/** A Watch Next card (US5): the art, the progress, and the title in the title area on focus. */
@Composable
private fun WatchNextCard(item: WatchNextItem, fancy: Boolean, modifier: Modifier, onFocused: (FocusInfo) -> Unit, onClick: () -> Unit) {
    val poster by rememberPoster(item.artUri)
    val appLabel = rememberAppLabel(item.packageName)
    val episode = when {
        item.season != null && item.episode != null -> stringResource(R.string.home_cw_episode, item.season, item.episode)
        item.episode != null -> stringResource(R.string.home_cw_episode_only, item.episode)
        else -> null
    }
    val subtitle = listOfNotNull(episode, item.episodeTitle, appLabel).joinToString(" · ")
    val p = poster
    FocusTile(
        fancy, modifier.size(TileW, TileH), false,
        { onFocused(FocusInfo(item.title, subtitle, art = p?.bitmap, artColor = p?.color, artUri = item.artUri)) },
        onClick, null, grow = FocusGrow,
        glow = p?.color?.let { Color(it) } ?: Color.Black,
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xFF1C1F26))) {
            if (p != null) {
                val image = remember(p) { p.bitmap.asImageBitmap() }
                Image(image, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(
                    item.title, Modifier.align(Alignment.Center).padding(12.dp), color = Color.White, fontSize = TvType.Body,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            item.progress?.let { f ->
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.25f)))
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(f).height(4.dp).background(Color.White))
            }
        }
    }
}

@Composable
private fun rememberAppLabel(pkg: String): String? {
    val ctx = LocalContext.current
    return remember(pkg) {
        runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
    }
}

@Composable
private fun FocusLabel(text: String, visible: Boolean) {
    // With the large title on screen, the name shows once: there, not under the tile.
    val shown = visible && !LocalTitleShown.current.value
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(160), label = "label")
    Text(
        text, Modifier.padding(top = 12.dp).width(TileW).graphicsLayer { alpha = a },
        color = Color.White, fontSize = TvType.Caption, fontWeight = FontWeight.Medium,
        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun AppTileView(
    app: AppTile,
    dim: Boolean,
    selected: Boolean,
    fancy: Boolean,
    badge: Recent.Badge?,
    modifier: Modifier,
    onFocused: (FocusInfo) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val art by rememberArt(app.packageName, app.activity)
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.onFocusChanged { focused = it.hasFocus }, horizontalAlignment = Alignment.CenterHorizontally) {
        FocusTile(
            fancy = fancy,
            modifier = modifier.size(TileW, TileH),
            selected = selected,
            onFocused = { onFocused(FocusInfo(app.label, "", app.packageName, app.activity)) },
            onClick = onClick,
            onLongClick = onLongClick,
            grow = FocusGrow,
            glow = art?.color?.let { Color(it) } ?: Color.Black,
        ) {
            val a = art
            val face = when {
                a?.isBanner == true -> Brush.linearGradient(listOf(Color.Black, Color.Black))
                a?.color != null -> Brush.linearGradient(listOf(Color(Ambient.shade(a.color, 0.42f)), Color(Ambient.shade(a.color, 0.22f))))
                else -> Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.08f)))
            }
            Box(
                Modifier.fillMaxSize().background(face)
                    .graphicsLayer { alpha = if (dim) 0.35f else 1f },
                contentAlignment = Alignment.Center,
            ) {
                if (a != null) {
                    Image(
                        a.image, app.label,
                        Modifier.fillMaxSize().let { if (a.isBanner) it else it.padding(18.dp) },
                        contentScale = if (a.isBanner) ContentScale.Crop else ContentScale.Fit,
                    )
                } else {
                    Text(app.label, color = Color.White, fontSize = TvType.Body, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                if (badge != null) TileBadge(badge, Modifier.align(Alignment.TopEnd))
            }
        }
        FocusLabel(app.label, focused)
    }
}

@Composable
private fun TileBadge(badge: Recent.Badge, modifier: Modifier) {
    Text(
        stringResource(if (badge == Recent.Badge.NEW) R.string.home_badge_new else R.string.home_badge_updated),
        modifier.padding(6.dp).background(BadgeBlue, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
        color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
    )
}

@Composable
private fun InputTile(
    input: InputEntry, fancy: Boolean, modifier: Modifier,
    onFocused: (FocusInfo) -> Unit, onLongClick: () -> Unit, onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.onFocusChanged { focused = it.hasFocus }, horizontalAlignment = Alignment.CenterHorizontally) {
        FocusTile(fancy, modifier.size(TileW, TileH), false, { onFocused(FocusInfo(input.label, "")) }, onClick, onLongClick, grow = FocusGrow) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF4B5563), Color(0xFF1F2937)))), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.Input, null, Modifier.size(36.dp), tint = Color.White)
            }
        }
        FocusLabel(input.label, focused)
    }
}

@Composable
private fun TelikTile(
    entry: TvHomeEntry,
    title: String,
    subtitle: String,
    active: Boolean,
    fancy: Boolean,
    modifier: Modifier,
    onFocused: (FocusInfo) -> Unit,
    onLongClick: (() -> Unit)?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.onFocusChanged { focused = it.hasFocus }, horizontalAlignment = Alignment.CenterHorizontally) {
        FocusTile(
            fancy, modifier.size(TileW, TileH), false, { onFocused(FocusInfo(title, subtitle, entry = entry)) }, onClick, onLongClick,
            grow = FocusGrow, glow = entry.accent().last(),
        ) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(entry.accent())), contentAlignment = Alignment.Center) {
                Box(Modifier.graphicsLayer { scaleX = 1.2f; scaleY = 1.2f }) { TelikGlyph(entry) }
                if (active) {
                    Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(10.dp).clip(CircleShape).background(ActiveDot))
                }
            }
        }
        FocusLabel(title, focused)
    }
}

/** Title, state line and whether the entry is in use right now (a green dot). */
@Composable
private fun telikCardText(entry: TvHomeEntry, t: TelikStatus): Triple<String, String, Boolean> = when (entry) {
    TvHomeEntry.CAST -> Triple(
        stringResource(R.string.tv_home_cast),
        when {
            t.server == ServerState.ERROR -> stringResource(R.string.tv_state_error)
            t.server != ServerState.RUNNING -> stringResource(R.string.tv_state_stopped)
            t.mirroring -> stringResource(R.string.home_cast_mirroring)
            t.audioOnly && t.connections > 0 && t.track.isNotBlank() -> stringResource(R.string.home_cast_playing, t.track)
            t.connections > 0 -> stringResource(R.string.waiting_for_playback)
            else -> stringResource(R.string.home_cast_visible, t.serverName)
        },
        t.server == ServerState.RUNNING && t.connections > 0,
    )
    TvHomeEntry.REMOTE -> Triple(
        stringResource(R.string.tv_home_remote),
        stringResource(
            when {
                !t.remoteEnabled -> R.string.tv_state_off
                t.remote == "running" && t.remoteConnected -> R.string.tv_state_connected
                t.remote == "running" -> R.string.tv_state_ready
                t.remote == "starting" -> R.string.tv_state_starting
                t.remote == "waiting" -> R.string.tv_state_waiting_network
                t.remote == "error" -> R.string.tv_state_error
                else -> R.string.tv_state_stopped
            },
        ),
        t.remoteEnabled && t.remote == "running" && t.remoteConnected,
    )
    TvHomeEntry.HEADPHONES -> Triple(
        stringResource(R.string.tv_headphones_title),
        stringResource(
            when (t.headphones) {
                HeadphonesStatus.IDLE -> R.string.tv_headphones_idle
                HeadphonesStatus.NEEDS_APPROVAL -> R.string.tv_headphones_needs_approval
                HeadphonesStatus.NO_DEBUGGING -> R.string.tv_headphones_no_debugging
                HeadphonesStatus.NOT_PAIRED -> R.string.tv_headphones_not_paired
                HeadphonesStatus.CONNECTING -> R.string.tv_headphones_connecting
                HeadphonesStatus.CONNECTED -> R.string.tv_headphones_connected
                HeadphonesStatus.NOT_REACHED -> R.string.tv_headphones_not_reached
            },
        ),
        t.headphones == HeadphonesStatus.CONNECTED,
    )
    TvHomeEntry.HOMEKIT -> Triple(
        stringResource(R.string.tv_home_homekit), stringResource(R.string.tv_home_homekit_sub), false,
    )
    TvHomeEntry.SPEAKERS -> Triple(
        stringResource(R.string.speaker_title),
        speakerLine(t.speaker),
        t.speaker.active && !t.speaker.error,
    )
}

@Composable
private fun speakerLine(s: SpeakerOutputState): String = when {
    s.error -> stringResource(R.string.tv_state_error)
    s.active && s.target.isNotBlank() -> stringResource(R.string.home_speaker_on, s.target)
    else -> stringResource(R.string.speaker_home_sub)
}

/** A large glass card in the Telik row: the entry's icon, its name and its state. */
private const val ATTENTION_DELAY_MS = 1_500L

@Composable
private fun NowPlayingCard(
    np: NowPlaying, fancy: Boolean, onFocused: () -> Unit,
    onOpen: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FocusTile(fancy, Modifier.weight(1f).height(112.dp), false, onFocused, onOpen, null, grow = 1.03f, shadow = false) {
            Row(
                Modifier.fillMaxSize().glassPanel(TileShape, fancy).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val art = np.art
                if (art != null) {
                    val image = remember(art) { art.asImageBitmap() }
                    Image(image, null, Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.home_np_title), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1,
                    )
                    Text(
                        np.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (np.subtitle.isNotBlank()) {
                        Text(
                            np.subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (np.playing) Box(Modifier.size(8.dp).clip(CircleShape).background(ActiveDot))
            }
        }
        ActionButton(stringResource(if (np.playing) R.string.home_np_pause else R.string.home_np_play), fancy, onClick = onPlayPause)
        if (np.canNext) ActionButton(stringResource(R.string.home_np_next), fancy, onClick = onNext)
    }
}

@Composable
private fun AttentionRow(attention: Attention, fancy: Boolean, onAction: () -> Unit) {
    val (title, body, action) = when (attention) {
        Attention.LOCAL_NETWORK -> Triple(R.string.tv_attn_permission, R.string.local_network_desc, R.string.btn_allow)
        Attention.AIRPLAY_ERROR -> Triple(R.string.tv_attn_airplay_error, R.string.tv_attn_airplay_error_body, R.string.tv_retry)
        Attention.AIRPLAY_OFF -> Triple(R.string.tv_attn_airplay_off, R.string.tv_attn_airplay_off_body, R.string.btn_start)
        Attention.NETWORK -> Triple(R.string.tv_attn_network, R.string.tv_attn_network_body, R.string.tv_attn_network_action)
        Attention.REMOTE_ERROR ->
            Triple(R.string.tv_attn_remote_error, R.string.tv_attn_remote_error_body, R.string.tv_remote_guide_settings)
    }
    Row(
        Modifier.fillMaxWidth()
            .glassPanel(TileShape, fancy).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                stringResource(body), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, lineHeight = 18.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        ActionButton(stringResource(action), fancy, onClick = onAction)
    }
}

@Composable
private fun Pill(
    icon: ImageVector, label: String, fancy: Boolean, onFocused: () -> Unit,
    onLongClick: (() -> Unit)? = null, iconOnly: Boolean = false, onClick: () -> Unit,
) {
    FocusTile(fancy, Modifier.height(36.dp), false, onFocused, onClick, onLongClick, grow = 1.08f, shadow = false) {
        Row(
            Modifier.glassPanel(RoundedCornerShape(18.dp), fancy).padding(horizontal = if (iconOnly) 9.dp else 14.dp).height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, if (iconOnly) label else null, Modifier.size(18.dp), tint = Color.White)
            if (!iconOnly) Text(label, color = Color.White, fontSize = TvType.Caption, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
private fun ActionButton(
    label: String, fancy: Boolean, modifier: Modifier = Modifier, wide: Boolean = false, onClick: () -> Unit,
) {
    FocusTile(fancy, modifier, false, {}, onClick, null, grow = 1.04f, shadow = false) {
        Box(
            (if (wide) Modifier.fillMaxWidth() else Modifier).glassPanel(TileShape, fancy).padding(horizontal = 16.dp).height(40.dp),
            contentAlignment = Alignment.CenterStart,
        ) { Text(label, color = Color.White, fontSize = TvType.Body, fontWeight = FontWeight.Medium, maxLines = 1) }
    }
}

/**
 * Focus effect: scale, a small tilt and a light sweep. All of it runs in graphicsLayer and
 * drawWithContent, so a focus change recomposes nothing (FR-018). Plain frame when not [fancy].
 */
@Composable
private fun FocusTile(
    fancy: Boolean,
    modifier: Modifier,
    selected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    grow: Float = 1.14f,
    // A shadow shows through translucent glass as a dark band, so glass tiles go without it.
    shadow: Boolean = true,
    glow: Color = Color.Black,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    // The light sweep passes once on each focus. It is read only in the draw phase (FR-018).
    val sweep = remember { Animatable(1f) }
    LaunchedEffect(focused, fancy) {
        if (focused && fancy) { sweep.snapTo(0f); sweep.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    }
    var longDone by remember { mutableStateOf(false) }
    // A key up counts only after a key down on this tile. After a long OK opens a menu, the key up
    // goes to the first menu item, and it must not press that item.
    var downSeen by remember { mutableStateOf(false) }
    val t by animateFloatAsState(if (focused) 1f else 0f, tween(if (fancy) 160 else 60), label = "focus")
    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                if (fancy) {
                    val s = 1f + (grow - 1f) * t
                    scaleX = s; scaleY = s
                    rotationX = -3f * t
                    cameraDistance = 16f * density
                    shadowElevation = if (shadow) 24f * t else 0f
                    ambientShadowColor = glow
                    spotShadowColor = glow
                    shape = TileShape
                    clip = false
                }
            }
            .onFocusChanged { if (it.isFocused && !focused) onFocused(); focused = it.isFocused }
            .onKeyEvent { e ->
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (!ok) return@onKeyEvent false
                when (e.type) {
                    KeyEventType.KeyDown -> {
                        if (e.nativeKeyEvent.repeatCount == 0) downSeen = true
                        if (e.nativeKeyEvent.isLongPress && onLongClick != null && !longDone) {
                            longDone = true
                            onLongClick()
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        if (downSeen && !longDone) onClick()
                        downSeen = false
                        longDone = false
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            // touch and mouse: tap and long press
            .pointerInput(onClick, onLongClick) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick?.invoke() })
            }
            .clip(TileShape)
            .drawWithContent {
                drawContent()
                if (t > 0f && fancy) {
                    // Depth without a frame (US8): a soft top highlight and one sweep of light.
                    drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.16f * t), 0.45f to Color.Transparent))
                    val p = sweep.value
                    if (p < 1f) {
                        val x = -size.width + p * size.width * 3f
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.28f * t), Color.Transparent),
                                start = Offset(x, 0f), end = Offset(x + size.width * 0.6f, size.height),
                            ),
                        )
                    }
                    if (selected) {
                        drawRoundRect(
                            Color(0xFF5AC8FA),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx()),
                        )
                    }
                } else if (t > 0f) {
                    // Plain look (effects off or slow frames): the white frame.
                    drawRoundRect(
                        Color.White.copy(alpha = t),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx()),
                    )
                } else if (selected) {
                    drawRoundRect(
                        Color(0xFF5AC8FA),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx()),
                    )
                }
            },
    ) { content() }
}
