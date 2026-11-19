package com.cascade.launcher

import android.os.SystemClock
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import android.os.Build
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleOut
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

/** Island-wide options that every stage reads. */
data class IslandMode(
    /** Swipe left/right to skip, double-tap to play/pause. */
    val swipe: Boolean = false,
    /** Hide the control buttons (only when [swipe] is on, so you can still control music). */
    val hideButtons: Boolean = false,
) {
    val showButtons: Boolean get() = !(swipe && hideButtons)
}

val LocalIslandMode = staticCompositionLocalOf { IslandMode() }

/** Lets the seek bar tell the island's swipe detector to stay out of a drag. */
class SwipeGate { var blocked = false }
private val LocalSwipeGate = staticCompositionLocalOf { SwipeGate() }

/** The island's surface colour (black, tinted or see-through glass), for contrast decisions. */
private val LocalIslandSurface = staticCompositionLocalOf { Color.Black }

/** Sample island used while positioning it with no music playing. */
val PreviewMedia = MediaInfo(
    title = "Dynamic Island preview",
    artist = "Use the arrows to move me",
    art = null,
    artColors = emptyList(),
    playing = true,
    durationMs = 200_000,
    positionMs = 60_000,
    positionUpdatedAt = 0,
    speed = 0f,
    packageName = "",
    appLabel = "Cascade",
)

/** The island's three stages. Tap moves MINI → PILL → EXPANDED. */
enum class IslandStage(val label: String) { MINI("Small"), PILL("Medium"), EXPANDED("Large") }

/** Size of one stage: width/height in dp and a content scale (1.0 = normal). */
data class StageSize(val width: Dp, val height: Dp?, val scale: Float)

/**
 * The Dynamic Island, drawn inside the launcher's home screen only.
 *  - MINI: just the round album cover + visualizer.
 *  - PILL: cover, scrolling song title, visualizer (clock width by default).
 *  - EXPANDED: the full card with progress and controls.
 */
@Composable
fun HomeIsland(
    info: MediaInfo,
    style: VisualizerStyle,
    stage: IslandStage,
    mini: StageSize,
    pill: StageSize,
    expanded: StageSize,
    pillStyle: PillStyle,
    miniFeatures: Set<MiniFeature>,
    expFeatures: Set<ExpFeature>,
    /** Inside border thickness per stage (0 = none), in the order Small, Medium, Large. */
    borders: List<Dp>,
    font: FontFamily,
    autoShrink: Boolean,
    onStageChange: (IslandStage) -> Unit,
    look: IslandLook = IslandLook.BLACK,
    mode: IslandMode = IslandMode(),
    onOpenPlayer: () -> Unit,
) {
    val accent = LocalAccent.current
    val palette = remember(style, info.artColors, accent) { visualizerPalette(style, info.artColors, accent) }

    // Shrink back one stage at a time after a few quiet seconds: Large → Medium → Small.
    var touchedAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(stage, touchedAt, autoShrink) {
        if (autoShrink && stage != IslandStage.MINI) {
            delay(if (stage == IslandStage.EXPANDED) 7000 else 6000)
            onStageChange(if (stage == IslandStage.EXPANDED) IslandStage.PILL else IslandStage.MINI)
        }
    }

    // One black shape that smoothly morphs between the stages (size + corner radius spring
    // together), while the content inside cross-fades. No popping, no hard cuts.
    val morph = spring<Float>(dampingRatio = 0.82f, stiffness = 300f)
    val cornerTarget = when (stage) {
        IslandStage.MINI -> (mini.height ?: 36.dp) / 2
        IslandStage.PILL -> pillHeightFor(pill, pillStyle) / 2
        IslandStage.EXPANDED -> (30 * expanded.scale).dp
    }
    val corner by animateDpAsState(cornerTarget, spring(dampingRatio = 0.82f, stiffness = 300f), label = "corner")
    val borderWidth by animateDpAsState(
        borders.getOrElse(stage.ordinal) { 0.dp },
        spring(dampingRatio = 0.82f, stiffness = 300f),
        label = "border",
    )

    // Island surface: solid black, a near-black tint of the song's colour, or see-through glass.
    val songColor = info.artColors.firstOrNull()?.let { Color(it) } ?: palette.first()
    val surface by androidx.compose.animation.animateColorAsState(
        when (look) {
            IslandLook.BLACK -> Color.Black
            IslandLook.TINTED -> lerp(Color.Black, songColor, 0.17f)
            IslandLook.GLASS -> Color.Black.copy(alpha = 0.42f)
        },
        tween(600),
        label = "surface",
    )

    val gate = remember { SwipeGate() }
    Box(
        Modifier
            .clip(RoundedCornerShape(corner))
            .background(surface)
            // Swipe left = next song, swipe right = previous (when turned on).
            .then(
                if (mode.swipe) Modifier.pointerInput(Unit) {
                    islandSwipes(gate) { forward ->
                        touchedAt = SystemClock.elapsedRealtime()
                        if (forward) NowPlaying.next() else NowPlaying.previous()
                    }
                } else Modifier,
            )
            .then(
                if (look == IslandLook.GLASS) Modifier.drawWithContent {
                    drawContent()
                    // Hairline light rim, like the edge of a glass pane.
                    val rim = 1.dp.toPx()
                    val r = (corner.toPx() - rim / 2).coerceAtLeast(0f)
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.08f))),
                        topLeft = Offset(rim / 2, rim / 2),
                        size = Size(size.width - rim, size.height - rim),
                        cornerRadius = CornerRadius(r, r),
                        style = Stroke(width = rim),
                    )
                } else Modifier,
            )
            // Black border drawn ON TOP of the content and entirely INSIDE the shape's edge,
            // so the island keeps exactly the same size with or without it.
            .drawWithContent {
                drawContent()
                val bw = borderWidth.toPx()
                if (bw > 0.5f) {
                    val r = (corner.toPx() - bw / 2).coerceAtLeast(0f)
                    drawRoundRect(
                        color = Color.Black,
                        topLeft = Offset(bw / 2, bw / 2),
                        size = Size(size.width - bw, size.height - bw),
                        cornerRadius = CornerRadius(r, r),
                        style = Stroke(width = bw),
                    )
                }
            },
    ) {
      CompositionLocalProvider(LocalIslandMode provides mode, LocalSwipeGate provides gate, LocalIslandSurface provides surface) {
        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                val growing = targetState.ordinal > initialState.ordinal
                val enter = fadeIn(tween(if (growing) 220 else 260, delayMillis = if (growing) 80 else 120)) +
                    scaleIn(morph, initialScale = if (growing) 0.94f else 1.04f)
                val exit = fadeOut(tween(if (growing) 90 else 140)) +
                    scaleOut(morph, targetScale = if (growing) 1.04f else 0.96f)
                (enter togetherWith exit).using(
                    SizeTransform(clip = true) { _, _ -> spring(dampingRatio = 0.82f, stiffness = 300f) },
                )
            },
            contentAlignment = Alignment.TopCenter,
            label = "island",
        ) { st ->
            when (st) {
                IslandStage.MINI -> MiniIsland(info, palette, mini, miniFeatures, { onStageChange(IslandStage.PILL) }, onOpenPlayer)
                IslandStage.PILL -> PillIsland(info, palette, pill, pillStyle, font, { onStageChange(IslandStage.EXPANDED) }, onOpenPlayer) {
                    touchedAt = SystemClock.elapsedRealtime()
                }
                IslandStage.EXPANDED -> ExpandedIsland(info, palette, expanded, expFeatures, font, onOpenPlayer) {
                    touchedAt = SystemClock.elapsedRealtime()
                }
            }
        }
      }
    }
}

/**
 * Horizontal swipe detector for the island. It looks at touches before the island's buttons do,
 * and only takes over (cancelling their tap) once the finger has clearly moved sideways.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.islandSwipes(
    gate: SwipeGate,
    onSwipe: (forward: Boolean) -> Unit,
) {
    val trigger = 36.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        gate.blocked = false
        var dx = 0f
        var dy = 0f
        var dragging = false
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            val c = e.changes.firstOrNull { it.id == down.id } ?: break
            if (gate.blocked) break                     // the seek bar has this drag
            if (!c.pressed) {
                if (dragging) c.consume()
                break
            }
            val d = c.positionChange()
            dx += d.x
            dy += d.y
            if (!dragging && abs(dx) > viewConfiguration.touchSlop && abs(dx) > abs(dy) * 1.4f) dragging = true
            if (dragging) c.consume()
        }
        if (dragging && abs(dx) > trigger) onSwipe(dx < 0)
    }
}

/** Double-tap = play/pause, when swipe controls are on (otherwise no double-tap delay). */
@Composable
private fun doubleTapPlayPause(onInteract: () -> Unit = {}): ((Offset) -> Unit)? =
    if (LocalIslandMode.current.swipe) { _ -> onInteract(); NowPlaying.playPause() } else null

private fun glowBrush(palette: List<Color>, vertical: Boolean = false): Brush {
    val colors = (if (palette.size > 1) palette else palette + palette).map { it.copy(alpha = 0.32f) }
    return if (vertical) Brush.verticalGradient(colors + Color.Transparent) else Brush.horizontalGradient(colors)
}

@Composable
private fun MiniIsland(
    info: MediaInfo,
    palette: List<Color>,
    size: StageSize,
    features: Set<MiniFeature>,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val k = size.scale
    val h = size.height ?: 36.dp
    val ring = MiniFeature.RING in features
    val pos = rememberPositionMs(info, ring)
    val fraction = if (info.durationMs > 0) (pos.toFloat() / info.durationMs).coerceIn(0f, 1f) else 0f
    val mode = LocalIslandMode.current
    val dbl = doubleTapPlayPause()
    Row(
        Modifier
            .widthIn(min = size.width)
            .height(h)
            .then(if (MiniFeature.GLOW in features) Modifier.background(glowBrush(palette)) else Modifier)
            .pointerInput(mode.swipe) { detectTapGestures(onTap = { onTap() }, onDoubleTap = dbl, onLongPress = { onLongPress() }) }
            .padding(horizontal = (h.value * 0.2f).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MiniCover(
            info, palette, (24 * k).dp,
            square = MiniFeature.SQUARE_COVER in features,
            spin = MiniFeature.SPIN in features,
            ring = ring,
            fraction = fraction,
        )
        if (MiniFeature.PLAY_BUTTON in features && mode.showButtons) {
            Spacer(Modifier.width((6 * k).dp))
            ControlButton(size = (h.value * 0.8f).coerceAtLeast(24f).dp, onClick = { NowPlaying.playPause() }) {
                if (info.playing) PauseIcon((14 * k).dp) else PlayIcon((14 * k).dp)
            }
            Spacer(Modifier.width((6 * k).dp))
        }
        if (MiniFeature.HIDE_VIZ !in features) {
            val wide = MiniFeature.WIDE_VIZ in features
            Visualizer(
                info.playing, palette, bars = if (wide) 9 else 5,
                modifier = Modifier.size(width = ((if (wide) 46 else 26) * k).dp, height = (17 * k).dp),
                dots = MiniFeature.DOTS in features,
            )
        }
    }
}

/** Album cover for the Small stage: optional square shape, vinyl-style spin and progress ring. */
@Composable
private fun MiniCover(
    info: MediaInfo,
    palette: List<Color>,
    cover: Dp,
    square: Boolean,
    spin: Boolean,
    ring: Boolean,
    fraction: Float,
) {
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(spin, info.playing) {
        if (spin && info.playing) {
            var last = 0L
            while (true) {
                withFrameNanos { t ->
                    if (last != 0L) angle = (angle + (t - last) / 1_000_000_000f * 40f) % 360f
                    last = t
                }
            }
        }
    }
    val shape = if (square) RoundedCornerShape(cover * 0.25f) else CircleShape
    val outer = if (ring) cover + 7.dp else cover
    Box(Modifier.size(outer), contentAlignment = Alignment.Center) {
        if (ring) {
            val brush = Brush.sweepGradient(if (palette.size > 1) palette + palette.first() else palette + palette)
            Canvas(Modifier.matchParentSize()) {
                val stroke = 2.2.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                drawArc(Color.White.copy(alpha = 0.15f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(brush, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Box(Modifier.graphicsLayer { rotationZ = angle }) { AlbumArt(info, cover, shape) }
    }
}

/**
 * Dynamic glow: the album colours slowly slide across the pill and the glow gently breathes.
 * It moves only while music plays and freezes in place when paused (no wasted animation).
 */
@Composable
private fun Modifier.dynamicGlow(colors: List<Color>, playing: Boolean): Modifier {
    var t by remember { mutableFloatStateOf(0f) }      // seconds of "playing" time
    LaunchedEffect(playing) {
        if (playing) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    if (last != 0L) t += (now - last) / 1_000_000_000f
                    last = now
                }
            }
        }
    }
    val loop = colors + colors.first()
    return this.drawBehind {
        val w = size.width
        val shift = (t / 6f) % 1f                      // one full colour sweep every 6 s
        val breathe = 0.30f + 0.10f * kotlin.math.sin(t * 2f * Math.PI.toFloat() / 2.4f)
        drawRect(
            brush = Brush.horizontalGradient(
                loop,
                startX = -w + shift * 2f * w,
                endX = shift * 2f * w + w,
                tileMode = androidx.compose.ui.graphics.TileMode.Mirror,
            ),
            alpha = breathe,
        )
    }
}

/** Height the Medium pill really uses (two lines of text need a bit more room). */
fun pillHeightFor(size: StageSize, style: PillStyle): Dp {
    val base = size.height ?: 36.dp
    return if (style.twoLines) maxOf(base, (46 * size.scale).dp) else base
}

/** Song position that ticks every half second, only while something on screen needs it. */
@Composable
private fun rememberPositionMs(info: MediaInfo, needed: Boolean): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(info.playing, info.positionUpdatedAt, needed) {
        while (needed && info.playing) {
            now = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    return info.currentPosition(now)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PillIsland(
    info: MediaInfo,
    palette: List<Color>,
    size: StageSize,
    style: PillStyle,
    font: FontFamily,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onInteract: () -> Unit,
) {
    val k = size.scale
    val h = pillHeightFor(size, style)
    val pos = rememberPositionMs(info, style.progress || style.time)
    val dur = info.durationMs
    val glow = if (palette.size > 1) palette else palette + palette
    val buttonSize = (minOf(h.value, 40f * k) * 0.82f).coerceAtLeast(24f).dp
    val mode = LocalIslandMode.current
    val dbl = doubleTapPlayPause(onInteract)
    val bmp = remember(info.art) { info.art?.asImageBitmap() }

    Box(
        Modifier
            // 7) Fit to content, or a fixed width (the clock's, by default).
            .then(if (style.fit) Modifier.widthIn(max = size.width.coerceAtLeast(160.dp)) else Modifier.width(size.width))
            .height(h)
            // 6) Album-colour glow: static gradient, or a dynamic one that flows and breathes.
            .then(
                when {
                    style.glowDynamic -> Modifier.dynamicGlow(glow, info.playing)
                    style.glow -> Modifier.background(Brush.horizontalGradient(glow.map { it.copy(alpha = 0.32f) }))
                    else -> Modifier
                },
            )
            .pointerInput(mode.swipe) { detectTapGestures(onTap = { onTap() }, onDoubleTap = dbl, onLongPress = { onLongPress() }) },
    ) {
        // Cover wash: the album art, heavily blurred and darkened, fills the pill.
        if (style.wash && bmp != null) {
            Image(
                bmp, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(18.dp) else Modifier)
                    .graphicsLayer { alpha = if (Build.VERSION.SDK_INT >= 31) 0.75f else 0.4f },
            )
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.42f)))
        }
        Row(
            Modifier
                .then(if (style.fit) Modifier else Modifier.fillMaxWidth())
                .height(h)
                .padding(horizontal = (h.value * 0.2f).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlbumArt(info, ((if (style.twoLines) 30 else 24) * k).dp, CircleShape)

            // 2) Song (and artist on a second line).
            Column(
                Modifier
                    .then(if (style.fit) Modifier.widthIn(max = (140 * k).dp) else Modifier.weight(1f))
                    .padding(horizontal = (10 * k).dp),
            ) {
                Text(
                    info.title,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = (12 * k).sp,
                    lineHeight = (14 * k).sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1500),
                )
                if (style.twoLines && info.artist.isNotBlank()) {
                    Text(
                        info.artist,
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = (10.5f * k).sp,
                        lineHeight = (12 * k).sp,
                        fontFamily = font,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2500),
                    )
                }
            }

            // 4) Time left.
            if (style.time && dur > 0) {
                Text(
                    "-" + fmt(dur - pos),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = (10.5f * k).sp,
                    fontFamily = font,
                    maxLines = 1,
                    modifier = Modifier.padding(end = (4 * k).dp),
                )
            }

            // 1) Previous / play-pause / next (taps here never expand the island).
            if (mode.showButtons) {
                if (style.skip) {
                    ControlButton(size = buttonSize, onClick = { onInteract(); NowPlaying.previous() }) { PrevIcon((12 * k).dp) }
                }
                ControlButton(size = buttonSize, onClick = { onInteract(); NowPlaying.playPause() }) {
                    if (info.playing) PauseIcon((15 * k).dp) else PlayIcon((15 * k).dp)
                }
                if (style.skip) {
                    ControlButton(size = buttonSize, onClick = { onInteract(); NowPlaying.next() }) { NextIcon((12 * k).dp) }
                }
            }
            Spacer(Modifier.width((4 * k).dp))

            // 5) Wide visualizer, or the normal small one.
            if (style.wideViz) {
                Visualizer(
                    info.playing, palette, bars = 14,
                    modifier = if (style.fit) Modifier.size(width = (72 * k).dp, height = (18 * k).dp)
                    else Modifier.weight(0.8f).height((18 * k).dp),
                    dots = style.dots,
                )
            } else {
                Visualizer(info.playing, palette, bars = 5, modifier = Modifier.size(width = (28 * k).dp, height = (18 * k).dp), dots = style.dots)
            }
            Spacer(Modifier.width(4.dp))
        }

        // 3) Thin progress line along the bottom edge.
        if (style.progress && dur > 0) {
            val fraction = (pos.toFloat() / dur).coerceIn(0f, 1f)
            val brush = Brush.horizontalGradient(glow)
            Canvas(
                Modifier
                    .matchParentSize()
                    .padding(horizontal = (h.value * 0.45f).dp, vertical = 2.dp),
            ) {
                val lh = 2.5.dp.toPx()
                val y = this.size.height - lh
                val w = this.size.width
                drawRoundRect(Color.White.copy(alpha = 0.15f), Offset(0f, y), Size(w, lh), CornerRadius(lh / 2))
                drawRoundRect(brush, Offset(0f, y), Size(w * fraction, lh), CornerRadius(lh / 2))
            }
        }
    }
}

@Composable
private fun ExpandedIsland(
    info: MediaInfo,
    palette: List<Color>,
    size: StageSize,
    f: Set<ExpFeature>,
    font: FontFamily,
    onOpenPlayer: () -> Unit,
    onInteract: () -> Unit,
) {
    val k = size.scale
    val poster = ExpFeature.POSTER in f
    val bmp = remember(info.art) { info.art?.asImageBitmap() }
    val mode = LocalIslandMode.current
    val dots = ExpFeature.DOTS in f
    val dbl = doubleTapPlayPause(onInteract)
    // With the cover as background, work out what colour the card really is behind the text and
    // pick text colours that stand out from it (bright covers get a deeper shade and dark text if
    // needed; accent text is lightened or darkened until it reads clearly).
    val artBg = ExpFeature.ART_BACKGROUND in f && bmp != null
    val artAvg = remember(info.art, info.artColors) { averageArtColor(info) }
    val contrastOn = ExpFeature.CONTRAST in f
    // Contrast also deepens the shade over bright covers; otherwise the cover keeps its usual shade.
    val shade = if (artBg) (if (contrastOn) 0.45f + 0.2f * artAvg.luminance() else 0.45f) else 0f
    val cardBg = estimateCardBackground(LocalIslandSurface.current, if (artBg) artAvg else null, shade)
    val accentText = ExpFeature.ACCENT_TEXT in f
    // Contrast: every text colour is checked against what's really behind it (the cover, when
    // it's the background) and made light or dark enough to read. With Accent text too, the
    // accent keeps its hue and only gets lighter or darker as needed.
    val autoText = contrastOn
    val textColor = rememberCardTextColor(info, palette, accentText, if (autoText) cardBg else null)
    val appColor = when {
        accentText -> textColor.copy(alpha = 0.8f)
        autoText -> readableOn(palette.first(), cardBg)
        else -> null
    }
    val subAlpha = if (autoText) 0.8f else 0.6f
    val shadow = if (autoText) androidx.compose.ui.graphics.Shadow(
        (if (cardBg.luminance() > 0.45f) Color.White else Color.Black).copy(alpha = 0.5f), Offset(0f, 1f), 6f,
    ) else null

    Box(
        Modifier
            .width(size.width)
            .then(if (size.height != null) Modifier.height(size.height) else Modifier),
    ) {
        // Cover as a soft, blurred background.
        if (ExpFeature.ART_BACKGROUND in f && bmp != null) {
            Image(
                bmp, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(26.dp) else Modifier)
                    .graphicsLayer { alpha = 0.6f },
            )
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = shade)))
        }
        if (ExpFeature.GLOW in f) Box(Modifier.matchParentSize().background(glowBrush(palette, vertical = true)))

        if (ExpFeature.MINIMAL in f) {
            MinimalCard(info, palette, size, f, font, onOpenPlayer, onInteract)
        } else Column(
            Modifier
                .fillMaxWidth()
                .then(if (size.height != null) Modifier.fillMaxHeight() else Modifier)
                .pointerInput(mode.swipe) { detectTapGestures(onTap = { onOpenPlayer() }, onDoubleTap = dbl) }
                .padding(start = (16 * k).dp, end = (16 * k).dp, top = (16 * k).dp, bottom = (10 * k).dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            if (poster) {
                // Big cover on top, text centred underneath.
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    AlbumArt(info, (128 * k).dp, RoundedCornerShape((20 * k).dp))
                    Spacer(Modifier.height((12 * k).dp))
                    Text(
                        info.title, color = textColor, fontSize = (17 * k).sp, fontFamily = font,
                        style = androidx.compose.ui.text.TextStyle(shadow = shadow),
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        info.artist, color = textColor.copy(alpha = subAlpha), fontSize = (14 * k).sp, fontFamily = font,
                        style = androidx.compose.ui.text.TextStyle(shadow = shadow),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (ExpFeature.APP_NAME in f) {
                        AppLabel(info, palette, k, font, appColor)
                    }
                    if (ExpFeature.VISUALIZER in f || dots) {
                        Spacer(Modifier.height((8 * k).dp))
                        Visualizer(info.playing, palette, bars = 7, modifier = Modifier.size(width = (42 * k).dp, height = (22 * k).dp), dots = dots)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AlbumArt(info, (58 * k).dp, RoundedCornerShape((14 * k).dp))
                    Spacer(Modifier.width((14 * k).dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            info.title, color = textColor, fontSize = (16 * k).sp, fontFamily = font,
                            style = androidx.compose.ui.text.TextStyle(shadow = shadow), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            info.artist, color = textColor.copy(alpha = subAlpha), fontSize = (14 * k).sp, fontFamily = font,
                        style = androidx.compose.ui.text.TextStyle(shadow = shadow),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (ExpFeature.APP_NAME in f) {
                            AppLabel(info, palette, k, font, appColor)
                        }
                    }
                    if (ExpFeature.VISUALIZER in f || dots) {
                        Visualizer(info.playing, palette, bars = 7, modifier = Modifier.size(width = (42 * k).dp, height = (28 * k).dp), dots = dots)
                    }
                }
            }

            Column {
                if (ExpFeature.WIDE_VIZ in f) {
                    Spacer(Modifier.height((12 * k).dp))
                    Visualizer(info.playing, palette, bars = 24, modifier = Modifier.fillMaxWidth().height((30 * k).dp), dots = dots)
                }
                Spacer(Modifier.height((14 * k).dp))
                Progress(info, palette, showTimes = ExpFeature.TIMES in f, seekable = ExpFeature.SEEK in f, onInteract = onInteract, font = font)
                if (mode.showButtons) Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ControlButton(size = (46 * k).dp, onClick = { onInteract(); NowPlaying.previous() }) { PrevIcon((22 * k).dp) }
                    PlayPauseButton(
                        info, (56 * k).dp, (26 * k).dp,
                        fill = if (ExpFeature.ACCENT_PLAY in f) songAccent(info, palette) else null,
                    ) { onInteract(); NowPlaying.playPause() }
                    ControlButton(size = (46 * k).dp, onClick = { onInteract(); NowPlaying.next() }) { NextIcon((22 * k).dp) }
                } else Spacer(Modifier.height((6 * k).dp))
            }
        }
    }
}

/** The music app's name, in the song's colour. */
@Composable
private fun AppLabel(info: MediaInfo, palette: List<Color>, k: Float, font: FontFamily, color: Color? = null) {
    Text(
        info.appLabel, color = color ?: palette.first().copy(alpha = 0.95f), fontSize = (11 * k).sp,
        fontFamily = font, fontWeight = FontWeight.Medium, maxLines = 1,
    )
}

// ------------------------------------------------------------------ visualizer colors

fun visualizerPalette(style: VisualizerStyle, artColors: List<Int>, accent: Color): List<Color> = when (style) {
    VisualizerStyle.ALBUM -> artColors.map { Color(it) }.ifEmpty { hexes(0xFF5EF7, 0x7B61FF, 0x2DE2E6, 0xFFD166) }
    VisualizerStyle.RAINBOW -> (0 until 7).map { Color.hsv(it * 360f / 7f, 0.85f, 1f) }
    VisualizerStyle.NEON -> hexes(0xFF2BD6, 0x00F0FF, 0x8A2BFF, 0x39FF14)
    VisualizerStyle.SUNSET -> hexes(0xFF5E62, 0xFF9966, 0xFFC371, 0xC33764)
    VisualizerStyle.OCEAN -> hexes(0x00C6FF, 0x0072FF, 0x00F5A0, 0x3A7BD5)
    VisualizerStyle.AURORA -> hexes(0x00FFA3, 0x03E1FF, 0xDC1FFF, 0x7CFFCB)
    VisualizerStyle.FIRE -> hexes(0xFF1E00, 0xFF7A00, 0xFFD000, 0xFF3D00)
    VisualizerStyle.CANDY -> hexes(0xFF6EC7, 0xFFB3DE, 0x9D8CFF, 0x6EE7FF)
    VisualizerStyle.ACCENT -> listOf(accent, lerp(accent, Color.White, 0.45f), lerp(accent, Color.Black, 0.25f))
    VisualizerStyle.WHITE -> listOf(Color.White)
}

private fun hexes(vararg rgb: Int) = rgb.map { Color(0xFF000000.toInt() or it) }

/** Little LED-meter simulation behind the dot-matrix visualizer. */
private class DotEq(val bars: Int) {
    val level = FloatArray(bars) { 0.3f }
    val peak = FloatArray(bars) { 0.3f }
    private val target = FloatArray(bars) { 0.3f }
    private val next = LongArray(bars)
    private var beatAt = 0L
    private val rnd = java.util.Random()

    fun step(now: Long, dt: Float) {
        // A soft "kick" roughly every 0.45 s lifts every column together, so it pulses like music.
        val beat = now >= beatAt
        if (beat) beatAt = now + 400_000_000L + rnd.nextInt(120_000_000)
        for (i in 0 until bars) {
            if (beat || now >= next[i]) {
                val v = rnd.nextFloat()
                target[i] = if (beat) 0.55f + 0.45f * v else 0.05f + 0.75f * v * v
                next[i] = now + 70_000_000L + rnd.nextInt(110_000_000)
            }
            // Fast attack up to the target, then fall under gravity.
            if (target[i] > level[i]) {
                level[i] += (target[i] - level[i]) * (1f - kotlin.math.exp(-dt * 38f))
            } else {
                level[i] = (level[i] - dt * 3.4f).coerceAtLeast(target[i])
            }
            if (level[i] >= peak[i]) peak[i] = level[i] else peak[i] = (peak[i] - dt * 1.1f).coerceAtLeast(level[i])
        }
    }
}

private val PausedHeights = floatArrayOf(0.35f, 0.6f, 0.45f, 0.75f, 0.4f, 0.55f, 0.3f)

/** Smoothly sample a looping palette at position [t] (any float; wraps around). */
private fun sample(palette: List<Color>, t: Float): Color {
    if (palette.size == 1) return palette[0]
    val x = ((t % 1f) + 1f) % 1f * palette.size
    val i = x.toInt() % palette.size
    return lerp(palette[i], palette[(i + 1) % palette.size], x - x.toInt())
}

/**
 * Colorful music visualizer: bounces while playing and its colors keep flowing through the
 * chosen palette, so the island never looks the same twice.
 */
@Composable
fun Visualizer(playing: Boolean, palette: List<Color>, bars: Int, modifier: Modifier, dots: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "viz")
    val heights = (0 until bars).map { i ->
        transition.animateFloat(
            initialValue = 0.22f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(durationMillis = 320 + (i * 137) % 260, delayMillis = (i * 70) % 210),
                RepeatMode.Reverse,
            ),
            label = "bar$i",
        )
    }
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing)),
        label = "phase",
    )

    // Dot matrix runs its own equaliser: every column jumps to a new level many times a second
    // (fast attack), then drops back under "gravity", with a peak dot that hangs and falls
    // slowly — the snappy feel of a hardware LED meter rather than a slow sine wave.
    val eq = remember(bars) { DotEq(bars) }
    var eqFrame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(dots, playing, bars) {
        if (!dots || !playing) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last != 0L) eq.step(t, ((t - last) / 1_000_000_000f).coerceAtMost(0.05f))
                last = t
                eqFrame = t
            }
        }
    }

    Canvas(modifier) {
        if (dots) {
            eqFrame // redraw every frame while it runs
            // Dot matrix: each column is the same smooth bar as the normal visualizer, just drawn
            // as dots. The bar's continuous height decides how far out the dots reach; the outer
            // dots fade and grow in gradually instead of popping on and off, so it moves as
            // smoothly as the bars do. Same colours, top to bottom.
            val pitch = size.width / bars
            val vPitch = pitch * 0.78f
            var rows = (size.height / vPitch).toInt().coerceAtLeast(3)
            if (rows % 2 == 0) rows -= 1
            val maxHalf = rows / 2
            val r = pitch * 0.3f
            val mid = size.height / 2
            for (i in 0 until bars) {
                val f = if (playing) eq.level[i] else PausedHeights[i % PausedHeights.size]
                val reach = f * (maxHalf + 0.5f)
                val lit = (reach + 0.05f).toInt().coerceIn(0, maxHalf)   // crisp, LED-like steps
                val peak = if (playing) (eq.peak[i] * (maxHalf + 0.5f) + 0.05f).toInt().coerceIn(0, maxHalf) else 0
                val cx = pitch * i + pitch / 2
                val pos = i.toFloat() / bars * 0.6f + (if (playing) phase else 0f)
                val top = sample(palette, pos)
                val bottom = sample(palette, pos + 0.18f)
                val a = if (playing) 1f else 0.75f
                for (k in -maxHalf..maxHalf) {
                    val d = abs(k)
                    val t = (k + maxHalf) / (2f * maxHalf)
                    val c = lerp(top, bottom, t)
                    when {
                        d <= lit -> drawCircle(c.copy(alpha = a), r, Offset(cx, mid + k * vPitch))
                        d == peak -> drawCircle(c.copy(alpha = 0.55f), r * 0.85f, Offset(cx, mid + k * vPitch))
                    }
                }
            }
            return@Canvas
        }
        val barW = size.width / (bars * 2 - 1)
        for (i in 0 until bars) {
            // Paused: frozen bars (a fixed little skyline) with frozen colors.
            val f = if (playing) heights[i].value else PausedHeights[i % PausedHeights.size]
            val h = (size.height * f).coerceAtLeast(barW)
            val pos = i.toFloat() / bars * 0.6f + (if (playing) phase else 0f)
            val top = sample(palette, pos)
            val bottom = sample(palette, pos + 0.18f)
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(top, bottom), startY = (size.height - h) / 2, endY = (size.height + h) / 2),
                topLeft = Offset(i * barW * 2, (size.height - h) / 2),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2),
                alpha = if (playing) 1f else 0.75f,
            )
        }
    }
}

// ------------------------------------------------------------------ pieces

@Composable
private fun AlbumArt(info: MediaInfo, size: Dp, shape: Shape) {
    val bmp = remember(info.art) { info.art?.asImageBitmap() }
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(Color(0xFF2A2A2E)),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Text("♪", color = Color.White.copy(alpha = 0.7f), fontSize = (size.value * 0.5f).sp)
        }
    }
}

@Composable
private fun Progress(
    info: MediaInfo,
    palette: List<Color>,
    showTimes: Boolean = true,
    seekable: Boolean = false,
    onInteract: () -> Unit = {},
    font: FontFamily? = null,
) {
    val pos = rememberPositionMs(info, true)
    val dur = info.durationMs
    val fraction = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val shown = dragFraction ?: fraction
    val brush = if (palette.size > 1) Brush.horizontalGradient(palette) else Brush.horizontalGradient(listOf(palette[0], palette[0]))
    val shownPos = if (dur > 0) (shown * dur).toLong() else pos
    val gate = LocalSwipeGate.current
    val timeColor = Color.White.copy(alpha = 0.55f)

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showTimes) Text(fmt(shownPos), color = timeColor, fontSize = 11.sp, fontFamily = font)
        Canvas(
            Modifier
                .weight(1f)
                .height(if (seekable) 22.dp else 6.dp)
                .padding(horizontal = if (showTimes) 10.dp else 0.dp)
                .then(
                    if (seekable && dur > 0) Modifier.pointerInput(dur) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            gate.blocked = true          // keep the island's swipe-to-skip out of it
                            var f = (down.position.x / this.size.width).coerceIn(0f, 1f)
                            dragFraction = f
                            while (true) {
                                val e = awaitPointerEvent()
                                val c = e.changes.firstOrNull { it.id == down.id } ?: break
                                if (!c.pressed) break
                                f = (c.position.x / this.size.width).coerceIn(0f, 1f)
                                dragFraction = f
                                c.consume()
                            }
                            onInteract()
                            NowPlaying.seekTo((f * dur).toLong())
                            dragFraction = null
                        }
                    } else Modifier,
                ),
        ) {
            val h = 6.dp.toPx()
            val top = (this.size.height - h) / 2
            val w = this.size.width
            drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(0f, top), Size(w, h), CornerRadius(h / 2))
            drawRoundRect(brush, Offset(0f, top), Size(w * shown, h), CornerRadius(h / 2))
            if (seekable) drawCircle(Color.White, radius = if (dragFraction != null) 8.dp.toPx() else 5.dp.toPx(), center = Offset(w * shown, this.size.height / 2))
        }
        if (showTimes) {
            Text(if (dur > 0) "-" + fmt(dur - shownPos) else "", color = timeColor, fontSize = 11.sp, fontFamily = font)
        }
    }
}

private fun fmt(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
private fun ControlButton(size: Dp = 46.dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun PlayIcon(dim: Dp = 26.dp, tint: Color = Color.White) = Canvas(Modifier.size(dim)) {
    val p = Path().apply {
        moveTo(size.width * 0.22f, size.height * 0.12f)
        lineTo(size.width * 0.9f, size.height * 0.5f)
        lineTo(size.width * 0.22f, size.height * 0.88f)
        close()
    }
    drawPath(p, tint)
}

@Composable
private fun PauseIcon(dim: Dp = 26.dp, tint: Color = Color.White) = Canvas(Modifier.size(dim)) {
    val w = size.width * 0.26f
    val r = CornerRadius(w / 3)
    drawRoundRect(tint, Offset(size.width * 0.16f, size.height * 0.12f), Size(w, size.height * 0.76f), r)
    drawRoundRect(tint, Offset(size.width * 0.58f, size.height * 0.12f), Size(w, size.height * 0.76f), r)
}

@Composable
private fun NextIcon(dim: Dp = 22.dp) = Canvas(Modifier.size(dim)) { drawSkip(forward = true) }

@Composable
private fun PrevIcon(dim: Dp = 22.dp) = Canvas(Modifier.size(dim)) { drawSkip(forward = false) }

private fun DrawScope.drawSkip(forward: Boolean) {
    val w = size.width
    val h = size.height
    fun x(v: Float) = if (forward) v * w else (1f - v) * w
    val tri = Path().apply {
        moveTo(x(0.08f), h * 0.12f)
        lineTo(x(0.72f), h * 0.5f)
        lineTo(x(0.08f), h * 0.88f)
        close()
    }
    drawPath(tri, Color.White)
    val barX = if (forward) w * 0.76f else w * (1f - 0.76f - 0.14f)
    drawRoundRect(Color.White, Offset(barX, h * 0.12f), Size(w * 0.14f, h * 0.76f), CornerRadius(w * 0.05f))
}

/** Title/artist/app colour on the Large card: white, or the play button's song accent. */
@Composable
private fun rememberCardTextColor(
    info: MediaInfo,
    palette: List<Color>,
    accent: Boolean,
    /** When set (Contrast), the text becomes whichever of white or near-black reads best on it. */
    autoOn: Color? = null,
): Color {
    val target = when {
        accent && autoOn != null -> readableOn(songAccent(info, palette), autoOn)
        accent -> songAccent(info, palette)
        autoOn != null -> {
            val dark = Color(0xFF111111)
            if (contrast(Color.White, autoOn) >= contrast(dark, autoOn)) Color.White else dark
        }
        else -> Color.White
    }
    val c by androidx.compose.animation.animateColorAsState(target, tween(500), label = "cardText")
    return c
}

/** WCAG contrast ratio between two opaque colours (1 = none, 21 = black on white). */
private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/** [c], lightened (on dark) or darkened (on light) just enough to read clearly on [bg]. */
private fun readableOn(c: Color, bg: Color, min: Float = 4.5f): Color {
    if (contrast(c, bg) >= min) return c
    val toward = if (bg.luminance() < 0.4f) Color.White else Color.Black
    var t = 0f
    var r = c
    while (contrast(r, bg) < min && t < 1f) {
        t += 0.08f
        r = lerp(c, toward, t.coerceAtMost(1f))
    }
    return r
}

/** Average colour of the album cover (tiny downscale), or its palette as a fallback. */
private fun averageArtColor(info: MediaInfo): Color {
    val fromBitmap = runCatching {
        val src = info.art ?: return@runCatching null
        val soft = if (Build.VERSION.SDK_INT >= 26 && src.config == android.graphics.Bitmap.Config.HARDWARE) {
            src.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
        } else src
        val tiny = android.graphics.Bitmap.createScaledBitmap(soft, 8, 8, true)
        var r = 0f; var g = 0f; var b = 0f
        for (y in 0 until 8) for (x in 0 until 8) {
            val p = tiny.getPixel(x, y)
            r += android.graphics.Color.red(p); g += android.graphics.Color.green(p); b += android.graphics.Color.blue(p)
        }
        Color(r / 64f / 255f, g / 64f / 255f, b / 64f / 255f)
    }.getOrNull()
    return fromBitmap ?: info.artColors.firstOrNull()?.let { Color(it) } ?: Color(0xFF333333)
}

/**
 * Roughly what colour the Large card is behind its text: the island surface (glass lets an
 * average wallpaper through), then the blurred cover at 60%, then the dark shade on top.
 */
private fun estimateCardBackground(surface: Color, art: Color?, shade: Float): Color {
    var bg = surface.copy(alpha = 1f).let { s -> lerp(Color(0xFF3A3A3A), s, surface.alpha) }
    if (art != null) bg = lerp(bg, art, 0.6f)
    return lerp(bg, Color.Black, shade)
}

/** The song's main colour, made bright enough to read as a solid button. */
private fun songAccent(info: MediaInfo, palette: List<Color>): Color {
    val c = info.artColors.firstOrNull()?.let { Color(it) } ?: palette.first()
    return lerp(c, Color.White, 0.08f)
}

/** Play/pause; with [fill] it becomes a solid colour disc with a dark icon (the one pop of colour). */
@Composable
private fun PlayPauseButton(info: MediaInfo, size: Dp, icon: Dp, fill: Color?, onClick: () -> Unit) {
    val bg by androidx.compose.animation.animateColorAsState(fill ?: Color.Transparent, tween(500), label = "playFill")
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (fill != null) Color(0xFF111111) else Color.White
        if (info.playing) PauseIcon(icon, tint) else PlayIcon(icon, tint)
    }
}

/**
 * Large stage, minimal: cover + title/artist, one thin white progress line, and three widely
 * spaced controls. No backgrounds or extra colour except an optional accent play button.
 */
@Composable
private fun MinimalCard(
    info: MediaInfo,
    palette: List<Color>,
    size: StageSize,
    f: Set<ExpFeature>,
    font: FontFamily,
    onOpenPlayer: () -> Unit,
    onInteract: () -> Unit,
) {
    val k = size.scale
    val showButtons = LocalIslandMode.current.showButtons
    val dbl = doubleTapPlayPause(onInteract)
    val textColor = rememberCardTextColor(info, palette, ExpFeature.ACCENT_TEXT in f)
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (size.height != null) Modifier.fillMaxHeight() else Modifier)
            .pointerInput(LocalIslandMode.current.swipe) { detectTapGestures(onTap = { onOpenPlayer() }, onDoubleTap = dbl) }
            .padding(start = (18 * k).dp, end = (18 * k).dp, top = (18 * k).dp, bottom = (12 * k).dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(info, (60 * k).dp, RoundedCornerShape((14 * k).dp))
            Spacer(Modifier.width((14 * k).dp))
            Column(Modifier.weight(1f)) {
                if (ExpFeature.APP_NAME in f) {
                    Text(
                        info.appLabel.uppercase(), color = textColor.copy(alpha = if (ExpFeature.ACCENT_TEXT in f) 0.7f else 0.4f), fontSize = (9.5f * k).sp,
                        fontFamily = font, fontWeight = FontWeight.Medium, letterSpacing = (1.2f * k).sp, maxLines = 1,
                    )
                    Spacer(Modifier.height((2 * k).dp))
                }
                Text(
                    info.title, color = textColor, fontSize = (16 * k).sp, fontFamily = font,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    info.artist, color = textColor.copy(alpha = 0.55f), fontSize = (13.5f * k).sp, fontFamily = font,
                    letterSpacing = (0.2f * k).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (ExpFeature.VISUALIZER in f || ExpFeature.DOTS in f) {
                Spacer(Modifier.width((8 * k).dp))
                Visualizer(info.playing, palette, bars = 7, modifier = Modifier.size(width = (42 * k).dp, height = (24 * k).dp), dots = ExpFeature.DOTS in f)
            }
        }
        Column {
            Spacer(Modifier.height((18 * k).dp))
            // A calm white line instead of the colourful bar.
            Progress(
                info, listOf(Color.White.copy(alpha = 0.85f)),
                showTimes = ExpFeature.TIMES in f, seekable = ExpFeature.SEEK in f,
                onInteract = onInteract, font = font,
            )
            Spacer(Modifier.height((6 * k).dp))
            if (showButtons) Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy((30 * k).dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlButton(size = (40 * k).dp, onClick = { onInteract(); NowPlaying.previous() }) { PrevIcon((17 * k).dp) }
                PlayPauseButton(
                    info, (50 * k).dp, (21 * k).dp,
                    fill = if (ExpFeature.ACCENT_PLAY in f) songAccent(info, palette) else null,
                ) { onInteract(); NowPlaying.playPause() }
                ControlButton(size = (40 * k).dp, onClick = { onInteract(); NowPlaying.next() }) { NextIcon((17 * k).dp) }
            }
        }
    }
}

/** Looks for the "open Spotify" button that sits in the island's place when nothing plays. */
enum class BadgeStyle(val label: String, val themed: Boolean = true) {
    CUTOUT("Cut-out"),
    OUTLINE("Outline"),
    GLASS("Glass"),
    ARCS("Arcs only"),
    RING("Ring cut-out"),
    SQUIRCLE("Rounded square"),
    NOTE("Music note"),
    PLAY("Play"),
    VINYL("Vinyl"),
    BARS("Equaliser"),
    SOLID("Solid", themed = false),
    ACCENT("Accent colour", themed = false),
    GREEN("Spotify green", themed = false),
    ORIGINAL("Original icon", themed = false),
}

private fun arcPath(w: Float, h: Float, i: Int, scale: Float = 1f): androidx.compose.ui.graphics.Path {
    val cx = w * 0.5f
    val cy = h * 0.5f
    val half = w * (0.285f - i * 0.05f) * scale
    val top = cy + (h * (0.355f + i * 0.15f) - cy) * scale
    val sag = h * (0.075f - i * 0.012f) * scale
    return androidx.compose.ui.graphics.Path().apply {
        moveTo(cx - half, top + sag * 0.55f)
        quadraticTo(cx + w * 0.02f * scale, top - sag * 0.95f, cx + half, top + sag * 1.25f)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArcs(
    color: Color,
    scale: Float = 1f,
    thick: Float = 1f,
    blend: androidx.compose.ui.graphics.BlendMode = androidx.compose.ui.graphics.BlendMode.SrcOver,
) {
    val w = size.width
    for (i in 0..2) {
        drawPath(
            arcPath(w, size.height, i, scale),
            color = color,
            style = Stroke(width = w * (0.085f - i * 0.012f) * scale * thick, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            blendMode = blend,
        )
    }
}

/**
 * The "open Spotify" button shown where the island sits when nothing is playing. Every style is
 * drawn in code. [color] is the clock colour (with the chosen see-through amount applied);
 * cut-out styles punch real holes so the wallpaper shows through.
 */
@Composable
fun SpotifyBadge(style: BadgeStyle, color: Color, accent: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(
        modifier.graphicsLayer {
            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
        },
    ) {
        val w = size.width
        val h = size.height
        val r = minOf(w, h) / 2f
        val c = Offset(w / 2f, h / 2f)
        val clear = androidx.compose.ui.graphics.BlendMode.Clear
        val round = androidx.compose.ui.graphics.StrokeCap.Round
        when (style) {
            BadgeStyle.CUTOUT -> {
                drawCircle(color, r)
                drawArcs(Color.Black, blend = clear)
            }
            BadgeStyle.SOLID -> {
                drawCircle(color.copy(alpha = 1f), r)
                drawArcs(Color.Black, blend = clear)
            }
            BadgeStyle.ACCENT -> {
                drawCircle(accent.copy(alpha = color.alpha.coerceAtLeast(0.5f)), r)
                drawArcs(Color.Black, blend = clear)
            }
            BadgeStyle.GREEN -> {
                drawCircle(Color(0xFF1ED760), r)
                drawArcs(Color(0xFF121212))
            }
            BadgeStyle.OUTLINE -> {
                drawCircle(color, r - w * 0.035f, style = Stroke(width = w * 0.07f))
                drawArcs(color, scale = 0.86f)
            }
            BadgeStyle.GLASS -> {
                drawCircle(Color.Black.copy(alpha = 0.38f), r)
                drawCircle(color.copy(alpha = color.alpha * 0.55f), r - w * 0.015f, style = Stroke(width = w * 0.03f))
                drawArcs(color)
            }
            BadgeStyle.ARCS -> drawArcs(color, scale = 1.25f, thick = 1.1f)
            BadgeStyle.RING -> {
                drawCircle(color, r)
                drawCircle(Color.Black, r * 0.84f, style = Stroke(width = w * 0.035f), blendMode = clear)
                drawArcs(Color.Black, scale = 0.86f, blend = clear)
            }
            BadgeStyle.SQUIRCLE -> {
                drawRoundRect(color, cornerRadius = CornerRadius(w * 0.3f, h * 0.3f))
                drawArcs(Color.Black, blend = clear)
            }
            BadgeStyle.NOTE -> {
                drawCircle(color, r)
                // Two beamed eighth notes.
                val headR = w * 0.085f
                val stemW = w * 0.055f
                val h1 = Offset(w * 0.36f, h * 0.67f)
                val h2 = Offset(w * 0.62f, h * 0.61f)
                drawOval(Color.Black, topLeft = Offset(h1.x - headR * 1.2f, h1.y - headR), size = Size(headR * 2.4f, headR * 2f), blendMode = clear)
                drawOval(Color.Black, topLeft = Offset(h2.x - headR * 1.2f, h2.y - headR), size = Size(headR * 2.4f, headR * 2f), blendMode = clear)
                drawRect(Color.Black, topLeft = Offset(h1.x + headR * 1.2f - stemW, h * 0.3f), size = Size(stemW, h1.y - h * 0.3f), blendMode = clear)
                drawRect(Color.Black, topLeft = Offset(h2.x + headR * 1.2f - stemW, h * 0.24f), size = Size(stemW, h2.y - h * 0.24f), blendMode = clear)
                val beam = androidx.compose.ui.graphics.Path().apply {
                    moveTo(h1.x + headR * 1.2f - stemW, h * 0.3f)
                    lineTo(h2.x + headR * 1.2f, h * 0.24f)
                    lineTo(h2.x + headR * 1.2f, h * 0.24f + w * 0.1f)
                    lineTo(h1.x + headR * 1.2f - stemW, h * 0.3f + w * 0.1f)
                    close()
                }
                drawPath(beam, Color.Black, blendMode = clear)
            }
            BadgeStyle.PLAY -> {
                drawCircle(color, r)
                val tri = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.4f, h * 0.3f)
                    lineTo(w * 0.72f, h * 0.5f)
                    lineTo(w * 0.4f, h * 0.7f)
                    close()
                }
                drawPath(tri, Color.Black, blendMode = clear)
                drawPath(tri, Color.Black, style = Stroke(width = w * 0.06f, join = androidx.compose.ui.graphics.StrokeJoin.Round), blendMode = clear)
            }
            BadgeStyle.VINYL -> {
                drawCircle(color, r)
                drawCircle(Color.Black, r * 0.8f, c, style = Stroke(width = w * 0.025f), blendMode = clear)
                drawCircle(Color.Black, r * 0.62f, c, style = Stroke(width = w * 0.025f), blendMode = clear)
                drawCircle(Color.Black, r * 0.36f, c, blendMode = clear)
                drawCircle(color, r * 0.1f, c)
            }
            BadgeStyle.BARS -> {
                drawCircle(color, r)
                val heights = listOf(0.22f, 0.4f, 0.3f, 0.46f, 0.26f)
                val bw = w * 0.075f
                val gap = w * 0.045f
                val total = heights.size * bw + (heights.size - 1) * gap
                heights.forEachIndexed { i, hh ->
                    val x = (w - total) / 2f + i * (bw + gap) + bw / 2f
                    drawLine(
                        Color.Black,
                        Offset(x, h / 2f - h * hh / 2f),
                        Offset(x, h / 2f + h * hh / 2f),
                        strokeWidth = bw,
                        cap = round,
                        blendMode = clear,
                    )
                }
            }
            BadgeStyle.ORIGINAL -> Unit // drawn by the caller with the real app icon
        }
    }
}
