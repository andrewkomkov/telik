package io.github.henry753951.airplayremote.ui

import io.github.henry753951.airplayremote.headphones.HeadphonesController
import io.github.henry753951.airplayremote.headphones.HeadphonesStatus
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.henry753951.airplayremote.remote.input.RemoteAccessibilityService
import io.github.henry753951.airplayremote.remote.input.LocalAdbDriver
import io.github.henry753951.airplayremote.remote.RemoteState
import io.github.henry753951.airplayremote.Prefs
import io.github.henry753951.airplayremote.R
import io.github.henry753951.airplayremote.runtime.AirPlayService.ServerState
import io.github.henry753951.airplayremote.ui.viewmodel.LocalNetworkAccess
import io.github.henry753951.airplayremote.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas

// TV receiver look: near-black greys, white type, one white highlight for focus
internal val TvBg = Color(0xFF0E0E10)
internal val TvText = Color(0xFFF5F5F7)
internal val TvMuted = Color(0xFF9C9CA3)
internal val TvTile = Color(0xFF1D1D20)
internal val TvInk = Color(0xFF111113)
private val TvError = Color(0xFFFF7A6E)
private val TvBackdrop = Brush.verticalGradient(listOf(Color(0xFF1B1B1E), TvBg))

// short and quick: motion confirms a key press, it never stands in for one
internal const val FOCUS_MS = 110

// one page frame for home, guides and settings, so nothing jumps sideways between levels
private val TV_EDGE_START = 80.dp
private val TV_EDGE_END = 64.dp
private const val TV_ART_WEIGHT = 0.44f
private val TV_ART_SIZE = 340.dp

// every page knows where Back goes; parents are declared before children
internal enum class TvPage(val parent: TvPage?) {
    HOME(null),
    CAST_GUIDE(HOME), REMOTE_GUIDE(HOME), SPEAKERS(HOME), HEADPHONES(HOME),
    SETTINGS(HOME),
    GENERAL(SETTINGS), VIDEO(SETTINGS), AUDIO(SETTINGS), REMOTE(SETTINGS), POWER(SETTINGS), DIAGNOSTICS(SETTINGS),
    GRAYSCALE(VIDEO),
    LOGS(DIAGNOSTICS), ADVANCED(DIAGNOSTICS)
}

@Composable
internal fun TvRouter(
    viewModel: MainViewModel,
    page: TvPage,
    onPage: (TvPage) -> Unit,
    idle: Boolean,
    covered: Boolean,
    onLocalNetwork: () -> Unit,
    overview: @Composable (onSettings: () -> Unit, focusSettings: Boolean) -> Unit
) {
    // focused row per page, so Back lands where the viewer left
    val focusMemory = remember { mutableStateMapOf<TvPage, Int>() }
    // pages reached from elsewhere (remote settings from the remote guide) go back the way they came
    val trail = remember { mutableStateListOf<TvPage>() }
    var cameFrom by remember { mutableStateOf<TvPage?>(null) }
    // deeper pages arrive from the right, Back returns from the left
    var forward by remember { mutableStateOf(true) }
    val go: (TvPage) -> Unit = {
        trail.add(page)
        cameFrom = page
        forward = true
        onPage(it)
    }
    val back: () -> Unit = {
        val to = trail.removeLastOrNull() ?: page.parent
        if (to != null) {
            cameFrom = page
            forward = false
            onPage(to)
        }
    }
    val memo = { p: TvPage -> focusMemory[p] ?: 0 }
    val saveFocus = { p: TvPage -> { i: Int -> focusMemory[p] = i } }

    // a short slide and fade between levels: enough to show where the viewer went, never long
    // enough to hold up the next key press
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val dir = if (forward) 1 else -1
            (fadeIn(tween(220, delayMillis = 70)) +
                slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { w -> dir * w / 14 })
                .togetherWith(
                    fadeOut(tween(150)) +
                        slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { w -> -dir * w / 24 }
                )
        },
        label = "page"
    ) { target ->
        // Exiting pages remain composed for the animation but must not accept focus or keys.
        Box(Modifier.fillMaxSize().focusProperties {
            onEnter = { if (target != page) cancelFocusChange() }
        }.focusGroup().onPreviewKeyEvent { target != page }) {
            when (target) {
                TvPage.HOME -> if (idle) {
                    // home is the root: nothing to go back to past here
                    LaunchedEffect(Unit) { trail.clear() }
                    TvIdleScreen(
                        viewModel, onLocalNetwork, go,
                        focusIndex = focusMemory[TvPage.HOME], onFocusIndex = saveFocus(TvPage.HOME),
                        covered = covered
                    )
                } else {
                    BackHandler { viewModel.disconnectSession() }
                    overview({ go(TvPage.SETTINGS) }, cameFrom == TvPage.SETTINGS)
                }
                TvPage.CAST_GUIDE -> TvCastGuide(viewModel, back, go)
                TvPage.REMOTE_GUIDE -> TvRemoteGuide(viewModel, back, go)
                TvPage.SPEAKERS -> TvSubPage(stringResource(R.string.speaker_title), back) { HomePodScreen(onLocalNetwork) }
                TvPage.SETTINGS -> TvRootSettings(viewModel, memo(target), saveFocus(target), back, go)
                TvPage.GENERAL, TvPage.VIDEO, TvPage.AUDIO, TvPage.REMOTE, TvPage.POWER, TvPage.DIAGNOSTICS, TvPage.HEADPHONES ->
                    TvCategory(viewModel, target, memo(target), saveFocus(target), back, go)
                TvPage.GRAYSCALE -> TvGrayscaleScreen(back)
                TvPage.ADVANCED -> TvSubPage(stringResource(R.string.tv_advanced), back) { SettingsScreen(viewModel) }
                TvPage.LOGS -> TvSubPage(stringResource(R.string.tab_logs), back) { LogsScreen(viewModel) }
            }
        }
    }
}

@Composable
internal fun AirPlayMark(height: Dp, tint: Color) {
    // drawable is 96x80
    Icon(painterResource(R.drawable.airplay_mark), null, Modifier.size(height * 1.2f, height), tint = tint)
}

@Composable
private fun TvPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (focused) TvText else Color(0x1FFFFFFF), tween(FOCUS_MS), label = "pillBg")
    val fg by animateColorAsState(if (focused) TvInk else TvText, tween(FOCUS_MS), label = "pillFg")
    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(bg)
            .clickable(
                remember { MutableInteractionSource() }, indication = null, enabled = enabled,
                role = Role.Button, onClick = onClick
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 30.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = fg.copy(alpha = if (enabled) 1f else 0.4f),
            fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1
        )
    }
}

// ---- service status ----

private enum class TvHealth { READY, BUSY, OFF, ERROR }

private class TvStatus(val health: TvHealth, @StringRes val label: Int)

@Composable
private fun remoteStatus(viewModel: MainViewModel): TvStatus {
    val enabled by viewModel.pref(Prefs.REMOTE_ENABLED).collectAsState()
    val status by RemoteState.status.collectAsState()
    val connected by RemoteState.connected.collectAsState()
    return when {
        !enabled -> TvStatus(TvHealth.OFF, R.string.tv_state_off)
        // connected tracks a live remote; paired is only the one-off pairing signal
        status == "running" && connected -> TvStatus(TvHealth.READY, R.string.tv_state_connected)
        status == "running" -> TvStatus(TvHealth.READY, R.string.tv_state_ready)
        status == "starting" -> TvStatus(TvHealth.BUSY, R.string.tv_state_starting)
        status == "waiting" -> TvStatus(TvHealth.BUSY, R.string.tv_state_waiting_network)
        status == "error" -> TvStatus(TvHealth.ERROR, R.string.tv_state_error)
        else -> TvStatus(TvHealth.OFF, R.string.tv_state_stopped)
    }
}

// Home stays silent while things work; only a problem the viewer can act on earns a line on screen.
private class TvAttention(@StringRes val title: Int, @StringRes val body: Int, @StringRes val action: Int, val onAction: () -> Unit)

@Composable
private fun homeAttention(viewModel: MainViewModel, onLocalNetwork: () -> Unit, go: (TvPage) -> Unit): TvAttention? {
    val state by viewModel.serverState.collectAsState()
    val localNetwork by viewModel.localNetwork.collectAsState()
    val remoteEnabled by viewModel.pref(Prefs.REMOTE_ENABLED).collectAsState()
    val remote by RemoteState.status.collectAsState()
    val ctx = LocalContext.current
    return when {
        state != ServerState.RUNNING && localNetwork != LocalNetworkAccess.GRANTED ->
            TvAttention(R.string.tv_attn_permission, R.string.local_network_desc, R.string.btn_allow, onLocalNetwork)
        state == ServerState.ERROR ->
            TvAttention(R.string.tv_attn_airplay_error, R.string.tv_attn_airplay_error_body, R.string.tv_retry, viewModel::startServer)
        state == ServerState.STOPPED ->
            TvAttention(R.string.tv_attn_airplay_off, R.string.tv_attn_airplay_off_body, R.string.btn_start, viewModel::startServer)
        // the remote is the only part that reports the network; without one AirPlay can't be found either
        remoteEnabled && remote == "waiting" ->
            TvAttention(R.string.tv_attn_network, R.string.tv_attn_network_body, R.string.tv_attn_network_action) {
                openSystemSettings(ctx, Settings.ACTION_WIFI_SETTINGS)
            }
        remoteEnabled && remote == "error" ->
            TvAttention(R.string.tv_attn_remote_error, R.string.tv_attn_remote_error_body, R.string.tv_remote_guide_settings) {
                go(TvPage.REMOTE)
            }
        else -> null
    }
}

private fun openSystemSettings(ctx: android.content.Context, action: String) {
    val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ctx.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
        }
    }
}

// ---- home ----

private enum class TvHomeEntry { CAST, REMOTE, HEADPHONES, HOMEKIT, SPEAKERS }

private const val ATTENTION_DELAY_MS = 1_500L

@Composable
internal fun TvIdleScreen(
    viewModel: MainViewModel,
    onLocalNetwork: () -> Unit,
    go: (TvPage) -> Unit,
    focusIndex: Int?,
    onFocusIndex: (Int) -> Unit,
    covered: Boolean
) {
    val state by viewModel.serverState.collectAsState()
    val connections by viewModel.connectionCount.collectAsState()
    val serverName by viewModel.pref(Prefs.SERVER_NAME).collectAsState()
    val remoteEnabled by viewModel.pref(Prefs.REMOTE_ENABLED).collectAsState()
    val activity = LocalContext.current as? Activity
    // states settle for a moment after launch (the service binds, the network comes up); only a
    // problem that lasts gets shown, and it leaves as soon as it is fixed
    val pending = homeAttention(viewModel, onLocalNetwork, go)
    var settled by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(pending?.title) {
        settled = null
        if (pending != null) {
            delay(ATTENTION_DELAY_MS)
            settled = pending.title
        }
    }
    val attention = pending?.takeIf { it.title == settled }

    // End any pending cast before returning to the TV home; discovery stays available.
    BackHandler {
        viewModel.disconnectSession()
        activity?.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    val entries = TvHomeEntry.entries
    val homeContext = LocalContext.current
    val requesters = remember { List(entries.size + 1) { FocusRequester() } }
    val settingsFocus = requesters.last()
    var settingsFocused by remember { mutableStateOf(false) }
    val actionFocus = remember { FocusRequester() }
    var actionFocused by remember { mutableStateOf(false) }
    // once the problem is fixed its card leaves; the focus must not leave with it
    LaunchedEffect(attention == null) {
        if (attention == null && actionFocused) {
            actionFocused = false
            requesters[0].requestFocusUntilLanded()
        }
    }
    // The gear remembers which primary entry led to it (indices 3–5).
    var focused by remember { mutableStateOf(entries[(focusIndex ?: 0).coerceAtLeast(0) % entries.size]) }
    // coming back lands where the viewer left; a fresh visit starts on the first entry
    val firstVisit = remember { focusIndex == null }
    var moves by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { requesters[(focusIndex ?: 0).coerceIn(0, requesters.lastIndex)].requestFocusUntilLanded() }
    // a problem that turns up on a fresh visit, before the viewer has moved, takes the focus to its fix
    LaunchedEffect(attention != null) {
        if (attention != null && firstVisit && moves <= 1) actionFocus.requestFocusUntilLanded()
    }

    // the entrance plays once, after the cast stage has lifted rather than hidden underneath it;
    // coming back from a page inside the app just shows home
    val reveal = remember { Animatable(if (focusIndex == null) 0f else 1f) }
    LaunchedEffect(covered) {
        if (!covered) reveal.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }

    Row(
        Modifier
            .fillMaxSize()
            .telikBackdrop()
            .padding(start = TV_EDGE_START, end = TV_EDGE_END)
            .graphicsLayer { alpha = reveal.value }
    ) {
        Column(
            Modifier
                .weight(1f - TV_ART_WEIGHT)
                .fillMaxHeight()
                .padding(top = 40.dp, bottom = 32.dp, end = 40.dp)
                .graphicsLayer { translationY = (1f - reveal.value) * 12.dp.toPx() }
        ) {
            Text(
                stringResource(R.string.tv_home_title),
                color = TvText, fontSize = 52.sp, lineHeight = 60.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1).sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
            // the name sits in the sentence the viewer needs it for, not as a headline
            val waiting = state == ServerState.RUNNING && connections > 0
            Text(
                if (waiting) {
                    androidx.compose.ui.text.AnnotatedString(stringResource(R.string.waiting_for_playback))
                } else {
                    tvChooseName(stringResource(R.string.tv_home_hint, serverName), serverName)
                },
                color = TvMuted, fontSize = 16.sp, lineHeight = 24.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                entries.forEachIndexed { i, entry ->
                    val labels = when (entry) {
                        TvHomeEntry.CAST -> R.string.tv_home_cast to R.string.tv_home_cast_sub
                        TvHomeEntry.REMOTE -> R.string.tv_home_remote to
                            if (remoteEnabled) R.string.tv_home_remote_sub else R.string.tv_home_remote_off
                        TvHomeEntry.HEADPHONES -> R.string.tv_headphones_title to R.string.tv_headphones_idle
                        TvHomeEntry.HOMEKIT -> R.string.tv_home_homekit to R.string.tv_home_homekit_sub
                        TvHomeEntry.SPEAKERS -> R.string.speaker_title to R.string.speaker_home_sub
                    }
                    TvHomeRow(
                        entry = entry,
                        title = stringResource(labels.first),
                        subtitle = if (entry == TvHomeEntry.HEADPHONES) headphonesValue() else stringResource(labels.second),
                        modifier = Modifier.weight(1f).focusRequester(requesters[i]).focusProperties {
                            up = if (i == 0) settingsFocus else requesters[i - 1]
                            down = if (i == entries.lastIndex) FocusRequester.Cancel else requesters[i + 1]
                            right = if (attention != null) actionFocus else settingsFocus
                        },
                        onFocused = {
                            focused = entry
                            moves++
                            onFocusIndex(i)
                        },
                        onClick = {
                            when (entry) {
                                TvHomeEntry.CAST -> go(TvPage.CAST_GUIDE)
                                TvHomeEntry.REMOTE -> go(TvPage.REMOTE_GUIDE)
                                TvHomeEntry.HEADPHONES -> go(TvPage.HEADPHONES)
                                TvHomeEntry.HOMEKIT -> homeContext.startActivity(
                                    Intent(homeContext, dev.tvhomekit.HomeKitActivity::class.java)
                                )
                                TvHomeEntry.SPEAKERS -> go(TvPage.SPEAKERS)
                            }
                        }
                    )
                }
            }
        }
        // the right half answers "what is this?" for the focused entry, with a picture, not words
        Box(Modifier.weight(TV_ART_WEIGHT).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Row(
                Modifier.align(Alignment.TopEnd).padding(top = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                TvMinuteClock()
                Box(
                    Modifier.size(48.dp).focusRequester(settingsFocus).focusProperties {
                        left = requesters[focused.ordinal]
                        down = if (attention != null) actionFocus else requesters[focused.ordinal]
                    }.onFocusChanged {
                        settingsFocused = it.isFocused
                        if (it.isFocused) onFocusIndex(entries.size + focused.ordinal)
                    }.clip(CircleShape)
                        .background(if (settingsFocused) TvText else Color(0x1FFFFFFF))
                        .clickable(role = Role.Button) { go(TvPage.SETTINGS) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Settings, stringResource(R.string.tv_settings), Modifier.size(24.dp),
                        tint = if (settingsFocused) TvInk else TvText)
                }
            }
            if (attention != null) {
                Box(Modifier.fillMaxSize().padding(top = 96.dp, bottom = 32.dp)
                    .verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                    TvAttentionCard(attention, Modifier.focusRequester(actionFocus)
                        .onFocusChanged { actionFocused = it.isFocused }
                        .focusProperties { left = requesters[focused.ordinal]; up = settingsFocus })
                }
            } else {
                AnimatedContent(
                    targetState = focused,
                    modifier = Modifier.padding(top = 56.dp).size(TV_ART_SIZE),
                    contentAlignment = Alignment.Center,
                    transitionSpec = {
                        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (fadeIn(tween(240, delayMillis = 60)) +
                            scaleIn(tween(340, easing = FastOutSlowInEasing), initialScale = 0.94f) +
                            slideInVertically(tween(340, easing = FastOutSlowInEasing)) { h -> dir * h / 12 })
                            .togetherWith(
                                fadeOut(tween(140)) +
                                    slideOutVertically(tween(240, easing = FastOutSlowInEasing)) { h -> -dir * h / 16 }
                            )
                            .using(SizeTransform(clip = false))
                    },
                    label = "art"
                ) { entry ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        TelikMascot(entry)
                    }
                }
            }
        }
    }
}

// brightens just the TV name inside the sentence, so it reads as the thing to pick
private fun tvChooseName(sentence: String, name: String) = androidx.compose.ui.text.buildAnnotatedString {
    append(sentence)
    val at = sentence.indexOf(name)
    if (at >= 0 && name.isNotEmpty()) {
        addStyle(androidx.compose.ui.text.SpanStyle(color = TvText), at, at + name.length)
    }
}

// a quiet clock in the corner; it wakes once a minute, on the minute, and follows the system 12/24-hour setting
@Composable
private fun TvMinuteClock(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val format = remember(ctx) { android.text.format.DateFormat.getTimeFormat(ctx) }
    var now by remember { mutableStateOf(java.util.Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            val ms = System.currentTimeMillis()
            delay(60_000L - ms % 60_000L + 50L)
            now = java.util.Date()
        }
    }
    Text(format.format(now), color = TvMuted, fontSize = 17.sp, maxLines = 1, modifier = modifier)
}

private fun Modifier.telikBackdrop(): Modifier = drawBehind {
    drawRect(Color(0xFF07070C))
    drawRect(
        androidx.compose.ui.graphics.Brush.radialGradient(
            listOf(Color(0x664A3AFF), Color.Transparent),
            center = Offset(size.width * 0.12f, size.height * 0.05f),
            radius = size.width * 0.55f,
        ),
    )
    drawRect(
        androidx.compose.ui.graphics.Brush.radialGradient(
            listOf(Color(0x55FF5E8A), Color.Transparent),
            center = Offset(size.width * 0.95f, size.height * 0.2f),
            radius = size.width * 0.5f,
        ),
    )
    drawRect(
        androidx.compose.ui.graphics.Brush.radialGradient(
            listOf(Color(0x44FFAA4D), Color.Transparent),
            center = Offset(size.width * 0.7f, size.height * 1.05f),
            radius = size.width * 0.55f,
        ),
    )
}

private fun TvHomeEntry.accent(): List<Color> = when (this) {
    TvHomeEntry.CAST -> listOf(Color(0xFF4FC3FF), Color(0xFF3A6BFF))
    TvHomeEntry.REMOTE -> listOf(Color(0xFFB98CFF), Color(0xFF6A4DFF))
    TvHomeEntry.HEADPHONES -> listOf(Color(0xFFFF8FD0), Color(0xFFB04DFF))
    TvHomeEntry.HOMEKIT -> listOf(Color(0xFFFFB35C), Color(0xFFFF5E8A))
    TvHomeEntry.SPEAKERS -> listOf(Color(0xFF5CE6B8), Color(0xFF12A58F))
}

@Composable
private fun TelikMascot(entry: TvHomeEntry) {
    val accent = entry.accent().first()
    Box(Modifier.size(TV_ART_SIZE), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.42f), Color.Transparent),
                    center = center,
                    radius = size.minDimension * 0.62f,
                ),
                radius = size.minDimension * 0.62f,
            )
        }
        Icon(
            androidx.compose.ui.res.painterResource(R.drawable.mascot),
            null,
            Modifier.size(330.dp),
            tint = Color.Unspecified,
        )
    }
}

@Composable
private fun TelikRowIcon(entry: TvHomeEntry) {
    Box(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(entry.accent())),
        contentAlignment = Alignment.Center,
    ) {
        when (entry) {
            TvHomeEntry.CAST -> AirPlayMark(28.dp, Color.White)
            TvHomeEntry.REMOTE -> Canvas(Modifier.size(width = 16.dp, height = 30.dp)) {
                drawRoundRect(Color.White, cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
                drawCircle(entry.accent().last(), radius = 4.5.dp.toPx(), center = Offset(size.width / 2, 8.dp.toPx()))
            }
            TvHomeEntry.HEADPHONES -> Icon(Icons.Default.Headphones, null, Modifier.size(30.dp), tint = Color.White)
            TvHomeEntry.HOMEKIT -> Icon(Icons.Default.Home, null, Modifier.size(30.dp), tint = Color.White)
            TvHomeEntry.SPEAKERS -> Icon(Icons.Default.Speaker, null, Modifier.size(30.dp), tint = Color.White)
        }
    }
}

@Composable
private fun TvHomeRow(
    entry: TvHomeEntry,
    title: String,
    subtitle: String,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.035f else 1f, tween(FOCUS_MS), label = "homeScale")
    val glass by animateColorAsState(if (focused) Color(0xF2FFFFFF) else Color(0x1AFFFFFF), tween(FOCUS_MS), label = "homeBg")
    val fg by animateColorAsState(if (focused) Color(0xFF0B0B10) else TvText, tween(FOCUS_MS), label = "homeFg")
    val sub by animateColorAsState(if (focused) Color(0x990B0B10) else TvMuted, tween(FOCUS_MS), label = "homeSub")
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clip(shape)
            .background(glass)
            .border(
                1.dp,
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color(0x66FFFFFF), Color(0x0DFFFFFF)),
                ),
                shape,
            )
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TelikRowIcon(entry)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = fg, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = sub, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TvAttentionCard(attention: TvAttention, actionModifier: Modifier) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, TvError.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .background(TvError.copy(alpha = 0.08f))
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(TvError))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(attention.title), color = TvText, fontSize = 19.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(attention.body), color = TvMuted, fontSize = 16.sp, lineHeight = 23.sp)
        Spacer(Modifier.height(14.dp))
        TvPill(stringResource(attention.action), attention.onAction, actionModifier)
    }
}

// ---- home artwork: drawn, static, no animation of its own ----

@Composable
private fun TvCastArt() {
    Box(Modifier.size(TV_ART_SIZE), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val w = 1.5.dp.toPx()
            listOf(78.dp to 0.16f, 122.dp to 0.09f, 166.dp to 0.05f).forEach { ring ->
                drawCircle(TvText.copy(alpha = ring.second), radius = ring.first.toPx(), center = c, style = Stroke(width = w))
            }
        }
        AirPlayMark(72.dp, TvText)
    }
}

@Composable
private fun TvRemoteArt(enabled: Boolean) {
    val tint = TvText.copy(alpha = if (enabled) 0.85f else 0.35f)
    Canvas(Modifier.size(width = 134.dp, height = 320.dp)) {
        val w = 2.dp.toPx()
        val body = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
        drawRoundRect(
            tint, topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2), size = body,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width / 2.6f), style = Stroke(width = w)
        )
        val cx = size.width / 2
        // touch surface, then two rows of buttons, like the remote on the viewer's phone
        drawCircle(tint, radius = size.width * 0.32f, center = androidx.compose.ui.geometry.Offset(cx, size.height * 0.24f), style = Stroke(width = w))
        drawCircle(tint.copy(alpha = tint.alpha * 0.6f), radius = size.width * 0.07f, center = androidx.compose.ui.geometry.Offset(cx, size.height * 0.24f))
        val r = size.width * 0.11f
        listOf(0.52f, 0.66f).forEach { y ->
            listOf(0.32f, 0.68f).forEach { x ->
                drawCircle(tint, radius = r, center = androidx.compose.ui.geometry.Offset(size.width * x, size.height * y), style = Stroke(width = w))
            }
        }
    }
}

// ---- guides: how to start from the phone, since casting always begins there ----

@Composable
private fun TvGuidePage(
    title: String,
    lead: String,
    steps: List<String>,
    note: String?,
    actions: List<Pair<String, () -> Unit>>,
    onBack: () -> Unit,
    art: @Composable () -> Unit
) {
    BackHandler(onBack = onBack)
    val first = remember { FocusRequester() }
    var reading by remember { mutableStateOf(false) }
    // the fine print lives one level down, so the page itself always fits the screen
    val allActions = if (note != null) actions + (stringResource(R.string.tv_more_info) to { reading = true }) else actions
    // keyed on the count: turning the remote on removes the focused button, so land on the new first one
    LaunchedEffect(allActions.size) { if (allActions.isNotEmpty()) first.requestFocusUntilLanded() }
    Row(Modifier.fillMaxSize().background(TvBackdrop).padding(start = TV_EDGE_START, end = TV_EDGE_END)) {
        Column(
            Modifier
                .weight(1f - TV_ART_WEIGHT)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(top = 48.dp, bottom = 32.dp, end = 40.dp)
        ) {
            Text(title, color = TvText, fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(12.dp))
            Text(lead, color = TvMuted, fontSize = 18.sp, lineHeight = 27.sp)
            Spacer(Modifier.height(20.dp))
            steps.forEachIndexed { i, step ->
                Row(Modifier.padding(bottom = 12.dp)) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).border(1.5.dp, TvText.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${i + 1}", color = TvText, fontSize = 15.sp)
                    }
                    Spacer(Modifier.width(18.dp))
                    Text(step, color = TvText, fontSize = 18.sp, lineHeight = 26.sp, modifier = Modifier.padding(top = 1.dp))
                }
            }
            if (allActions.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    allActions.forEachIndexed { i, action ->
                        TvPill(action.first, action.second, if (i == 0) Modifier.focusRequester(first) else Modifier)
                    }
                }
            }
        }
        Box(Modifier.weight(TV_ART_WEIGHT).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(TV_ART_SIZE), contentAlignment = Alignment.Center) { art() }
        }
    }
    if (reading && note != null) TvInfoDialog(title, note) { reading = false }
}

@Composable
private fun TvCastGuide(viewModel: MainViewModel, onBack: () -> Unit, go: (TvPage) -> Unit) {
    val serverName by viewModel.pref(Prefs.SERVER_NAME).collectAsState()
    TvGuidePage(
        title = stringResource(R.string.tv_cast_guide_title),
        lead = stringResource(R.string.tv_cast_guide_lead),
        steps = listOf(
            stringResource(R.string.tv_cast_step_open),
            stringResource(R.string.tv_cast_step_mirror),
            stringResource(R.string.tv_cast_step_pick, serverName),
        ),
        note = stringResource(R.string.tv_cast_note_mac),
        actions = listOf(stringResource(R.string.tv_cast_guide_rename) to { go(TvPage.GENERAL) }),
        onBack = onBack,
        art = { TvCastArt() }
    )
}

@Composable
private fun TvRemoteGuide(viewModel: MainViewModel, onBack: () -> Unit, go: (TvPage) -> Unit) {
    val serverName by viewModel.pref(Prefs.SERVER_NAME).collectAsState()
    val enabled by viewModel.pref(Prefs.REMOTE_ENABLED).collectAsState()
    val turnOn = stringResource(R.string.tv_remote_turn_on)
    val settings = stringResource(R.string.tv_remote_guide_settings)
    TvGuidePage(
        title = stringResource(R.string.tv_remote_guide_title),
        lead = stringResource(if (enabled) R.string.tv_remote_guide_lead else R.string.tv_remote_guide_off),
        steps = listOf(
            stringResource(R.string.tv_remote_step_open),
            stringResource(R.string.tv_remote_step_pick, serverName),
            stringResource(R.string.tv_remote_step_code),
        ),
        note = stringResource(R.string.tv_remote_guide_note),
        actions = if (enabled) {
            listOf(settings to { go(TvPage.REMOTE) })
        } else {
            listOf(turnOn to { viewModel.set(Prefs.REMOTE_ENABLED, true) }, settings to { go(TvPage.REMOTE) })
        },
        onBack = onBack,
        art = { TvRemoteArt(enabled) }
    )
}

// ---- settings building blocks ----

internal class TvItem(
    val title: String,
    val description: String,
    val checked: Boolean? = null,
    val value: String? = null,
    val link: Boolean = false,
    // a list of (key, label); selecting the row opens a chooser
    val choices: List<Pair<String, String>>? = null,
    val selected: String? = null,
    val onPick: ((String) -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
    // longer explanation shown one level down (in the chooser, or on OK for an info row)
    val detail: String? = null
)

@Composable
private fun TvSettingsList(
    title: String,
    crumb: String?,
    items: List<TvItem>,
    focusIndex: Int,
    onFocusIndex: (Int) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var choosing by remember { mutableStateOf<TvItem?>(null) }
    var reading by remember { mutableStateOf<TvItem?>(null) }
    val requesters = remember(items.size) { List(items.size) { FocusRequester() } }
    val current = focusIndex.coerceIn(0, items.lastIndex)
    LaunchedEffect(Unit) { requesters[current].requestFocusUntilLanded() }

    Row(Modifier.fillMaxSize().background(TvBackdrop).padding(start = TV_EDGE_START, end = TV_EDGE_END)) {
        Column(Modifier.weight(0.4f).fillMaxHeight().padding(top = 64.dp, bottom = 56.dp, end = 48.dp)) {
            AirPlayMark(28.dp, TvMuted)
            Spacer(Modifier.height(18.dp))
            if (crumb != null) {
                Text(crumb, color = TvMuted, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
            }
            Text(
                title, color = TvText, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Light,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(24.dp))
            // the focused row explains itself here, so rows never change height; the area is bounded,
            // and anything longer belongs in the row's detail one level down
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
                Crossfade(targetState = current, animationSpec = tween(140), label = "desc") { i ->
                    Text(
                        items[i].description, color = TvMuted, fontSize = 17.sp, lineHeight = 26.sp,
                        maxLines = 8, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Column(
            Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items.forEachIndexed { i, item ->
                val click = when {
                    item.choices != null -> ({ choosing = item })
                    item.onClick == null && item.detail != null -> ({ reading = item })
                    else -> item.onClick
                }
                TvSettingRow(item, click, Modifier.focusRequester(requesters[i])) { onFocusIndex(i) }
            }
        }
    }

    reading?.let { item -> TvInfoDialog(item.title, item.detail.orEmpty()) { reading = null } }
    choosing?.let { item ->
        TvChoiceDialog(
            title = item.title,
            detail = item.detail,
            options = item.choices.orEmpty(),
            selected = item.selected,
            onPick = { key ->
                item.onPick?.invoke(key)
                choosing = null
            },
            onDismiss = { choosing = null }
        )
    }
}

@Composable
private fun TvSettingRow(item: TvItem, onClick: (() -> Unit)?, modifier: Modifier, onFocused: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (focused) TvText else Color.Transparent, tween(FOCUS_MS), label = "rowBg")
    val fg by animateColorAsState(if (focused) TvInk else TvText, tween(FOCUS_MS), label = "rowFg")
    val sub by animateColorAsState(if (focused) TvInk.copy(alpha = 0.6f) else TvMuted, tween(FOCUS_MS), label = "rowSub")
    val base = modifier
        .fillMaxWidth()
        .onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) onFocused()
        }
        .clip(RoundedCornerShape(14.dp))
        .background(bg)
    val interactive = when {
        onClick == null -> base.focusable()
        item.checked != null -> base.clickable(
            remember { MutableInteractionSource() }, indication = null, role = Role.Switch, onClick = onClick
        )
        else -> base.clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
    }
    Row(
        interactive.semantics { contentDescription = item.title }.heightIn(min = 58.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            item.title, color = fg, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        val value = item.value ?: item.choices?.firstOrNull { it.first == item.selected }?.second ?: item.selected
        if (value != null) {
            Spacer(Modifier.width(20.dp))
            Text(
                value, color = sub, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 280.dp)
            )
        }
        when {
            item.checked != null -> {
                Spacer(Modifier.width(20.dp))
                TvSwitch(item.checked, focused)
            }
            item.link || item.choices != null || (item.detail != null && onClick != null) -> {
                Spacer(Modifier.width(14.dp))
                Text("›", color = sub, fontSize = 22.sp)
            }
        }
    }
}

@Composable
private fun TvSwitch(checked: Boolean, onLightRow: Boolean) {
    val knobX by animateDpAsState(if (checked) 20.dp else 2.dp, tween(150), label = "knob")
    val onTrack = if (onLightRow) TvInk else TvText
    val offTrack = if (onLightRow) Color(0x33000000) else Color(0x33FFFFFF)
    val track by animateColorAsState(if (checked) onTrack else offTrack, tween(FOCUS_MS), label = "track")
    val knob by animateColorAsState(
        when {
            checked && onLightRow -> TvText
            checked -> TvInk
            onLightRow -> Color(0xFF8A8A90)
            else -> Color(0xFFB4B4BA)
        }, tween(FOCUS_MS), label = "knobColor"
    )
    Box(Modifier.size(42.dp, 24.dp).clip(CircleShape).background(track)) {
        Box(Modifier.offset(x = knobX, y = 2.dp).size(20.dp).clip(CircleShape).background(knob))
    }
}

@Composable
private fun TvInfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    val ok = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { ok.requestFocusUntilLanded() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(min = 460.dp, max = 620.dp)
                .heightIn(max = 460.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                        Key.DirectionUp, Key.DirectionDown -> {
                            val direction = if (event.key == Key.DirectionDown) 1 else -1
                            scope.launch { scroll.animateScrollBy(direction * scroll.viewportSize * 0.6f) }
                            true
                        }
                        else -> false
                    }
                }
                .clip(RoundedCornerShape(24.dp))
                .background(TvTile)
                .padding(32.dp)
        ) {
            Text(title, color = TvText, fontSize = 24.sp, fontWeight = FontWeight.Light, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(16.dp))
            Text(
                text, color = TvMuted, fontSize = 17.sp, lineHeight = 26.sp,
                modifier = Modifier.weight(1f, fill = false).verticalScroll(scroll)
            )
            if (scroll.maxValue > 0) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.tv_scroll_hint), color = TvMuted, fontSize = 14.sp)
            }
            Spacer(Modifier.height(24.dp))
            TvPill(stringResource(R.string.tv_got_it), onDismiss, Modifier.align(Alignment.End).focusRequester(ok))
        }
    }
}

@Composable
private fun TvChoiceDialog(
    title: String,
    detail: String? = null,
    options: List<Pair<String, String>>,
    selected: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var reading by remember { mutableStateOf(false) }
    val more = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val requesters = remember(options.size) { List(options.size) { FocusRequester() } }
    val start = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    LaunchedEffect(Unit) { if (requesters.isNotEmpty()) requesters[start].requestFocusUntilLanded() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(min = 420.dp, max = 560.dp)
                .heightIn(max = 460.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(TvTile)
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Text(
                title, color = TvText, fontSize = 22.sp, fontWeight = FontWeight.Light,
                modifier = Modifier.padding(start = 24.dp, bottom = 12.dp),
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (detail != null) {
                TvPill(stringResource(R.string.tv_more_info), { reading = true }, Modifier.focusRequester(more))
                Spacer(Modifier.height(12.dp))
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEachIndexed { i, option ->
                    TvSettingRow(
                        TvItem(option.second, "", value = if (option.first == selected) "✓" else null),
                        onClick = { onPick(option.first) },
                        modifier = Modifier.focusRequester(requesters[i]),
                        onFocused = {}
                    )
                }
            }
        }
    }
    if (reading && detail != null) TvInfoDialog(title, detail) {
        reading = false
        scope.launch { more.requestFocusUntilLanded() }
    }
}

// ---- pages ----

@Composable
private fun TvRootSettings(
    viewModel: MainViewModel,
    focusIndex: Int,
    onFocusIndex: (Int) -> Unit,
    onBack: () -> Unit,
    go: (TvPage) -> Unit
) {
    val remote = remoteStatus(viewModel)
    val items = listOf(
        TvItem(stringResource(R.string.tv_cat_general), stringResource(R.string.tv_cat_general_desc), link = true,
            onClick = { go(TvPage.GENERAL) }),
        TvItem(stringResource(R.string.tv_cat_video), stringResource(R.string.tv_cat_video_desc), link = true,
            onClick = { go(TvPage.VIDEO) }),
        TvItem(stringResource(R.string.tv_cat_audio), stringResource(R.string.tv_cat_audio_desc), link = true,
            onClick = { go(TvPage.AUDIO) }),
        TvItem(stringResource(R.string.tv_cat_remote), stringResource(R.string.tv_cat_remote_desc), link = true,
            value = stringResource(remote.label), onClick = { go(TvPage.REMOTE) }),
        TvItem(stringResource(R.string.tv_cat_power), stringResource(R.string.tv_cat_power_desc), link = true,
            onClick = { go(TvPage.POWER) }),
        TvItem(stringResource(R.string.tv_cat_diagnostics), stringResource(R.string.tv_cat_diagnostics_desc), link = true,
            onClick = { go(TvPage.DIAGNOSTICS) }),
    )
    TvSettingsList(stringResource(R.string.tv_settings), null, items, focusIndex, onFocusIndex, onBack)
}

@Composable
private fun TvCategory(
    viewModel: MainViewModel,
    page: TvPage,
    focusIndex: Int,
    onFocusIndex: (Int) -> Unit,
    onBack: () -> Unit,
    go: (TvPage) -> Unit
) {
    var renaming by remember { mutableStateOf(false) }
    val items = when (page) {
        TvPage.GENERAL -> generalItems(viewModel) { renaming = true }
        TvPage.VIDEO -> videoItems(viewModel, go)
        TvPage.AUDIO -> audioItems(viewModel)
        TvPage.HEADPHONES -> headphonesItems(viewModel)
        TvPage.REMOTE -> remoteItems(viewModel)
        TvPage.POWER -> powerItems(viewModel)
        else -> diagnosticsItems(viewModel, go)
    }
    val title = stringResource(
        when (page) {
            TvPage.GENERAL -> R.string.tv_cat_general
            TvPage.VIDEO -> R.string.tv_cat_video
            TvPage.AUDIO -> R.string.tv_cat_audio
            TvPage.HEADPHONES -> R.string.tv_headphones_title
            TvPage.REMOTE -> R.string.tv_cat_remote
            TvPage.POWER -> R.string.tv_cat_power
            else -> R.string.tv_cat_diagnostics
        }
    )
    val parent = stringResource(if (page == TvPage.HEADPHONES) R.string.tv_home_title else R.string.tv_settings)
    TvSettingsList(title, parent, items, focusIndex, onFocusIndex, onBack)

    if (renaming) {
        val serverName by viewModel.pref(Prefs.SERVER_NAME).collectAsState()
        TvRenameDialog(
            initial = serverName,
            onSave = { viewModel.set(Prefs.SERVER_NAME, it); renaming = false },
            onDismiss = { renaming = false }
        )
    }
}

@Composable
private fun toggle(viewModel: MainViewModel, pref: io.github.henry753951.airplayremote.BoolPref, title: Int, desc: Int): TvItem {
    val on by viewModel.pref(pref).collectAsState()
    return TvItem(stringResource(title), stringResource(desc), checked = on, onClick = { viewModel.set(pref, !on) })
}

@Composable
private fun generalItems(viewModel: MainViewModel, onRename: () -> Unit): List<TvItem> {
    val serverName by viewModel.pref(Prefs.SERVER_NAME).collectAsState()
    val launchOnConnect by viewModel.pref(Prefs.LAUNCH_ON_CONNECT).collectAsState()
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasOverlayPermission by remember { mutableStateOf(canAutoLaunch(ctx)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasOverlayPermission = canAutoLaunch(ctx)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val autoLaunch = launchOnConnect && hasOverlayPermission
    return listOf(
        TvItem(stringResource(R.string.tv_name), stringResource(R.string.tv_name_desc), value = serverName, onClick = onRename),
        TvItem(
            stringResource(R.string.setting_launch_on_connect),
            stringResource(
                if (launchOnConnect && !hasOverlayPermission) R.string.setting_launch_on_connect_no_permission
                else R.string.setting_launch_on_connect_desc
            ),
            checked = autoLaunch,
            onClick = {
                viewModel.set(Prefs.LAUNCH_ON_CONNECT, !autoLaunch)
                if (!autoLaunch && !canAutoLaunch(ctx)) openOverlaySettings(ctx)
            }
        ),
        toggle(viewModel, Prefs.ALLOW_NEW_CONN, R.string.setting_allow_new_conn, R.string.setting_allow_new_conn_desc),
    )
}

@Composable
private fun videoItems(viewModel: MainViewModel, go: (TvPage) -> Unit): List<TvItem> {
    val resolution by viewModel.pref(Prefs.RESOLUTION).collectAsState()
    val maxFps by viewModel.pref(Prefs.MAX_FPS).collectAsState()
    val colorRange by viewModel.pref(Prefs.COLOR_RANGE).collectAsState()
    val h265 by viewModel.pref(Prefs.H265).collectAsState()
    val auto = stringResource(R.string.chip_auto)
    // keep a custom value from the advanced page visible and selectable
    fun withCurrent(base: List<Pair<String, String>>, current: String) =
        if (base.any { it.first == current }) base else base + (current to current)
    val resolutions = withCurrent(
        listOf(
            Prefs.AUTO to auto, "1280x720" to "1280×720", "1920x1080" to "1920×1080", "3840x2160" to "3840×2160",
            Prefs.LANDSCAPE to stringResource(R.string.chip_device_landscape),
            Prefs.PORTRAIT to stringResource(R.string.chip_device_portrait),
        ), resolution
    )
    val fpsOptions = withCurrent(listOf("24", "30", "60").map { it to it }, maxFps.toString())
    return listOf(
        toggle(viewModel, Prefs.AUTO_FULLSCREEN, R.string.setting_auto_fullscreen, R.string.setting_auto_fullscreen_desc),
        TvItem(
            stringResource(R.string.setting_resolution), stringResource(R.string.tv_resolution_desc),
            choices = resolutions, selected = resolution, onPick = { viewModel.set(Prefs.RESOLUTION, it) }
        ),
        TvItem(
            stringResource(R.string.tv_fps), stringResource(R.string.tv_fps_desc),
            choices = fpsOptions, selected = maxFps.toString(),
            onPick = { v -> v.toIntOrNull()?.let { viewModel.set(Prefs.MAX_FPS, it) } }
        ),
        TvItem(
            stringResource(R.string.tv_color_range), stringResource(R.string.tv_color_range_desc),
            choices = listOf(
                Prefs.AUTO to auto,
                "full" to stringResource(R.string.tv_color_range_full),
                "limited" to stringResource(R.string.tv_color_range_limited),
            ),
            selected = colorRange, onPick = { viewModel.set(Prefs.COLOR_RANGE, it) }
        ),
        TvItem(
            stringResource(R.string.tv_hevc), stringResource(R.string.tv_hevc_desc),
            choices = listOf(
                Prefs.AUTO to auto,
                Prefs.ON to stringResource(R.string.chip_on),
                Prefs.OFF to stringResource(R.string.chip_off),
            ),
            selected = h265, onPick = { viewModel.set(Prefs.H265, it) }
        ),
        toggle(viewModel, Prefs.OVERSCANNED, R.string.tv_overscan, R.string.tv_overscan_desc),
        TvItem(
            stringResource(R.string.tv_grayscale), stringResource(R.string.tv_grayscale_desc),
            link = true, onClick = { go(TvPage.GRAYSCALE) }
        ),
    )
}

@Composable
private fun audioItems(viewModel: MainViewModel): List<TvItem> {
    val latency by viewModel.pref(Prefs.AUDIO_LATENCY_MS).collectAsState()
    val off = stringResource(R.string.chip_off)
    val steps = listOf(100, 200, 300, 500)
    val latencies = listOf("-1" to off) +
        (steps + latency.takeIf { it >= 0 && it !in steps }).filterNotNull().sorted()
            .map { it.toString() to stringResource(R.string.tv_ms, it) }
    return listOf(
        toggle(viewModel, Prefs.SYNC_CLIENT_VOLUME, R.string.setting_sync_client_volume, R.string.setting_sync_client_volume_desc),
        TvItem(
            stringResource(R.string.tv_audio_delay), stringResource(R.string.tv_audio_delay_desc),
            choices = latencies, selected = latency.toString(),
            onPick = { v -> v.toIntOrNull()?.let { viewModel.set(Prefs.AUDIO_LATENCY_MS, it) } }
        ),
        toggle(viewModel, Prefs.AUDIO_AUTO_BUFFER, R.string.tv_audio_smooth, R.string.tv_audio_auto_buffer_desc),
    )
}

@Composable
private fun headphonesItems(viewModel: MainViewModel): List<TvItem> {
    val status by HeadphonesController.status.collectAsState()
    val connected = status == HeadphonesStatus.CONNECTED
    return listOf(
        TvItem(
            stringResource(if (connected) R.string.tv_headphones_disconnect else R.string.tv_headphones_connect),
            stringResource(if (connected) R.string.tv_headphones_disconnect_desc else R.string.tv_headphones_connect_desc),
            value = headphonesValue(),
            onClick = HeadphonesController::toggleConnection
        ),
        toggle(viewModel, Prefs.HEADPHONES_AUTO_CONNECT, R.string.tv_headphones_auto, R.string.tv_headphones_auto_desc),
    )
}

@Composable
private fun headphonesValue(): String {
    val status by HeadphonesController.status.collectAsState()
    return stringResource(
        when (status) {
            HeadphonesStatus.IDLE -> R.string.tv_headphones_idle
            HeadphonesStatus.NEEDS_APPROVAL -> R.string.tv_headphones_needs_approval
            HeadphonesStatus.NO_DEBUGGING -> R.string.tv_headphones_no_debugging
            HeadphonesStatus.NOT_PAIRED -> R.string.tv_headphones_not_paired
            HeadphonesStatus.CONNECTING -> R.string.tv_headphones_connecting
            HeadphonesStatus.CONNECTED -> R.string.tv_headphones_connected
            HeadphonesStatus.NOT_REACHED -> R.string.tv_headphones_not_reached
        }
    )
}

@Composable
private fun remoteItems(viewModel: MainViewModel): List<TvItem> {
    val ctx = LocalContext.current
    val status = remoteStatus(viewModel)
    // neither flag is observable; poll only while this page is on screen
    var adb by remember { mutableStateOf(LocalAdbDriver.isConnected.get()) }
    var access by remember { mutableStateOf(RemoteAccessibilityService.isServiceRunning) }
    LaunchedEffect(Unit) {
        while (true) {
            adb = LocalAdbDriver.isConnected.get()
            access = RemoteAccessibilityService.isServiceRunning
            delay(2000)
        }
    }
    val on = stringResource(R.string.tv_state_on)
    val off = stringResource(R.string.tv_state_off)
    return listOf(
        toggle(viewModel, Prefs.REMOTE_ENABLED, R.string.tv_remote_enable, R.string.tv_remote_enable_desc),
        TvItem(
            stringResource(R.string.tv_remote_status), stringResource(R.string.tv_remote_status_desc),
            value = stringResource(status.label)
        ),
        TvItem(
            stringResource(R.string.tv_remote_access), stringResource(R.string.tv_remote_access_desc),
            value = if (access) on else off, link = true,
            onClick = {
                try {
                    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: ActivityNotFoundException) {
                }
            }
        ),
        TvItem(
            stringResource(R.string.tv_remote_adb), stringResource(R.string.tv_remote_adb_desc),
            value = stringResource(if (adb) R.string.tv_state_ready else R.string.tv_state_not_ready)
        ),
        TvItem(
            stringResource(R.string.tv_remote_dpad), stringResource(R.string.tv_remote_dpad_short),
            detail = stringResource(R.string.tv_remote_dpad_desc)
        ),
    )
}

@Composable
private fun powerItems(viewModel: MainViewModel): List<TvItem> {
    val standby by viewModel.pref(Prefs.STANDBY_REACHABLE).collectAsState()
    return listOf(
    toggle(viewModel, Prefs.BOOT_AUTO_START, R.string.setting_boot_auto_start, R.string.setting_boot_auto_start_desc),
    TvItem(
        stringResource(R.string.tv_power_mode), stringResource(R.string.tv_power_mode_short),
        detail = stringResource(R.string.tv_power_mode_desc),
        choices = listOf(
            Prefs.ON to stringResource(R.string.tv_power_standby),
            Prefs.OFF to stringResource(R.string.tv_power_saving),
        ),
        selected = if (standby) Prefs.ON else Prefs.OFF,
        onPick = { viewModel.set(Prefs.STANDBY_REACHABLE, it == Prefs.ON) }
    ),
    toggle(viewModel, Prefs.KEEP_SCREEN_ON, R.string.setting_keep_screen_on, R.string.setting_keep_screen_on_desc),
    toggle(viewModel, Prefs.AUTO_START, R.string.setting_auto_start, R.string.setting_auto_start_desc),
)
}

@Composable
private fun diagnosticsItems(viewModel: MainViewModel, go: (TvPage) -> Unit): List<TvItem> = listOf(
    TvItem(stringResource(R.string.tab_logs), stringResource(R.string.tv_logs_desc), link = true,
        onClick = { go(TvPage.LOGS) }),
    toggle(viewModel, Prefs.DEBUG_ENABLED, R.string.setting_debug_overlay, R.string.setting_debug_overlay_desc),
    TvItem(stringResource(R.string.tv_advanced), stringResource(R.string.tv_advanced_desc), link = true,
        onClick = { go(TvPage.ADVANCED) }),
)

// same checks the pref applies on write, so the dialog never accepts what would be dropped
internal enum class TvNameError { EMPTY, TOO_LONG, CONTROL }

internal fun tvNameError(name: String): TvNameError? = when {
    name.isBlank() -> TvNameError.EMPTY
    name.any(Char::isISOControl) -> TvNameError.CONTROL
    name.toByteArray(Charsets.UTF_8).size > 63 -> TvNameError.TOO_LONG
    else -> null
}

@Composable
private fun TvRenameDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { fieldFocus.requestFocusUntilLanded() }
    val trimmed = text.trim()
    val error = tvNameError(trimmed)
    val bytes = trimmed.toByteArray(Charsets.UTF_8).size
    val save = { if (error == null) onSave(trimmed) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(min = 460.dp, max = 580.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(TvTile)
                .padding(32.dp)
        ) {
            Text(stringResource(R.string.tv_name), color = TvText, fontSize = 24.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = text,
                // a little headroom past the limit so the error can be shown instead of silently cutting
                onValueChange = { text = it.take(80) },
                singleLine = true,
                isError = error != null,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, color = TvText),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TvText, unfocusedBorderColor = Color(0x55FFFFFF), cursorColor = TvText,
                    errorBorderColor = TvError
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus)
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    when (error) {
                        TvNameError.EMPTY -> stringResource(R.string.tv_name_error_empty)
                        TvNameError.TOO_LONG -> stringResource(R.string.tv_name_error_long)
                        TvNameError.CONTROL -> stringResource(R.string.tv_name_error_control)
                        null -> stringResource(R.string.tv_name_shared)
                    },
                    color = if (error != null) TvError else TvMuted, fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(R.string.tv_name_bytes, bytes),
                    color = if (bytes > 63) TvError else TvMuted, fontSize = 15.sp
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvPill(stringResource(R.string.tv_cancel), onDismiss)
                TvPill(stringResource(R.string.tv_save), { save() }, enabled = error == null)
            }
        }
    }
}

@Composable
internal fun TvSubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(TvBg).padding(horizontal = 48.dp, vertical = 32.dp)) {
        Text(title, color = TvText, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

// near-black and near-white steps drawn by this TV, to compare against the same pattern sent over AirPlay
private val GRAY_BLACK = listOf(0, 4, 8, 12, 16, 20, 24, 32)
private val GRAY_WHITE = listOf(223, 231, 235, 239, 243, 247, 251, 255)

@Composable
private fun TvGrayscaleScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        GrayRow(GRAY_BLACK, Color.Black, Modifier.weight(1f))
        GrayRow(GRAY_WHITE, Color.White, Modifier.weight(1f))
        Text(
            stringResource(R.string.tv_grayscale_note), color = TvMuted, fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().background(TvBg).padding(horizontal = 48.dp, vertical = 14.dp)
        )
    }
}

@Composable
private fun GrayRow(levels: List<Int>, surround: Color, modifier: Modifier) {
    val label = if (surround == Color.Black) Color(0xFF8E8E93) else Color(0xFF3A3A3C)
    Row(
        modifier.fillMaxWidth().background(surround).padding(horizontal = 48.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        levels.forEach { v ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().weight(1f).background(Color(v, v, v)))
                Spacer(Modifier.height(10.dp))
                Text(v.toString(), color = label, fontSize = 16.sp)
            }
        }
    }
}

// ---- pairing ----

@Composable
internal fun TvPinOverlays(
    remotePin: String?,
    remoteSucceeded: () -> Boolean,
    onDismissRemote: () -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(remotePin) { if (remotePin != null) shown = true }
    if (shown) TvPinOverlay(remotePin, remoteSucceeded, onDismissRemote) { shown = false }
}

// keeps the last code up through a short "done" beat, only when that service reports success
@Composable
private fun TvPinOverlay(
    pin: String?,
    succeeded: () -> Boolean,
    onDismiss: () -> Unit,
    onFinished: () -> Unit
) {
    var shownPin by remember { mutableStateOf<String?>(null) }
    var paired by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    val success by rememberUpdatedState(succeeded)
    val fade = remember { Animatable(1f) }
    LaunchedEffect(pin) {
        if (pin != null) {
            dismissed = false
            paired = false
            fade.snapTo(1f)
            shownPin = pin
        } else if (shownPin != null) {
            if (!dismissed && success()) {
                paired = true
                delay(650)
            }
            fade.animateTo(0f, tween(180))
            shownPin = null
            paired = false
            onFinished()
        } else {
            // the request ended before this screen ever showed it
            onFinished()
        }
    }
    val code = shownPin ?: return
    Dialog(
        onDismissRequest = {
            dismissed = true
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false
        )
    ) {
        Box(
            Modifier.fillMaxSize().background(TvBackdrop).graphicsLayer { alpha = fade.value },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SettingsRemote, null, Modifier.size(26.dp), tint = TvMuted)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.tv_remote_enable),
                        color = TvMuted, fontSize = 18.sp
                    )
                }
                Spacer(Modifier.height(24.dp))
                Crossfade(targetState = paired, animationSpec = tween(160), label = "pairTitle") { done ->
                    Text(
                        stringResource(
                            when {
                                !done -> R.string.tv_pair_title
                                else -> R.string.tv_remote_paired
                            }
                        ),
                        color = TvText, fontSize = 28.sp, fontWeight = FontWeight.Light
                    )
                }
                Spacer(Modifier.height(40.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.semantics { contentDescription = code.toList().joinToString(" ") }
                ) {
                    code.forEachIndexed { i, c -> PinDigit(c, i, paired) }
                }
                Spacer(Modifier.height(36.dp))
                val hintAlpha by animateFloatAsState(if (paired) 0f else 1f, tween(160), label = "hint")
                Text(
                    stringResource(R.string.tv_remote_pair_hint),
                    color = TvMuted, fontSize = 18.sp,
                    modifier = Modifier.graphicsLayer { alpha = hintAlpha }
                )
            }
        }
    }
}

@Composable
private fun PinDigit(digit: Char, index: Int, paired: Boolean) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(70L * index)
        appear.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
    }
    // completion runs left to right as a quick wave of white tiles
    val fill by animateFloatAsState(
        if (paired) 1f else 0f, tween(200, delayMillis = if (paired) 55 * index else 0), label = "fill"
    )
    Box(
        Modifier
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 16.dp.toPx()
            }
            .size(88.dp, 112.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(lerp(TvTile, TvText, fill))
            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            digit.toString(), color = lerp(TvText, TvInk, fill),
            fontSize = 60.sp, fontWeight = FontWeight.Light
        )
    }
}

// ---- cast stage ----

// Sits between "a device connected" and its first picture, so casting feels like the TV changing
// input rather than an app opening. Covers the receiver UI, then opens onto the stream.
internal class TvCastStage {
    // the service state isn't known until the activity binds; hold plain black until it is,
    // so neither the receiver UI nor the stage flashes up the wrong way
    var settling by mutableStateOf(true)
    var visible by mutableStateOf(true)
    var revealing by mutableStateOf(false)
    // one stage per connection: a timeout or Back must not bring it back until the sender reconnects
    var spent = false
    val veil = Animatable(1f)
    val mark = Animatable(0f)
    val ring = Animatable(0f)
}

private const val STAGE_SETTLE_MS = 350L
private const val STAGE_TIMEOUT_MS = 8_000L

@Composable
internal fun rememberTvCastStage(connected: Boolean, streaming: Boolean, pinShowing: Boolean): TvCastStage {
    val stage = remember { TvCastStage() }
    LaunchedEffect(connected, streaming) {
        if (stage.settling && !connected && !streaming) {
            // nothing is connecting (yet); a late bind that says otherwise restarts this effect
            delay(STAGE_SETTLE_MS)
            stage.settling = false
            stage.veil.animateTo(0f, tween(200))
            stage.visible = false
            return@LaunchedEffect
        }
        stage.settling = false
        when {
            !connected -> {
                stage.spent = false
                if (stage.visible) {
                    stage.veil.animateTo(0f, tween(220))
                    stage.visible = false
                    stage.revealing = false
                }
            }
            streaming -> {
                stage.spent = true
                if (stage.visible && !stage.revealing) {
                    stage.revealing = true
                    try {
                        // give the decoder a beat to put its first frame up behind the veil
                        delay(200)
                        coroutineScope {
                            launch { stage.mark.animateTo(0f, tween(340, easing = FastOutSlowInEasing)) }
                            stage.veil.animateTo(0f, tween(420, delayMillis = 80, easing = FastOutSlowInEasing))
                        }
                    } finally {
                        // an interrupted reveal must never leave the picture covered
                        stage.visible = false
                        stage.revealing = false
                    }
                }
            }
            !stage.spent -> {
                stage.spent = true
                stage.revealing = false
                stage.visible = true
                coroutineScope {
                    launch { stage.mark.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
                    stage.veil.animateTo(1f, tween(240))
                }
            }
        }
    }
    LaunchedEffect(stage.visible, stage.revealing, stage.settling) {
        if (stage.visible && !stage.revealing && !stage.settling) {
            stage.ring.snapTo(0f)
            stage.ring.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
    }
    // a sender that connects but never streams (or a bare probe) must not leave the TV covered;
    // the clock waits while a code is on screen
    LaunchedEffect(stage.visible, stage.revealing, stage.settling, pinShowing) {
        if (stage.visible && !stage.revealing && !stage.settling && !pinShowing) {
            delay(STAGE_TIMEOUT_MS)
            stage.veil.animateTo(0f, tween(300))
            stage.visible = false
        }
    }
    return stage
}

@Composable
internal fun TvCastStageLayer(stage: TvCastStage, onDisconnect: () -> Unit) {
    if (!stage.visible) return
    BackHandler(enabled = !stage.settling) {
        onDisconnect()
        stage.visible = false
        stage.revealing = false
    }
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = stage.veil.value }.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                val m = stage.mark.value
                alpha = m
                // in: settle from slightly small; out: open up towards the viewer
                val s = if (stage.revealing) 1f + (1f - m) * 0.14f else 0.92f + 0.08f * m
                scaleX = s
                scaleY = s
            }
        ) {
            Box(Modifier.size(136.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 1.5.dp.toPx()
                    drawArc(
                        // the ring draws itself once and then holds still: no looping work while waiting
                        color = TvText.copy(alpha = 0.32f),
                        startAngle = -90f, sweepAngle = 360f * stage.ring.value, useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                        style = Stroke(width = w)
                    )
                }
                AirPlayMark(48.dp, TvText)
            }
            Spacer(Modifier.height(30.dp))
            Text(
                stringResource(R.string.tv_stage_connecting),
                color = TvMuted, fontSize = 20.sp, fontWeight = FontWeight.Light
            )
        }
    }
}
