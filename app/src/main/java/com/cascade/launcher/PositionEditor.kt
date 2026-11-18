package com.cascade.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Floating panel with arrow (+/-) buttons that moves the home apps, the clock or the
 * Dynamic Island to an exact spot. Changes are saved and shown live.
 */
@Composable
fun PositionEditor(act: MainActivity, target: EditTarget) {
    var panelAtTop by remember { mutableStateOf(false) }
    val accent = LocalAccent.current
    val (x, y) = act.currentOffset()

    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(12.dp),
        contentAlignment = if (panelAtTop) Alignment.TopCenter else Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF0141418))
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Target tabs + done
            Row(verticalAlignment = Alignment.CenterVertically) {
                EditTarget.entries.forEach { t ->
                    val selected = t == target
                    Text(
                        t.label,
                        color = if (selected) Color.Black else Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) accent else Color.White.copy(alpha = 0.1f))
                            .clickable { act.selectEditTarget(t) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Done",
                    color = accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { act.stopEditing() }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SmallTextButton("Reset") { act.resetPosition() }
                ArrowButton("←") { act.nudge(-STEP, 0) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ArrowButton("↑") { act.nudge(0, -STEP) }
                    Text(
                        "x $x · y $y",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    ArrowButton("↓") { act.nudge(0, STEP) }
                }
                ArrowButton("→") { act.nudge(STEP, 0) }
                SmallTextButton(if (panelAtTop) "Panel ↓" else "Panel ↑") { panelAtTop = !panelAtTop }
            }

            Text(
                "Tap to move ${STEP}dp · hold to move faster",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private const val STEP = 2

@Composable
private fun SmallTextButton(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.8f),
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    )
}

/** A round button that fires once on press and then repeats, faster and faster, while held. */
@Composable
private fun ArrowButton(label: String, onStep: () -> Unit) {
    val step by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }

    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (pressed) 0.28f else 0.12f))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    pressed = true
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    step()
                    val repeat = scope.launch {
                        delay(350)
                        var interval = 90L
                        while (true) {
                            step()
                            delay(interval)
                            interval = (interval - 6).coerceAtLeast(20)
                        }
                    }
                    waitForUpOrCancellation()
                    repeat.cancel()
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Darkness slider shown over the real home screen, so every drag shows exactly how dark the
 * wallpaper will be. Presets for quick picks; Done goes back to settings.
 */
@Composable
fun DarkenEditor(act: MainActivity) {
    val s = act.settings
    val accent = LocalAccent.current
    // Local value keeps the drag smooth; each change is applied (and saved) immediately.
    var value by remember { mutableStateOf(s.darkenAmount.toFloat()) }

    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF0141418))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Darken background", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    "${value.toInt()}%",
                    color = accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                "Drag and watch your home screen — that's the preview.",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
            )
            androidx.compose.material3.Slider(
                value = value,
                onValueChange = { v ->
                    value = v
                    act.updateSettings { it.copy(darkenEnabled = true, darkenAmount = v.toInt()) }
                },
                valueRange = 0f..90f,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                ),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf(0, 20, 40, 60, 80).forEach { p ->
                    val sel = value.toInt() == p
                    Text(
                        "$p%",
                        color = if (sel) Color.Black else Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (sel) accent else Color.White.copy(alpha = 0.1f))
                            .clickable {
                                value = p.toFloat()
                                act.updateSettings { it.copy(darkenEnabled = true, darkenAmount = p) }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Done",
                    color = accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { act.finishDarkenPreview() }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/**
 * Resize each island stage with the real island shown above as a live preview.
 * Small = cover + visualizer, Medium = with song title, Large = full card with controls.
 */
@Composable
fun IslandSizeEditor(act: MainActivity, stage: IslandStage) {
    val s = act.settings
    val accent = LocalAccent.current

    Box(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF0141418))
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IslandStage.entries.forEach { st ->
                    val sel = st == stage
                    Text(
                        st.label,
                        color = if (sel) Color.Black else Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (sel) accent else Color.White.copy(alpha = 0.1f))
                            .clickable { act.islandSizeStage = st }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Done",
                    color = accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { act.finishIslandSizing() }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
            Text(
                when (stage) {
                    IslandStage.MINI -> "Small: album cover + visualizer (what you see first)"
                    IslandStage.PILL -> "Medium: after one tap — adds the song title"
                    IslandStage.EXPANDED -> "Large: after two taps — full controls"
                },
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )

            when (stage) {
                IslandStage.MINI -> {
                    FeaturePicker(
                        presets = MiniPresets,
                        all = MiniFeature.entries,
                        current = s.miniFeatures,
                        label = { it.label },
                    ) { v -> act.updateSettings { it.copy(miniFeatures = v) } }
                    EditorSlider("Width", s.islandMiniWidth, 56..220, "dp") { v -> act.updateSettings { it.copy(islandMiniWidth = v) } }
                    EditorSlider("Height", s.islandMiniHeight, 26..64, "dp") { v -> act.updateSettings { it.copy(islandMiniHeight = v) } }
                    EditorSlider("Size", s.islandMiniScale, 60..170, "%") { v -> act.updateSettings { it.copy(islandMiniScale = v) } }
                }
                IslandStage.PILL -> {
                    PillLayoutPicker(act)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { act.updateSettings { it.copy(islandMatchClock = !it.islandMatchClock) } }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Match clock width", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        androidx.compose.material3.Switch(
                            checked = s.islandMatchClock,
                            onCheckedChange = { v -> act.updateSettings { it.copy(islandMatchClock = v) } },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedTrackColor = accent, checkedBorderColor = accent, checkedThumbColor = Color.White,
                            ),
                        )
                    }
                    EditorSlider(
                        if (s.pillFit) "Max width" else "Width", s.islandPillWidth, 120..420, "dp",
                        enabled = !s.islandMatchClock,
                    ) { v ->
                        act.updateSettings { it.copy(islandPillWidth = v) }
                    }
                    EditorSlider("Height", s.islandPillHeight, 26..64, "dp") { v -> act.updateSettings { it.copy(islandPillHeight = v) } }
                    EditorSlider("Size", s.islandPillScale, 60..170, "%") { v -> act.updateSettings { it.copy(islandPillScale = v) } }
                }
                IslandStage.EXPANDED -> {
                    FeaturePicker(
                        presets = ExpPresets,
                        all = ExpFeature.entries,
                        current = s.expFeatures,
                        label = { it.label },
                    ) { v -> act.updateSettings { it.copy(expFeatures = v) } }
                    EditorSlider("Width", s.islandExpWidth, 50..100, "% of screen") { v -> act.updateSettings { it.copy(islandExpWidth = v) } }
                    EditorSlider(
                        "Height",
                        s.islandExpHeight,
                        0..420,
                        "dp",
                        valueText = if (s.islandExpHeight < 120) "Auto" else null,
                    ) { v -> act.updateSettings { it.copy(islandExpHeight = if (v < 120) 0 else v) } }
                    EditorSlider("Size", s.islandExpScale, 70..150, "%") { v -> act.updateSettings { it.copy(islandExpScale = v) } }
                }
            }

            // Inside black border for this stage.
            val (borderOn, borderDp) = when (stage) {
                IslandStage.MINI -> s.islandMiniBorderOn to s.islandMiniBorder
                IslandStage.PILL -> s.islandPillBorderOn to s.islandPillBorder
                IslandStage.EXPANDED -> s.islandExpBorderOn to s.islandExpBorder
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clickable {
                        act.updateSettings {
                            when (stage) {
                                IslandStage.MINI -> it.copy(islandMiniBorderOn = !borderOn)
                                IslandStage.PILL -> it.copy(islandPillBorderOn = !borderOn)
                                IslandStage.EXPANDED -> it.copy(islandExpBorderOn = !borderOn)
                            }
                        }
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Black border", color = Color.White, fontSize = 14.sp)
                    Text(
                        "Drawn on the inside edge — the island's size doesn't change",
                        color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = borderOn,
                    onCheckedChange = { v ->
                        act.updateSettings {
                            when (stage) {
                                IslandStage.MINI -> it.copy(islandMiniBorderOn = v)
                                IslandStage.PILL -> it.copy(islandPillBorderOn = v)
                                IslandStage.EXPANDED -> it.copy(islandExpBorderOn = v)
                            }
                        }
                    },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedTrackColor = accent, checkedBorderColor = accent, checkedThumbColor = Color.White,
                    ),
                )
            }
            EditorSlider("Border thickness", borderDp, 1..14, "dp", enabled = borderOn) { v ->
                act.updateSettings {
                    when (stage) {
                        IslandStage.MINI -> it.copy(islandMiniBorder = v)
                        IslandStage.PILL -> it.copy(islandPillBorder = v)
                        IslandStage.EXPANDED -> it.copy(islandExpBorder = v)
                    }
                }
            }

            Text(
                "Reset this stage",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable {
                        val d = LauncherSettings()
                        act.updateSettings {
                            when (stage) {
                                IslandStage.MINI -> it.copy(islandMiniWidth = d.islandMiniWidth, islandMiniHeight = d.islandMiniHeight, islandMiniScale = d.islandMiniScale, miniFeatures = d.miniFeatures, islandMiniBorderOn = d.islandMiniBorderOn, islandMiniBorder = d.islandMiniBorder)
                                IslandStage.PILL -> it.copy(islandMatchClock = d.islandMatchClock, islandPillWidth = d.islandPillWidth, islandPillHeight = d.islandPillHeight, islandPillScale = d.islandPillScale, islandPillBorderOn = d.islandPillBorderOn, islandPillBorder = d.islandPillBorder)
                                IslandStage.EXPANDED -> it.copy(islandExpWidth = d.islandExpWidth, islandExpHeight = d.islandExpHeight, islandExpScale = d.islandExpScale, expFeatures = d.expFeatures, islandExpBorderOn = d.islandExpBorderOn, islandExpBorder = d.islandExpBorder)
                            }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun EditorSlider(
    label: String,
    value: Int,
    range: IntRange,
    unit: String,
    enabled: Boolean = true,
    valueText: String? = null,
    onChange: (Int) -> Unit,
) {
    val accent = LocalAccent.current
    var local by remember(value) { mutableStateOf(value.toFloat()) }
    val alpha = if (enabled) 1f else 0.4f
    Column(Modifier.padding(top = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White.copy(alpha = alpha), fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(
                valueText ?: "${local.toInt()} $unit",
                color = Color.White.copy(alpha = 0.6f * alpha),
                fontSize = 12.sp,
            )
        }
        androidx.compose.material3.Slider(
            value = local,
            onValueChange = { local = it; onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
            modifier = Modifier.height(32.dp),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.15f),
            ),
        )
    }
}


/** Medium-stage layout: try a preset, or switch each extra on/off yourself. */
@Composable
private fun PillLayoutPicker(act: MainActivity) {
    val s = act.settings
    val accent = LocalAccent.current
    val current = s.pillStyle
    Text("Layout presets", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        PillPresets.forEach { (name, style) ->
            val sel = style == current
            Text(
                name,
                color = if (sel) Color.Black else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) accent else Color.White.copy(alpha = 0.1f))
                    .clickable { act.updateSettings { it.withPillStyle(style) } }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
    if (PillPresets.none { it.second == current }) {
        Text("Custom mix", color = accent, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }

    Text("Or pick your own", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
    val toggles: List<Triple<String, Boolean, (Boolean) -> PillStyle>> = listOf(
        Triple("⏮ ⏭ buttons", current.skip) { v -> current.copy(skip = v) },
        Triple("Artist line", current.twoLines) { v -> current.copy(twoLines = v) },
        Triple("Progress line", current.progress) { v -> current.copy(progress = v) },
        Triple("Time left", current.time) { v -> current.copy(time = v) },
        Triple("Wide visualizer", current.wideViz) { v -> current.copy(wideViz = v) },
        // Static and dynamic glow are either/or: switching one on switches the other off.
        Triple("Glow: static", current.glow) { v -> current.copy(glow = v, glowDynamic = if (v) false else current.glowDynamic) },
        Triple("Glow: dynamic", current.glowDynamic) { v -> current.copy(glowDynamic = v, glow = if (v) false else current.glow) },
        Triple("Fit to content", current.fit) { v -> current.copy(fit = v) },
    )
    toggles.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth()) {
            pair.forEach { (label, on, make) ->
                Row(
                    Modifier
                        .weight(1f)
                        .padding(end = 6.dp, top = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (on) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
                        .clickable { act.updateSettings { it.withPillStyle(make(!on)) } }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (on) "✓" else "○", color = if (on) accent else Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = Color.White, fontSize = 13.sp, maxLines = 1)
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(6.dp))
}


/** Presets row + one switch per feature (used by the Small and Large tabs). */
@Composable
private fun <F> FeaturePicker(
    presets: List<Pair<String, Set<F>>>,
    all: List<F>,
    current: Set<F>,
    label: (F) -> String,
    onChange: (Set<F>) -> Unit,
) {
    val accent = LocalAccent.current
    Text("Layout presets", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        presets.forEach { (name, set) ->
            val sel = set == current
            Text(
                name,
                color = if (sel) Color.Black else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) accent else Color.White.copy(alpha = 0.1f))
                    .clickable { onChange(set) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
    if (presets.none { it.second == current }) {
        Text("Custom mix", color = accent, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
    Text("Or pick your own", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
    all.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth()) {
            pair.forEach { feat ->
                val on = feat in current
                Row(
                    Modifier
                        .weight(1f)
                        .padding(end = 6.dp, top = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (on) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
                        .clickable { onChange(if (on) current - feat else current + feat) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (on) "✓" else "○", color = if (on) accent else Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(label(feat), color = Color.White, fontSize = 13.sp, maxLines = 1)
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(6.dp))
}
