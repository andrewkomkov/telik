package io.github.henry753951.airplayremote.launcher.ui

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
import io.github.henry753951.airplayremote.ui.requestFocusUntilLanded
import io.github.henry753951.airplayremote.ui.theme.AirPlayTheme
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
)

private val TileShape = RoundedCornerShape(16.dp)
private val EdgeMargin = 56.dp

@Composable
fun HomeScreen(
    state: HomeState,
    onFirstFrame: () -> Unit,
    onLaunch: (AppTile) -> Unit,
    onTelik: () -> Unit,
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
) = AirPlayTheme {
    // Frame watch (FR-012): slow frames while focus moves switch the effects to plain.
    var slow by remember { mutableStateOf(false) }
    var focusStamp by remember { mutableIntStateOf(0) }
    val fancy = state.glass && !slow

    LaunchedEffect(Unit) { withFrameNanos { }; onFirstFrame() }
    LaunchedEffect(focusStamp) {
        if (focusStamp == 0 || slow) return@LaunchedEffect
        var last = 0L
        var sum = 0L
        var n = 0
        withTimeoutOrNull(300) {
            while (true) {
                withFrameNanos { t ->
                    if (last != 0L && t - last < 100_000_000L) { sum += t - last; n++ }
                    last = t
                }
            }
        }
        if (n >= 4 && sum / n > 33_000_000L) slow = true
    }

    val editing = state.editing
    val rowTiles = if (editing != null) state.editTiles else state.tiles
    val listState = rememberLazyListState()
    val firstTile = remember { FocusRequester() }

    LaunchedEffect(state.homeTick) {
        if (state.homeTick > 0) {
            listState.scrollToItem(0)
            firstTile.requestFocusUntilLanded()
        }
    }
    LaunchedEffect(Unit) { firstTile.requestFocusUntilLanded() }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF1B2433), Color(0xFF0B0D12))),
        ),
    ) {
        // Rows run to the screen edges and keep the margin as content padding, so a cut tile
        // at the edge shows that the row scrolls.
        Column(Modifier.fillMaxSize().padding(vertical = 32.dp)) {
            // shortcuts: Telik, HDMI inputs, search, settings, Home screen menu
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = EdgeMargin),
            ) {
                item { Pill(Icons.Default.Home, stringResource(R.string.home_entry_telik), fancy, { focusStamp++ }, onTelik) }
                items(state.inputs, key = { it.id }) { i ->
                    Pill(Icons.AutoMirrored.Filled.Input, i.label, fancy, { focusStamp++ }) { onInput(i.id) }
                }
                item { Pill(Icons.Default.Search, stringResource(R.string.home_entry_search), fancy, { focusStamp++ }, onSearch) }
                item { Pill(Icons.Default.Settings, stringResource(R.string.home_entry_settings), fancy, { focusStamp++ }, onSettings) }
                item { Pill(Icons.Default.Home, stringResource(R.string.home_entry_home), fancy, { focusStamp++ }) { onMenu(true) } }
            }
            if (!state.isDefault) {
                Text(
                    stringResource(R.string.home_not_default),
                    Modifier.padding(start = EdgeMargin, top = 12.dp),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                )
            }

            Box(Modifier.weight(1f))

            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                contentPadding = PaddingValues(vertical = 40.dp, horizontal = EdgeMargin),
            ) {
                items(rowTiles, key = { it.packageName }) { app ->
                    val isFirst = app === rowTiles.first()
                    AppCard(
                        app = app,
                        dim = editing != null && app.packageName in state.hidden,
                        selected = editing == app.packageName,
                        fancy = fancy,
                        modifier = if (isFirst) Modifier.focusRequester(firstTile) else Modifier,
                        onFocused = { focusStamp++ },
                        onClick = { if (editing != null) onEdit(app.packageName) else onLaunch(app) },
                        onLongClick = { onEdit(app.packageName) },
                    )
                }
            }

            if (editing != null) {
                val isHidden = editing in state.hidden
                Row(
                    Modifier.padding(horizontal = EdgeMargin).fillMaxWidth()
                        .glassPanel(RoundedCornerShape(20.dp), fancy)
                        .horizontalScroll(rememberScrollState()).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ActionButton(stringResource(R.string.home_edit_left), fancy) { onMove(editing, -1) }
                    ActionButton(stringResource(R.string.home_edit_right), fancy) { onMove(editing, 1) }
                    ActionButton(
                        stringResource(if (isHidden) R.string.home_edit_show else R.string.home_edit_hide), fancy,
                    ) { onHide(editing, !isHidden) }
                    ActionButton(stringResource(R.string.home_edit_info), fancy) { onInfo(editing) }
                    ActionButton(stringResource(R.string.home_edit_done), fancy, onClick = onCloseEdit)
                }
            } else {
                Box(Modifier.height(24.dp))
            }
        }

        if (state.menuOpen) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                val first = remember { FocusRequester() }
                LaunchedEffect(Unit) { first.requestFocusUntilLanded() }
                Column(
                    Modifier.glassPanel(RoundedCornerShape(28.dp), fancy).padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (!state.isDefault) {
                        ActionButton(stringResource(R.string.home_make_default), fancy, Modifier.focusRequester(first), onMakeDefault)
                    }
                    ActionButton(
                        stringResource(if (state.glass) R.string.home_glass_on else R.string.home_glass_off),
                        fancy,
                        if (state.isDefault) Modifier.focusRequester(first) else Modifier,
                        onToggleGlass,
                    )
                    ActionButton(stringResource(R.string.home_restore_old), fancy, onClick = onRestoreOld)
                    ActionButton(stringResource(R.string.home_edit_done), fancy) { onMenu(false) }
                }
            }
        }
    }
}

@Composable
private fun AppCard(
    app: AppTile,
    dim: Boolean,
    selected: Boolean,
    fancy: Boolean,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val art by rememberArt(app.packageName, app.activity)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FocusTile(
            fancy = fancy,
            modifier = Modifier.size(264.dp, 148.dp),
            selected = selected,
            onFocused = onFocused,
            onClick = onClick,
            onLongClick = onLongClick,
        ) {
            val a = art
            Box(
                Modifier.fillMaxSize().background(tileColor(app.packageName)).graphicsLayer { alpha = if (dim) 0.35f else 1f },
                contentAlignment = Alignment.Center,
            ) {
                if (a != null) {
                    Image(
                        a.image, app.label,
                        Modifier.fillMaxSize().let { if (a.isBanner) it else it.padding(24.dp) },
                        contentScale = if (a.isBanner) ContentScale.Crop else ContentScale.Fit,
                    )
                }
            }
        }
        Text(
            app.label, Modifier.padding(top = 18.dp).size(264.dp, 28.dp),
            color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun Pill(icon: ImageVector, label: String, fancy: Boolean, onFocused: () -> Unit, onClick: () -> Unit) {
    FocusTile(fancy, Modifier.height(56.dp), false, onFocused, onClick, null, grow = 1.08f, shadow = false) {
        Row(
            Modifier.glassPanel(TileShape, fancy).padding(horizontal = 20.dp).height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, null, tint = Color.White)
            Text(label, color = Color.White, fontSize = 18.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ActionButton(label: String, fancy: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusTile(fancy, modifier, false, {}, onClick, null, grow = 1.06f, shadow = false) {
        Box(
            Modifier.glassPanel(TileShape, fancy).padding(horizontal = 18.dp).height(52.dp),
            contentAlignment = Alignment.Center,
        ) { Text(label, color = Color.White, fontSize = 18.sp, maxLines = 1) }
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
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var longDone by remember { mutableStateOf(false) }
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
                        if (e.nativeKeyEvent.isLongPress && onLongClick != null && !longDone) {
                            longDone = true
                            onLongClick()
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        if (!longDone) onClick()
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
                if (t > 0f) {
                    if (fancy) {
                        // light sweep across the face
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.22f * t), Color.Transparent),
                                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                            ),
                        )
                    }
                    // plain frame, also the fallback look
                    drawRoundRect(
                        Color.White.copy(alpha = t),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = (if (fancy) 2.dp else 4.dp).toPx()),
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

/** Stable color per package for apps without a banner. */
private fun tileColor(pkg: String): Color {
    val h = (pkg.hashCode().toLong() and 0xFFFFFFFFL) % 360
    return Color.hsv(h.toFloat(), 0.45f, 0.35f)
}
