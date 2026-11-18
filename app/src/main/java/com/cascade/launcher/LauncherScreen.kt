package com.cascade.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.key
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

val LocalAccent = staticCompositionLocalOf { Color(AccentChoices[0]) }
private val TextShadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 10f)

@Composable
fun LauncherScreen(act: MainActivity) {
    val accent = Color(act.settings.accent)
    MaterialTheme(colorScheme = darkColorScheme(primary = accent)) {
        CompositionLocalProvider(LocalAccent provides accent) {
            // Re-apply bar hiding whenever the switches change or settings opens/closes.
            LaunchedEffect(act.settings.hideStatusBar, act.settings.hideNavBar, act.settingsOpen, act.drawerOpen) {
                act.applySystemBars()
            }
            // A launcher must never "exit" on back.
            BackHandler(enabled = !act.drawerOpen && !act.settingsOpen) {
                when {
                    act.darkenEditing -> act.finishDarkenPreview()
                    act.islandSizeStage != null -> act.finishIslandSizing()
                    else -> act.stopEditing()
                }
            }
            BackHandler(enabled = act.drawerOpen && !act.settingsOpen) { act.drawerOpen = false }

            Box(Modifier.fillMaxSize()) {
                HomeScreen(act)
                // The drawer is built once and kept ready; opening it only animates a GPU layer
                // (slide + fade), so there's nothing to construct or load at the moment you swipe.
                val drawerProgress by animateFloatAsState(
                    targetValue = if (act.drawerOpen) 1f else 0f,
                    animationSpec = if (act.drawerOpen) {
                        tween(210, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
                    } else {
                        tween(160, easing = androidx.compose.animation.core.FastOutLinearInEasing)
                    },
                    label = "drawer",
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = drawerProgress
                            // Fully closed: park it off-screen so it can't catch touches.
                            translationY = if (drawerProgress == 0f) size.height * 2f
                            else (1f - drawerProgress) * size.height * 0.12f
                        }
                        .then(if (drawerProgress == 0f) Modifier.clearAndSetSemantics { } else Modifier),
                ) {
                    AppDrawer(act = act, onClose = { act.drawerOpen = false })
                }
                AnimatedVisibility(
                    visible = act.settingsOpen,
                    enter = slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(200)),
                    exit = slideOutHorizontally(tween(220)) { it / 4 } + fadeOut(tween(180)),
                ) {
                    SettingsScreen(act = act, onClose = { act.settingsOpen = false })
                }
            }
        }
    }
}

// =====================================================================================
// Home
// =====================================================================================

@Composable
private fun HomeScreen(act: MainActivity) {
    val s = act.settings
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 70.dp.toPx() }
    val listScroll = rememberScrollState()
    var homeMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val favorites = remember(act.allApps, act.favoriteKeys) {
        val byKey = act.allApps.associateBy { it.key }
        act.favoriteKeys.mapNotNull { byKey[it] }
    }
    // Background darkening (live while you drag the slider in the darkness editor).
    val darkAlpha by animateFloatAsState(
        if (s.darkenEnabled) s.darkenAmount / 100f else 0f,
        animationSpec = tween(120),
        label = "darken",
    )

    // ---- Dynamic Island (home screen only) ----
    val context = LocalContext.current
    var rootWidthPx by remember { mutableIntStateOf(0) }
    var clockWidthPx by remember { mutableIntStateOf(0) }
    var slotCenter by remember { mutableStateOf(Offset.Zero) }   // top-center of the island slot
    var islandStage by remember { mutableStateOf(IslandStage.MINI) }
    val sizingStage = act.islandSizeStage              // non-null while resizing in the editor
    val editingIsland = act.editTarget == EditTarget.ISLAND || sizingStage != null
    val shownStage = sizingStage ?: islandStage
    val islandExpanded = shownStage == IslandStage.EXPANDED
    val nowPlaying = NowPlaying.media.value
    val islandMedia = nowPlaying ?: if (editingIsland) PreviewMedia else null
    val islandVisible = islandMedia != null && (
        editingIsland || (s.islandEnabled && !(s.islandHideWhenPaused && !islandMedia.playing))
        )
    LaunchedEffect(islandVisible) { if (!islandVisible) islandStage = IslandStage.MINI }
    // Nothing playing and Spotify not running → a small Spotify button sits where the island goes.
    val spotifyApp = remember(act.allApps) { act.allApps.firstOrNull { it.packageName == IslandService.SPOTIFY } }
    val showSpotifyButton = !islandVisible && islandMedia == null && s.islandEnabled && spotifyApp != null
    val reserveSlot = islandVisible || showSpotifyButton
    val screenWidthDp: Dp = with(density) { if (rootWidthPx > 0) rootWidthPx.toDp() else 400.dp }
    // Medium stage: clock width (or a set width). Small: compact. Large: % of the screen.
    val islandWidth: Dp = with(density) {
        if (s.islandMatchClock && s.showClock && clockWidthPx > 0) clockWidthPx.toDp().coerceAtLeast(150.dp)
        else s.islandPillWidth.dp
    }.coerceAtMost(screenWidthDp - 16.dp)
    val miniSize = StageSize(s.islandMiniWidth.dp, s.islandMiniHeight.dp, s.islandMiniScale / 100f)
    val pillSize = StageSize(islandWidth, s.islandPillHeight.dp, s.islandPillScale / 100f)
    val expandedSize = StageSize(
        ((screenWidthDp - 24.dp) * (s.islandExpWidth / 100f)).coerceAtLeast(200.dp),
        if (s.islandExpHeight > 0) s.islandExpHeight.dp else null,
        s.islandExpScale / 100f,
    )
    val slotHeight = maxOf(s.islandMiniHeight.dp, pillHeightFor(pillSize, s.pillStyle))

    LaunchedEffect(act.returnTick) {
        if (act.returnTick > 0) {
            act.homeEnter.animateTo(
                1f,
                tween(420, easing = androidx.compose.animation.core.CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)),
            )
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { rootWidthPx = it.width }
            // 1) Darken the whole wallpaper by the chosen amount.
            .background(Color.Black.copy(alpha = darkAlpha))
            // 2) A light fixed shade at the top and bottom so text stays readable.
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.18f),
                    0.4f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.3f),
                ),
            )
            // Coming back from an app: the clock, island and apps rise into place (a slight
            // zoom up from 92% with a fade) while the system slides the app away. The
            // wallpaper and dim behind stay put.
            .graphicsLayer {
                val p = act.homeEnter.value
                alpha = 0.25f + 0.75f * p
                val sc = 0.92f + 0.08f * p
                scaleX = sc
                scaleY = sc
            }
            // Swipe detection that works anywhere on the home screen (over the clock, the app
            // list, empty space...). It watches events *before* children see them, and only
            // takes over when the favorites list itself isn't scrolling.
            .pointerInput(s.swipeUp, s.swipeDown, act.editTarget, act.darkenEditing, act.islandSizeStage) {
                if (act.editTarget != null || act.darkenEditing || act.islandSizeStage != null) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val startScroll = listScroll.value
                    var dx = 0f
                    var dy = 0f
                    var fired = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val delta = change.positionChangeIgnoreConsumed()
                        dx += delta.x
                        dy += delta.y
                        if (fired) { change.consume(); continue }
                        val listMoved = listScroll.value != startScroll
                        val vertical = kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.3f
                        if (!listMoved && vertical && kotlin.math.abs(dy) > swipeThreshold) {
                            val upAllowed = listScroll.value >= listScroll.maxValue
                            val downAllowed = listScroll.value <= 0
                            if (dy < 0 && upAllowed) {
                                fired = true; act.runGesture(s.swipeUp)
                            } else if (dy > 0 && downAllowed) {
                                fired = true; act.runGesture(s.swipeDown)
                            }
                            // Swallow the rest so the app under the finger doesn't get tapped.
                            if (fired) change.consume()
                        }
                    }
                }
            }
            .pointerInput(s.doubleTap, act.editTarget, act.darkenEditing, act.islandSizeStage) {
                if (act.editTarget != null || act.darkenEditing || act.islandSizeStage != null) return@pointerInput
                detectTapGestures(
                    onDoubleTap = if (s.doubleTap == GestureAction.NONE) null else { _ -> act.runGesture(s.doubleTap) },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        homeMenu = true
                    },
                )
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                // When a bar is set to hidden, reserve no space for it at all — so when you swipe
                // it into view for a moment it simply floats over the top, and nothing on the home
                // screen shifts down and back.
                .then(if (s.hideStatusBar) Modifier else Modifier.statusBarsPadding())
                .then(if (s.hideNavBar) Modifier else Modifier.navigationBarsPadding()),
        ) {
            Spacer(Modifier.height(if (reserveSlot) 20.dp else 56.dp))
            if (s.showClock || s.showDate || s.showBattery || islandVisible) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .offset(x = s.clockX.dp, y = s.clockY.dp),
                ) {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .then(
                                if (act.editTarget == EditTarget.CLOCK) {
                                    Modifier.border(1.5.dp, LocalAccent.current, RoundedCornerShape(12.dp))
                                } else Modifier,
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Reserved slot: the island itself is drawn on a layer above (so it can
                        // expand over the clock) but lines up with this spot.
                        if (reserveSlot) {
                            Box(
                                Modifier
                                    .width(islandWidth)
                                    .height(slotHeight)
                                    .onGloballyPositioned {
                                        val p = it.positionInRoot()
                                        slotCenter = Offset(p.x + it.size.width / 2f, p.y)
                                    },
                            )
                            Spacer(Modifier.height(14.dp))
                        }
                        if (s.showClock || s.showDate || s.showBattery) {
                            CenteredClock(act, onTimeWidth = { clockWidthPx = it })
                        }
                    }
                }
            }

            // Favorites list sits in the lower part of the screen, within thumb reach.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.BottomStart,
            ) {
                Column(
                    Modifier
                        .offset(y = s.listY.dp)
                        .then(
                            if (act.editTarget == EditTarget.APPS) {
                                Modifier.border(1.5.dp, LocalAccent.current, RoundedCornerShape(12.dp))
                            } else Modifier,
                        )
                        .verticalScroll(listScroll)
                        .padding(top = 24.dp, bottom = 8.dp),
                ) {
                    if (favorites.isEmpty() && act.allApps.isNotEmpty()) {
                        Text(
                            "Long-press an app in the drawer and choose “Add to home”",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 28.dp),
                        )
                    }
                    // Rebuild the rows whenever the list is moved, so the icons are always drawn
                    // fresh at the new position (some devices kept showing a stale icon image
                    // after a placement-only move while the names moved correctly).
                    favorites.forEachIndexed { index, app -> key(app.key, s.listX, s.listY) {
                        AppRow(
                            act = act,
                            app = app,
                            onHome = true,
                            canMoveUp = index > 0,
                            canMoveDown = index < favorites.lastIndex,
                        )
                    } }
                }
            }

            // Bottom bar: swipe hint + search button.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(horizontal = 20.dp),
            ) {
                if (s.showAllAppsHint) {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(onClick = { act.openDrawer(false) })
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("︿", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                        Text(
                            "All apps",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            letterSpacing = 1.sp,
                        )
                    }
                }
                if (s.showSearchButton) {
                    RoundButton(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        onClick = { act.openDrawer(true) },
                    ) { SearchGlyph() }
                }
            }
        }

        // Spotify shortcut (when Spotify isn't running)
        if (showSpotifyButton && spotifyApp != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .offset {
                        IntOffset(
                            (slotCenter.x - rootWidthPx / 2f + s.islandX.dp.toPx()).roundToInt(),
                            (slotCenter.y + s.islandY.dp.toPx()).roundToInt(),
                        )
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                SpotifyButton(
                    act = act,
                    spotifyApp = spotifyApp,
                    modifier = Modifier.combinedClickable(onClick = { act.launch(spotifyApp) }),
                )
            }
        }

        // Dynamic Island layer
        if (islandVisible && islandMedia != null) {
            if (islandExpanded && sizingStage == null) {
                // Tap anywhere else to collapse.
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures { islandStage = IslandStage.MINI } },
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .offset {
                        IntOffset(
                            // Expanded card is centered on the screen; collapsed pill follows the clock.
                            if (islandExpanded) 0
                            else (slotCenter.x - rootWidthPx / 2f + s.islandX.dp.toPx()).roundToInt(),
                            (slotCenter.y + s.islandY.dp.toPx()).roundToInt(),
                        )
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                HomeIsland(
                    info = islandMedia,
                    style = s.islandVisualizer,
                    stage = shownStage,
                    mini = miniSize,
                    pill = pillSize,
                    expanded = expandedSize,
                    pillStyle = s.pillStyle,
                    miniFeatures = s.miniFeatures,
                    expFeatures = s.expFeatures,
                    borders = listOf(
                        if (s.islandMiniBorderOn) s.islandMiniBorder.dp else 0.dp,
                        if (s.islandPillBorderOn) s.islandPillBorder.dp else 0.dp,
                        if (s.islandExpBorderOn) s.islandExpBorder.dp else 0.dp,
                    ),
                    font = (s.islandFont ?: s.labelFont).family(),
                    autoShrink = sizingStage == null,
                    onStageChange = { if (sizingStage == null) islandStage = it },
                    onOpenPlayer = {
                        if (sizingStage == null) {
                            islandStage = IslandStage.MINI
                            if (nowPlaying != null) NowPlaying.openPlayer(context)
                        }
                    },
                )
            }
        }

        // On-screen +/- position editor.
        act.editTarget?.let { target ->
            PositionEditor(act, target)
        }
        // Darkness editor: slider over the real home screen = live preview.
        if (act.darkenEditing) DarkenEditor(act)
        // Island size editor: per-stage width/height/size with the real island as preview.
        act.islandSizeStage?.let { IslandSizeEditor(act, it) }

        // Long-press on empty space.
        Box(Modifier.align(Alignment.Center)) {
            DropdownMenu(expanded = homeMenu, onDismissRequest = { homeMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Launcher settings") },
                    onClick = { homeMenu = false; act.openSettings() },
                )
                DropdownMenuItem(
                    text = { Text("Move apps / clock / island") },
                    onClick = { homeMenu = false; act.startEditing(EditTarget.APPS) },
                )
                DropdownMenuItem(
                    text = { Text("Change wallpaper") },
                    onClick = { homeMenu = false; act.openWallpaperPicker() },
                )
            }
        }
    }
}

@Composable
private fun RoundButton(modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier
            .size(52.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(Color(0xFF1E1E22))
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun SearchGlyph(size: androidx.compose.ui.unit.Dp = 22.dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val stroke = 2.4.dp.toPx()
        val r = this.size.minDimension * 0.33f
        val c = Offset(this.size.width * 0.42f, this.size.height * 0.42f)
        drawCircle(color, r, c, style = Stroke(stroke))
        drawLine(
            color,
            start = Offset(c.x + r * 0.72f, c.y + r * 0.72f),
            end = Offset(this.size.width * 0.92f, this.size.height * 0.92f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

// =====================================================================================
// Centered clock (lock-screen style)
// =====================================================================================

@Composable
private fun CenteredClock(act: MainActivity, onTimeWidth: (Int) -> Unit = {}) {
    val s = act.settings
    val clockFont = s.clockFont.family()
    val context = LocalContext.current
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    val is24h = when (s.clockFormat) {
        ClockFormat.SYSTEM -> DateFormat.is24HourFormat(context)
        ClockFormat.H12 -> false
        ClockFormat.H24 -> true
    }
    val timeFmt = remember(is24h) { DateTimeFormatter.ofPattern(if (is24h) "H:mm" else "h:mm") }
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()) }
    val noRipple = remember { MutableInteractionSource() }
    val clockColor = Color(s.clockColor)

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (s.showDate) {
            Text(
                text = now.format(dateFmt),
                color = clockColor.copy(alpha = clockColor.alpha * 0.92f),
                fontSize = s.dateSize.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = s.clockFont.familyAt(600),
                style = TextStyle(shadow = if (clockColor.alpha > 0.9f) TextShadow else null),
                modifier = Modifier.combinedClickable(
                    interactionSource = noRipple, indication = null, onClick = act::openCalendar,
                ),
            )
        }
        if (s.showClock) {
            ClockTimeText(
                s = s,
                text = now.format(timeFmt),
                modifier = Modifier
                    .onSizeChanged { onTimeWidth(it.width) }
                    .combinedClickable(
                        interactionSource = noRipple, indication = null, onClick = act::openClock,
                    ),
            )
        }
        if (s.showBattery) {
            val battery = rememberBattery()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.12f))
                    .combinedClickable(onClick = act::openBatterySettings)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                BatteryGlyph(battery.level, battery.charging)
                Spacer(Modifier.width(7.dp))
                BatteryStatusText(battery, clockFont)
            }
        }
    }
}

@Composable
private fun BatteryGlyph(level: Int, charging: Boolean) {
    val accent = LocalAccent.current
    Canvas(Modifier.size(width = 20.dp, height = 11.dp)) {
        val stroke = 1.3.dp.toPx()
        val nub = 2.dp.toPx()
        val body = Size(size.width - nub - stroke, size.height - stroke)
        drawRoundRect(
            Color.White.copy(alpha = 0.85f),
            topLeft = Offset(stroke / 2, stroke / 2),
            size = body,
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(stroke),
        )
        drawRoundRect(
            Color.White.copy(alpha = 0.85f),
            topLeft = Offset(size.width - nub, size.height * 0.3f),
            size = Size(nub, size.height * 0.4f),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
        val inset = stroke + 1.2.dp.toPx()
        val fillW = (body.width - inset * 2 + stroke) * (level.coerceIn(0, 100) / 100f)
        drawRoundRect(
            when {
                charging -> Color(0xFF4CD964)
                level <= 20 -> accent
                else -> Color.White
            },
            topLeft = Offset(inset, inset),
            size = Size(fillW, size.height - inset * 2),
            cornerRadius = CornerRadius(1.5.dp.toPx()),
        )
    }
}

// =====================================================================================
// App row (shared by home + drawer)
// =====================================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    act: MainActivity,
    app: AppEntry,
    onHome: Boolean,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    afterLaunch: () -> Unit = {},
) {
    val s = act.settings
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val startPad = if (onHome) (28 + s.listX).coerceAtLeast(0).dp else 28.dp

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .height(if (onHome) s.rowHeight.dp else 52.dp)
                .combinedClickable(
                    onClick = { act.launch(app); afterLaunch() },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menu = true
                    },
                )
                .padding(start = startPad, end = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val display = if (onHome) s.homeDisplay else s.drawerDisplay
            if (display != AppDisplay.NAME_ONLY) {
                AppIconImage(act, app, Modifier.size(if (onHome) s.iconSize.dp else 26.dp))
            }
            if (display == AppDisplay.ICON_AND_NAME) {
                Spacer(Modifier.width(if (onHome) (s.iconSize * 0.75f).dp else 20.dp))
            }
            // Icon only: same list, same spot — the name just isn't drawn.
            if (display != AppDisplay.ICON_ONLY) Text(
                text = app.label,
                color = Color.White,
                fontSize = if (onHome) s.labelSize.sp else 18.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = s.labelFont.family(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = if (onHome) TextShadow else null),
            )
        }

        AppMenu(act, app, onHome, canMoveUp, canMoveDown, menu) { menu = false }
    }
}

/** Long-press menu shared by list rows and icon cells. */
@Composable
private fun AppMenu(
    act: MainActivity,
    app: AppEntry,
    onHome: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, offset = DpOffset(28.dp, 0.dp)) {
        val fav = act.isFavorite(app)
        DropdownMenuItem(
            text = { Text(if (fav) "Remove from home" else "Add to home") },
            onClick = { onDismiss(); act.toggleFavorite(app) },
        )
        if (onHome && canMoveUp) {
            DropdownMenuItem(text = { Text("Move up") }, onClick = { onDismiss(); act.moveFavorite(app, -1) })
        }
        if (onHome && canMoveDown) {
            DropdownMenuItem(text = { Text("Move down") }, onClick = { onDismiss(); act.moveFavorite(app, 1) })
        }
        if (!onHome) {
            DropdownMenuItem(text = { Text("Hide from drawer") }, onClick = { onDismiss(); act.toggleHidden(app) })
        }
        DropdownMenuItem(text = { Text("App info") }, onClick = { onDismiss(); act.openAppInfo(app) })
        DropdownMenuItem(text = { Text("Uninstall") }, onClick = { onDismiss(); act.uninstall(app) })
    }
}

@Composable
fun AppIconImage(act: MainActivity, app: AppEntry, modifier: Modifier, monochrome: Boolean? = null) {
    val sizePx = act.iconSizePx
    val mono = monochrome ?: act.settings.monochromeIcons
    val icon by produceState(act.repo.peekIcon(app, sizePx, mono), app.key, mono, act.iconGeneration) {
        // Already cached (the usual case after preloading)? Then there's nothing to do.
        if (value == null) value = withContext(Dispatchers.IO) { act.repo.loadIcon(app, sizePx, mono) }
    }
    val current = icon
    if (current == null) {
        Box(modifier)
    } else {
        Image(
            bitmap = current.bitmap,
            contentDescription = null,
            modifier = modifier,
            colorFilter = if (current.tintable) ColorFilter.tint(Color.White) else null,
        )
    }
}

// =====================================================================================
// App drawer with search + alphabet scrubber
// =====================================================================================

private sealed interface DrawerItem {
    val key: String
    data class Header(val letter: String) : DrawerItem { override val key = "h_$letter" }
    data class App(val app: AppEntry) : DrawerItem { override val key = app.key }
}



private fun sectionOf(label: String): String {
    val c = label.firstOrNull { !it.isWhitespace() }?.uppercaseChar() ?: return "#"
    return if (c in 'A'..'Z') c.toString() else "#"
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AppDrawer(act: MainActivity, onClose: () -> Unit) {
    val s = act.settings
    val accent = LocalAccent.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val close = {
        keyboard?.hide()
        focusManager.clearFocus()
        onClose()
    }

    val apps = remember(act.allApps, s.hiddenApps) { act.drawerApps }
    val (items, headerIndex) = remember<Pair<List<DrawerItem>, Map<String, Int>>>(apps, query, s.showSectionHeaders) {
        val q = query.trim()
        if (q.isNotEmpty()) {
            val matches = act.allApps
                .filter { it.label.contains(q, ignoreCase = true) }
                .sortedBy { if (it.label.startsWith(q, ignoreCase = true)) 0 else 1 }
            matches.map<AppEntry, DrawerItem> { DrawerItem.App(it) } to emptyMap()
        } else {
            val list = mutableListOf<DrawerItem>()
            val idx = linkedMapOf<String, Int>()
            apps.groupBy { sectionOf(it.label) }
                .toSortedMap(compareBy<String> { if (it == "#") "~" else it })
                .forEach { (letter, group) ->
                    idx[letter] = list.size
                    if (s.showSectionHeaders) list += DrawerItem.Header(letter)
                    group.forEach { list += DrawerItem.App(it) }
                }
            list to idx
        }
    }

    LaunchedEffect(act.drawerOpen) {
        if (act.drawerOpen) {
            if (act.drawerFocusSearch) runCatching { focus.requestFocus() }
        } else {
            // Closed: tidy up for next time (it stays built in the background).
            query = ""
            focusManager.clearFocus()
            listState.scrollToItem(0)
        }
    }
    LaunchedEffect(query) { listState.scrollToItem(0) }

    // Pull down while at the top of the list to close the drawer.
    val closeThreshold = with(LocalDensity.current) { 110.dp.toPx() }
    val pullToClose = remember {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0) {
                    pulled += available.y
                    if (pulled > closeThreshold) { pulled = 0f; close() }
                } else if (consumed.y != 0f) {
                    pulled = 0f
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity { pulled = 0f; return Velocity.Zero }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E11).copy(alpha = s.drawerOpacity / 100f))
            // Fixed bar-sized padding (even while bars are still sliding in), so the drawer's
            // content never shifts during the opening animation.
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Search field
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchGlyph(18.dp, Color.White.copy(alpha = 0.5f))
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("Search apps", color = Color.White.copy(alpha = 0.45f), fontSize = 17.sp)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                            cursorBrush = SolidColor(accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                when (val first = items.firstOrNull()) {
                                    is DrawerItem.App -> { act.launch(first.app); close() }
                                    else -> {}
                                }
                            }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focus),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Text(
                            "✕",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 16.sp,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .combinedClickable(onClick = { query = "" }),
                        )
                    }
                }
                // Settings button
                Box(
                    Modifier
                        .padding(start = 8.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .combinedClickable(onClick = {
                            keyboard?.hide()
                            focusManager.clearFocus()
                            act.openSettings()
                        }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⚙", color = Color.White.copy(alpha = 0.75f), fontSize = 22.sp)
                }
            }

            val showScrubber = query.isEmpty() && s.showScrubber && headerIndex.isNotEmpty()
            Box(Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(pullToClose)
                        .padding(end = if (showScrubber) 28.dp else 0.dp),
                ) {
                    items(items, key = { it.key }) { item ->
                        when (item) {
                            is DrawerItem.Header -> Text(
                                item.letter,
                                color = accent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 28.dp, top = 14.dp, bottom = 2.dp),
                            )
                            is DrawerItem.App -> AppRow(act, item.app, onHome = false, afterLaunch = close)
                        }
                    }
                }

                if (showScrubber) {
                    AlphabetScrubber(
                        letters = headerIndex.keys.toList(),
                        modifier = Modifier.align(Alignment.CenterEnd),
                        onLetter = { letter ->
                            headerIndex[letter]?.let { scope.launch { listState.scrollToItem(it) } }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlphabetScrubber(letters: List<String>, modifier: Modifier, onLetter: (String) -> Unit) {
    val accent = LocalAccent.current
    var active by remember { mutableStateOf<String?>(null) }
    var heightPx by remember { mutableFloatStateOf(1f) }
    val haptic = LocalHapticFeedback.current

    fun pick(y: Float) {
        val i = ((y / heightPx) * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val letter = letters[i]
        if (letter != active) {
            active = letter
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onLetter(letter)
        }
    }

    BoxWithConstraints(modifier.fillMaxHeight()) {
        heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        Column(
            Modifier
                .fillMaxHeight()
                .width(32.dp)
                .padding(vertical = 8.dp)
                .pointerInput(letters) {
                    detectVerticalDragGestures(
                        onDragStart = { pick(it.y) },
                        onVerticalDrag = { change, _ -> pick(change.position.y) },
                        onDragEnd = { active = null },
                        onDragCancel = { active = null },
                    )
                }
                .pointerInput(letters) {
                    detectTapGestures(onPress = {
                        pick(it.y)
                        tryAwaitRelease()
                        active = null
                    })
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { l ->
                Text(
                    l,
                    color = if (l == active) accent else Color.White.copy(alpha = 0.55f),
                    fontSize = if (l == active) 14.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    // Big letter bubble while scrubbing
    active?.let { letter ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    letter,
                    color = if (accent.red + accent.green + accent.blue > 2.5f) Color.Black else Color.White,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** The Spotify button in the chosen style (Settings → Dynamic Island → Spotify button). */
@Composable
fun SpotifyButton(act: MainActivity, spotifyApp: AppEntry?, modifier: Modifier = Modifier, style: BadgeStyle? = null, sizeDp: Int? = null) {
    val s = act.settings
    val st = style ?: s.badgeStyle
    val dim = (sizeDp ?: s.badgeSize).dp
    val shape = if (st == BadgeStyle.SQUIRCLE) RoundedCornerShape(30) else CircleShape
    val color = Color(s.clockColor).let { it.copy(alpha = it.alpha * s.badgeOpacity / 100f) }
    if (st == BadgeStyle.ORIGINAL) {
        Box(modifier.size(dim).clip(shape), contentAlignment = Alignment.Center) {
            if (spotifyApp != null) AppIconImage(act, spotifyApp, Modifier.fillMaxSize(), monochrome = false)
        }
    } else {
        SpotifyBadge(st, color, LocalAccent.current, Modifier.size(dim).clip(shape).then(modifier))
    }
}
