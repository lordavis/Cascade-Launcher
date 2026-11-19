package com.cascade.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private enum class SettingsPage(val title: String) {
    MAIN("Settings"),
    CLOCK("Clock & date"),
    HOME("Home screen"),
    HOME_APPS("Home apps"),
    APPEARANCE("Appearance"),
    DRAWER("App drawer"),
    HIDDEN("Hidden apps"),
    GESTURES("Gestures"),
    ISLAND("Dynamic Island"),
}

private val Bg = Color(0xFF0E0E11)
private val Card = Color(0xFF1A1A1F)
private val Muted = Color.White.copy(alpha = 0.55f)

@Composable
fun SettingsScreen(act: MainActivity, onClose: () -> Unit) {
    var page by rememberSaveable {
        mutableStateOf(
            act.settingsReturnPage?.let { name -> SettingsPage.entries.firstOrNull { it.name == name } }
                ?: SettingsPage.MAIN,
        )
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { act.settingsReturnPage = null }
    val back = { if (page == SettingsPage.MAIN) onClose() else page = parentOf(page) }
    BackHandler(onBack = back)

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = back),
                contentAlignment = Alignment.Center,
            ) { Text("←", color = Color.White, fontSize = 24.sp) }
            Spacer(Modifier.width(8.dp))
            Text(page.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
        ) {
            when (page) {
                SettingsPage.MAIN -> mainPage(act) { page = it }
                SettingsPage.CLOCK -> clockPage(act)
                SettingsPage.HOME -> homePage(act) { page = it }
                SettingsPage.HOME_APPS -> homeAppsPage(act)
                SettingsPage.APPEARANCE -> appearancePage(act)
                SettingsPage.DRAWER -> drawerPage(act) { page = it }
                SettingsPage.HIDDEN -> hiddenPage(act)
                SettingsPage.GESTURES -> gesturesPage(act)
                SettingsPage.ISLAND -> islandPage(act)
            }
        }
    }
}

private fun parentOf(page: SettingsPage) = when (page) {
    SettingsPage.HOME_APPS -> SettingsPage.HOME
    SettingsPage.HIDDEN -> SettingsPage.DRAWER
    else -> SettingsPage.MAIN
}

// =====================================================================================
// Pages
// =====================================================================================

private fun LazyListScope.mainPage(act: MainActivity, go: (SettingsPage) -> Unit) {
    item {
        NavRow("Clock & date", "iPhone-style looks, thickness, color, numerals, size") { go(SettingsPage.CLOCK) }
        NavRow("Home screen", "Home apps, icons and/or names, fonts, spacing, positions, hide bars") { go(SettingsPage.HOME) }
        NavRow("Appearance", "Accent color, darken background") { go(SettingsPage.APPEARANCE) }
        NavRow("App drawer", "Icons and/or names, A–Z bar, section letters, hidden apps") { go(SettingsPage.DRAWER) }
        NavRow("Gestures", "Swipe up, swipe down, double tap") { go(SettingsPage.GESTURES) }
        NavRow(
            "Dynamic Island",
            if (act.settings.islandEnabled) "On · shows what's playing at the top of the screen"
            else "Off · music pill above the clock",
        ) { go(SettingsPage.ISLAND) }

        Section("Launcher")
        ActionRow("Set as default launcher", "Choose Cascade as your Home app") { act.openDefaultHomeSettings() }
        ActionRow("Change wallpaper") { act.openWallpaperPicker() }
        ActionRow("Reload app list", "Use if a new app doesn't show up") { act.refresh() }

        var confirmReset by remember { mutableStateOf(false) }
        ActionRow("Reset settings", "Restore every option to its default", danger = true) { confirmReset = true }
        if (confirmReset) {
            AlertDialog(
                onDismissRequest = { confirmReset = false },
                title = { Text("Reset all settings?") },
                text = { Text("Your home apps stay as they are. Everything else returns to defaults.") },
                confirmButton = {
                    TextButton(onClick = { confirmReset = false; act.resetSettings() }) { Text("Reset") }
                },
                dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
                containerColor = Card,
                titleContentColor = Color.White,
                textContentColor = Muted,
            )
        }

        Section("About")
        InfoRow("Cascade Launcher", "Version ${act.versionName}")
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.clockPage(act: MainActivity) {
    val s = act.settings
    // The preview stays pinned at the top while you scroll, and redraws on every change.
    stickyHeader { Box(Modifier.background(Bg)) { Preview(act) } }
    item {
        Section("Style")
        ClockStyleGrid(act)

        Section("Fine-tune")
        SliderRow("Thickness", s.clockWeight, 100..900, "", enabled = s.showClock) { v ->
            act.updateSettings { it.copy(clockWeight = v) }
        }
        if (!s.clockFont.isVariable) {
            Text(
                "This style only has a few thicknesses, so the slider moves in steps.",
                color = Muted, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        SliderRow("Clock size", s.clockSize, 56..150, "sp", enabled = s.showClock) { v ->
            act.updateSettings { it.copy(clockSize = v) }
        }
        SliderRow("Date size", s.dateSize, 12..44, "sp", enabled = s.showDate) { v ->
            act.updateSettings { it.copy(dateSize = v) }
        }

        Section("Color")
        ClockColorPicker(s.clockColor) { v -> act.updateSettings { it.copy(clockColor = v) } }

        Section("Numbers")
        ChoiceRow("Numerals", s.clockNumerals, Numerals.entries, { it.label }) { v ->
            act.updateSettings { it.copy(clockNumerals = v) }
        }
        ChoiceRow("Time format", s.clockFormat, ClockFormat.entries, { it.label }) { v ->
            act.updateSettings { it.copy(clockFormat = v) }
        }

        Section("Show")
        SwitchRow("Clock", s.showClock) { v -> act.updateSettings { it.copy(showClock = v) } }
        SwitchRow("Date above the clock", s.showDate) { v -> act.updateSettings { it.copy(showDate = v) } }
        SwitchRow("Battery below the clock", s.showBattery) { v -> act.updateSettings { it.copy(showBattery = v) } }
    }
}

/** One entry in the clock Style grid: a ready-made look (font + thickness) or just a font. */
private data class ClockChoice(val name: String, val font: AppFont, val weight: Int?)

/** Every clock look in one list: the presets first, then the remaining fonts. */
private val ClockChoices: List<ClockChoice> =
    ClockPresets.map { ClockChoice(it.name, it.font, it.weight) } +
        AppFont.entries
            .filter { f -> ClockPresets.none { it.font == f } }
            .map { ClockChoice(if (it == AppFont.SERIF) "System serif" else it.label.substringBefore(" ("), it, null) }

/** Clock styles as simple text tiles; the pinned preview above shows the real clock. */
@Composable
private fun ClockStyleGrid(act: MainActivity) {
    val s = act.settings
    val accent = LocalAccent.current
    // A font-only tile is selected when no preset matches the current font + thickness.
    val presetMatch = ClockPresets.any { it.font == s.clockFont && it.weight == s.clockWeight }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        ClockChoices.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    val selected = if (c.weight != null) s.clockFont == c.font && s.clockWeight == c.weight
                    else s.clockFont == c.font && !presetMatch
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = if (selected) 0.12f else 0.05f))
                            .border(
                                if (selected) 2.dp else 1.dp,
                                if (selected) accent else Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(14.dp),
                            )
                            .clickable {
                                act.updateSettings {
                                    if (c.weight != null) it.copy(clockFont = c.font, clockWeight = c.weight)
                                    else it.copy(clockFont = c.font)
                                }
                            }
                            .padding(vertical = 14.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            c.name,
                            color = if (selected) accent else Color.White.copy(alpha = 0.85f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ClockColorPicker(selected: Long, onSelect: (Long) -> Unit) {
    val accent = LocalAccent.current
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        ClockColors.chunked(6).forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                row.forEach { c ->
                    val isSel = c == selected
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3A3970))
                            .border(if (isSel) 3.dp else 1.dp, if (isSel) accent else Color.White.copy(alpha = 0.15f), CircleShape)
                            .clickable { onSelect(c) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(c)))
                    }
                }
            }
        }
        Text(
            when (selected) {
                0xFFFFFFFF -> "White"
                0xC8FFFFFF -> "Vibrant — translucent like the iPhone, wallpaper glows through"
                else -> "Tinted"
            },
            color = Muted, fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun LazyListScope.homePage(act: MainActivity, go: (SettingsPage) -> Unit) {
    val s = act.settings
    item {
        NavRow("Home apps", "${act.favoriteKeys.size} apps · add, remove and reorder") { go(SettingsPage.HOME_APPS) }

        Section("How apps look")
        DisplayPicker(s.homeDisplay) { v -> act.updateSettings { it.copy(homeDisplay = v) } }
        val homeIcons = s.homeDisplay != AppDisplay.NAME_ONLY
        SwitchRow(
            "Monochrome icons",
            s.monochromeIcons,
            subtitle = "White themed icons (Android 13+, for apps that support it)",
            enabled = homeIcons || s.drawerDisplay != AppDisplay.NAME_ONLY,
        ) { v -> act.updateSettings { it.copy(monochromeIcons = v) } }
        SliderRow("Icon size", s.iconSize, 18..44, "dp", enabled = homeIcons) { v ->
            act.updateSettings { it.copy(iconSize = v) }
        }

        Section("List")
        FontChoiceRow("App name font", s.labelFont) { v -> act.updateSettings { it.copy(labelFont = v) } }
        SliderRow("Text size", s.labelSize, 14..30, "sp") { v -> act.updateSettings { it.copy(labelSize = v) } }
        SliderRow("Row spacing", s.rowHeight, 40..80, "dp") { v -> act.updateSettings { it.copy(rowHeight = v) } }
        Section("Position")
        NavRow("Move apps on screen", "Use ← ↑ ↓ → buttons to place the app list exactly") {
            act.startEditing(EditTarget.APPS)
        }
        NavRow("Move clock on screen", "Same arrow controls for the clock, date and battery") {
            act.startEditing(EditTarget.CLOCK)
        }

        Section("Bottom bar")
        SwitchRow("Show “All apps” hint", s.showAllAppsHint) { v -> act.updateSettings { it.copy(showAllAppsHint = v) } }
        SwitchRow("Show search button", s.showSearchButton) { v -> act.updateSettings { it.copy(showSearchButton = v) } }

        Section("System bars")
        SwitchRow(
            "Hide status bar",
            s.hideStatusBar,
            subtitle = "Top bar with time and icons. Swipe down from the top edge to see it briefly.",
        ) { v -> act.updateSettings { it.copy(hideStatusBar = v) } }
        SwitchRow(
            "Hide navigation bar",
            s.hideNavBar,
            subtitle = "Bottom gesture bar. Your first swipe up shows it, the second one acts.",
        ) { v -> act.updateSettings { it.copy(hideNavBar = v) } }
        Text(
            "Only on Cascade's home screen and app drawer — other apps are untouched, and the " +
                "bars always show while you're in these settings. No permission needed.",
            color = Muted, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
    }
}

private fun LazyListScope.homeAppsPage(act: MainActivity) {
    val byKey = act.allApps.associateBy { it.key }
    val favorites = act.favoriteKeys.mapNotNull { byKey[it] }
    val others = act.allApps.filter { !act.isFavorite(it) }

    item { Section("On your home screen") }
    if (favorites.isEmpty()) item { InfoRow("No home apps yet", "Add some from the list below") }
    itemsIndexed(favorites, key = { _, a -> "fav_" + a.key }) { i, app ->
        AppSettingRow(act, app) {
            SmallButton("↑", enabled = i > 0) { act.moveFavorite(app, -1) }
            SmallButton("↓", enabled = i < favorites.lastIndex) { act.moveFavorite(app, 1) }
            SmallButton("✕") { act.toggleFavorite(app) }
        }
    }
    item { Section("Add to home") }
    items(others, key = { "add_" + it.key }) { app ->
        AppSettingRow(act, app, onClick = { act.toggleFavorite(app) }) {
            SmallButton("+") { act.toggleFavorite(app) }
        }
    }
}

private fun LazyListScope.appearancePage(act: MainActivity) {
    val s = act.settings
    item {
        Preview(act)
        Section("Accent color")
        Text(
            "Used for drawer letters, the A–Z bubble and highlights",
            color = Muted, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AccentChoices.forEach { c ->
                val selected = c == s.accent
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(if (selected) 3.dp else 0.dp, Color.White, CircleShape)
                        .clickable { act.updateSettings { it.copy(accent = c) } },
                )
            }
        }
        Section("Background")
        SwitchRow(
            "Darken background",
            s.darkenEnabled,
            subtitle = "Lay a dark shade over the wallpaper so the clock and apps stand out",
        ) { v -> act.updateSettings { it.copy(darkenEnabled = v) } }
        NavRow(
            "How dark",
            if (s.darkenEnabled) "${s.darkenAmount}% · opens a live preview on your home screen" else "Turn on Darken background first",
        ) { if (s.darkenEnabled) act.startDarkenPreview() }
        SliderRow("Drawer background", s.drawerOpacity, 40..100, "%") { v ->
            act.updateSettings { it.copy(drawerOpacity = v) }
        }
        ActionRow("Change wallpaper") { act.openWallpaperPicker() }
    }
}

private fun LazyListScope.drawerPage(act: MainActivity, go: (SettingsPage) -> Unit) {
    val s = act.settings
    item {
        Section("How apps look")
        DisplayPicker(s.drawerDisplay) { v -> act.updateSettings { it.copy(drawerDisplay = v) } }
        Section("List")
        SwitchRow("A–Z scroll bar", s.showScrubber, subtitle = "Drag along the right edge to jump to a letter") { v ->
            act.updateSettings { it.copy(showScrubber = v) }
        }
        SwitchRow("Section letters", s.showSectionHeaders) { v ->
            act.updateSettings { it.copy(showSectionHeaders = v) }
        }
        SwitchRow("Open keyboard automatically", s.autoKeyboard, subtitle = "Start typing to search as soon as the drawer opens") { v ->
            act.updateSettings { it.copy(autoKeyboard = v) }
        }
        NavRow("Hidden apps", "${s.hiddenApps.size} hidden · they still show up in search") { go(SettingsPage.HIDDEN) }
    }
}

private fun LazyListScope.hiddenPage(act: MainActivity) {
    item {
        Text(
            "Hidden apps don't appear in the drawer list, but you can still find them with search.",
            color = Muted, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
    }
    items(act.allApps, key = { "hid_" + it.key }) { app ->
        AppSettingRow(act, app, onClick = { act.toggleHidden(app) }) {
            Switch(
                checked = act.isHidden(app),
                onCheckedChange = { act.toggleHidden(app) },
                colors = switchColors(),
            )
        }
    }
}

private fun LazyListScope.gesturesPage(act: MainActivity) {
    val s = act.settings
    item {
        ChoiceRow("Swipe up", s.swipeUp, GestureAction.entries, { it.label }) { v ->
            act.updateSettings { it.copy(swipeUp = v) }
        }
        ChoiceRow("Swipe down", s.swipeDown, GestureAction.entries, { it.label }) { v ->
            act.updateSettings { it.copy(swipeDown = v) }
        }
        ChoiceRow("Double tap (empty space)", s.doubleTap, GestureAction.entries, { it.label }) { v ->
            act.updateSettings { it.copy(doubleTap = v) }
        }

        Text(
            "Long-press an empty spot on the home screen any time to get back here.",
            color = Muted, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.islandPage(act: MainActivity) {
    val s = act.settings
    // Pinned while you change colours and fonts; the Spotify button preview takes over further down.
    stickyHeader {
        Box(Modifier.background(Bg)) { IslandPreview(s.islandVisualizer, (s.islandFont ?: s.labelFont).family(), s.islandLook) }
    }
    item {
        // Reading resumeTick re-checks the permission every time you come back from system settings.
        val notifOk = act.resumeTick.let { act.hasNotificationAccess() }

        SwitchRow(
            "Enable Dynamic Island",
            s.islandEnabled,
            subtitle = "Shows what's playing above the clock on your home screen only. " +
                "Small (cover + bars) → tap → Medium (with title) → tap → Large (controls). " +
                "Long-press to open the music app.",
        ) { v -> act.updateSettings { it.copy(islandEnabled = v) } }

        Section("Permission")
        PermissionRow(
            "Notification access",
            granted = notifOk,
            why = "Needed to see what song is playing and control it",
            onClick = act::openNotificationAccess,
        )
        if (!notifOk) {
            Text(
                "If Notification access is greyed out (“Restricted setting”): open Cascade's App info → ⋮ menu → " +
                    "“Allow restricted settings”, then come back and try again.",
                color = Muted, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            ActionRow("Open Cascade app info") { act.openOwnAppInfo() }
        }

        Section("Look")
        Text(
            "The island's surface, on every stage.",
            color = Muted, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val accent = LocalAccent.current
            IslandLook.entries.forEach { lk ->
                val sel = lk == s.islandLook
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = if (sel) 0.12f else 0.05f))
                        .border(
                            if (sel) 2.dp else 1.dp,
                            if (sel) accent else Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(14.dp),
                        )
                        .clickable { act.updateSettings { it.copy(islandLook = lk) } }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(lk.label, color = if (sel) accent else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(lk.blurb, color = Muted, fontSize = 11.sp, lineHeight = 14.sp)
                }
            }
        }

        Section("Gestures")
        SwitchRow(
            "Swipe to skip",
            s.islandSwipe,
            subtitle = "Swipe left on the island for the next song, right for the previous one. Double-tap to play or pause",
        ) { v -> act.updateSettings { it.copy(islandSwipe = v) } }
        SwitchRow(
            "Hide buttons",
            s.islandHideButtons,
            subtitle = "A cleaner island with no play or skip buttons; use the gestures instead",
            enabled = s.islandSwipe,
        ) { v -> act.updateSettings { it.copy(islandHideButtons = v) } }

        Section("Visualizer colors")
        Text(
            "The bars keep flowing through these colors while music plays.",
            color = Muted, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        VisualizerChoices(s.islandVisualizer) { v -> act.updateSettings { it.copy(islandVisualizer = v) } }

        Section("Font")
        Text(
            "For the song title, artist, app name and times in the island.",
            color = Muted, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        IslandFontChoices(s.islandFont, s.labelFont) { v -> act.updateSettings { it.copy(islandFont = v) } }

    }
    // Pinned while you pick a style and drag the sliders, so every change is visible right away.
    stickyHeader { Box(Modifier.background(Bg)) { Preview(act, badge = true) } }
    item {
        Section("Spotify button")
        Text(
            "Shown where the island sits when Spotify isn't running. Tap it to open Spotify. " +
                "The first styles use your clock colour; the last few have their own colours.",
            color = Muted, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        BadgeChoices(act) { v -> act.updateSettings { it.copy(badgeStyle = v) } }
        SliderRow("See-through", 100 - s.badgeOpacity, 0..80, "%", enabled = s.badgeStyle.themed) { v ->
            act.updateSettings { it.copy(badgeOpacity = 100 - v) }
        }
        SliderRow("Button size", s.badgeSize, 24..56, "dp") { v -> act.updateSettings { it.copy(badgeSize = v) } }
    }
    // An empty pinned header here pushes the preview away once you scroll past this section.
    stickyHeader { Spacer(Modifier.height(0.dp)) }
    item {

        Section("Size & position")
        SwitchRow(
            "Match clock width (Medium stage)",
            s.islandMatchClock,
            subtitle = "The Medium stage is exactly as wide as the time",
        ) { v -> act.updateSettings { it.copy(islandMatchClock = v) } }
        NavRow(
            "Resize island stages",
            "Width, height and size of Small, Medium and Large · live on your home screen",
        ) { act.startIslandSizing() }
        NavRow(
            "Move island on screen",
            "Sits above the clock · use ← ↑ ↓ → to place it exactly",
        ) { act.startEditing(EditTarget.ISLAND) }

        Section("Options")
        SwitchRow("Only Spotify", s.islandSpotifyOnly, subtitle = "Ignore other music and video apps") { v ->
            act.updateSettings { it.copy(islandSpotifyOnly = v) }
        }
        SwitchRow(
            "Hide when paused",
            s.islandHideWhenPaused,
            subtitle = "Off: a paused song keeps the island with frozen bars. " +
                "When Spotify isn't running, a small Spotify button takes the island's place.",
        ) { v ->
            act.updateSettings { it.copy(islandHideWhenPaused = v) }
        }
        Text(
            "The island only appears on Cascade's home screen — it never draws over other apps. " +
                "Works with Spotify, YouTube Music and any app that shows media controls. " +
                "Everything happens on the phone: Cascade has no internet access.",
            color = Muted, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun VisualizerChoices(selected: VisualizerStyle, onSelect: (VisualizerStyle) -> Unit) {
    val accent = LocalAccent.current
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        VisualizerStyle.entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { style ->
                    val isSel = style == selected
                    val palette = remember(style, accent) { visualizerPalette(style, emptyList(), accent) }
                    Row(
                        Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Card)
                            .border(if (isSel) 2.dp else 0.dp, if (isSel) accent else Color.Transparent, RoundedCornerShape(14.dp))
                            .clickable { onSelect(style) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Visualizer(true, palette, bars = 4, modifier = Modifier.size(width = 22.dp, height = 18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            style.label,
                            color = if (isSel) Color.White else Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, why: String, onClick: () -> Unit) =
    RowShell(title, if (granted) "Allowed" else "Tap to allow · $why", onClick = onClick) {
        Text(
            if (granted) "✓" else "!",
            color = if (granted) Color(0xFF34C759) else Color(0xFFFFB020),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }

@Composable
private fun IslandPreview(
    style: VisualizerStyle,
    font: androidx.compose.ui.text.font.FontFamily,
    look: IslandLook,
) {
    val accent = LocalAccent.current
    val palette = remember(style, accent) { visualizerPalette(style, emptyList(), accent) }
    Box(
        Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF26262E))
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .width(250.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    when (look) {
                        IslandLook.BLACK -> Color.Black
                        IslandLook.TINTED -> androidx.compose.ui.graphics.lerp(Color.Black, palette.first(), 0.17f)
                        IslandLook.GLASS -> Color.Black.copy(alpha = 0.42f)
                    },
                )
                .then(
                    if (look == IslandLook.GLASS) Modifier.border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(50))
                    else Modifier,
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(palette.ifEmpty { listOf(accent) }.let { if (it.size == 1) it + it else it })),
            )
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text("Midnight City", color = Color.White, fontSize = 14.sp, fontFamily = font, maxLines = 1)
                Text("M83", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontFamily = font, maxLines = 1)
            }
            Visualizer(true, palette, bars = 5, modifier = Modifier.size(width = 28.dp, height = 18.dp))
        }
    }
}

// =====================================================================================
// Building blocks
// =====================================================================================

@Composable
private fun Preview(act: MainActivity, badge: Boolean = false) {
    val s = act.settings
    val now = remember { java.time.LocalDateTime.now() }
    val is24 = s.clockFormat == ClockFormat.H24
    val clockColor = Color(s.clockColor)
    Box(
        Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Color(0xFF3B3A86), Color(0xFF1D1C4A), Color(0xFF4A2D7A)),
                ),
            )
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (badge) {
                // The real Spotify button, drawn exactly as on the home screen (style, colour,
                // see-through, size), scaled like the rest of this preview.
                val spotifyApp = remember(act.allApps) {
                    act.allApps.firstOrNull { it.packageName == IslandService.SPOTIFY }
                }
                SpotifyButton(act, spotifyApp, sizeDp = (s.badgeSize * 0.72f).roundToInt())
                Spacer(Modifier.height(10.dp))
            }
            if (s.showDate) {
                Text(
                    now.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                    color = clockColor.copy(alpha = clockColor.alpha * 0.92f),
                    fontSize = (s.dateSize * 0.72f).sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = s.clockFont.familyAt(600),
                )
            }
            if (s.showClock) {
                ClockTimeText(
                    s,
                    now.format(java.time.format.DateTimeFormatter.ofPattern(if (is24) "H:mm" else "h:mm")),
                    sizeSp = s.clockSize * 0.72f,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        color = LocalAccent.current,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 6.dp),
    )
}

@Composable
private fun RowShell(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    titleColor: Color = Color.White,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor.copy(alpha = alpha), fontSize = 17.sp)
            if (subtitle != null) {
                Text(subtitle, color = Muted.copy(alpha = Muted.alpha * alpha), fontSize = 13.sp)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String? = null, onClick: () -> Unit) =
    RowShell(title, subtitle, onClick = onClick) { Text("›", color = Muted, fontSize = 24.sp) }

@Composable
private fun ActionRow(title: String, subtitle: String? = null, danger: Boolean = false, onClick: () -> Unit) =
    RowShell(title, subtitle, titleColor = if (danger) Color(0xFFFF6B6B) else Color.White, onClick = onClick)

@Composable
private fun InfoRow(title: String, subtitle: String) = RowShell(title, subtitle)

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = LocalAccent.current,
    checkedBorderColor = LocalAccent.current,
)

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    subtitle: String? = null,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) = RowShell(title, subtitle, enabled, onClick = { onChange(!checked) }) {
    Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, colors = switchColors())
}

@Composable
private fun SliderRow(
    title: String,
    value: Int,
    range: IntRange,
    unit: String,
    enabled: Boolean = true,
    onChange: (Int) -> Unit,
) {
    // Keep a local value while dragging so the slider stays smooth; commit as it moves.
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val alpha = if (enabled) 1f else 0.4f
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White.copy(alpha = alpha), fontSize = 17.sp, modifier = Modifier.weight(1f))
            Text("${local.roundToInt()} $unit", color = Muted, fontSize = 14.sp)
        }
        Slider(
            value = local,
            onValueChange = {
                local = it
                onChange(it.roundToInt())
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = LocalAccent.current,
                activeTrackColor = LocalAccent.current,
                inactiveTrackColor = Color.White.copy(alpha = 0.15f),
            ),
        )
    }
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    selected: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        RowShell(title, label(selected), onClick = { open = true }) {
            Text("▾", color = Muted, fontSize = 18.sp)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            offset = androidx.compose.ui.unit.DpOffset(24.dp, 0.dp),
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = {
                        Text(
                            (if (opt == selected) "✓  " else "     ") + label(opt),
                            color = if (opt == selected) LocalAccent.current else Color.White,
                        )
                    },
                    onClick = { open = false; onSelect(opt) },
                )
            }
        }
    }
}

@Composable
private fun AppSettingRow(
    act: MainActivity,
    app: AppEntry,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconImage(act, app, Modifier.size(26.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            app.label,
            color = Color.White,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            trailing()
        }
    }
}

@Composable
private fun SmallButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.08f else 0.03f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White.copy(alpha = if (enabled) 0.9f else 0.25f), fontSize = 17.sp)
    }
}

/** Picker where every option is written in its own font. */
@Composable
private fun FontChoiceRow(title: String, selected: AppFont, onSelect: (AppFont) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val accent = LocalAccent.current
    Box {
        RowShell(title, null, onClick = { open = true }) {
            Text(selected.label, color = Muted, fontSize = 16.sp, fontFamily = selected.family())
            Spacer(Modifier.width(6.dp))
            Text("▾", color = Muted, fontSize = 18.sp)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            offset = androidx.compose.ui.unit.DpOffset(24.dp, 0.dp),
        ) {
            AppFont.entries.forEach { f ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "12:45",
                                color = if (f == selected) accent else Color.White,
                                fontSize = 22.sp,
                                fontFamily = f.family(),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(92.dp),
                            )
                            Text(
                                f.label,
                                color = if (f == selected) accent else Color.White.copy(alpha = 0.8f),
                                fontSize = 15.sp,
                                fontFamily = f.family(),
                            )
                        }
                    },
                    onClick = { open = false; onSelect(f) },
                )
            }
        }
    }
}

/** Two side-by-side cards: "Icon + name" or "Name only", each with a tiny example. */
@Composable
private fun DisplayPicker(selected: AppDisplay, onSelect: (AppDisplay) -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppDisplay.entries.forEach { d ->
            val isSel = d == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Card)
                    .border(if (isSel) 2.dp else 0.dp, if (isSel) accent else Color.Transparent, RoundedCornerShape(16.dp))
                    .clickable { onSelect(d) }
                    .padding(12.dp),
            ) {
                listOf("Spotify", "YouTube", "Camera").forEach { name ->
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (d != AppDisplay.NAME_ONLY) {
                            Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.8f)))
                            Spacer(Modifier.width(6.dp))
                        }
                        if (d != AppDisplay.ICON_ONLY) {
                            Text(name, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    d.label,
                    color = if (isSel) accent else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Grid of every Spotify button style, each drawn live over a sample wallpaper. */
@Composable
private fun BadgeChoices(act: MainActivity, onSelect: (BadgeStyle) -> Unit) {
    val selected = act.settings.badgeStyle
    val accent = LocalAccent.current
    val spotifyApp = remember(act.allApps) { act.allApps.firstOrNull { it.packageName == IslandService.SPOTIFY } }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BadgeStyle.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { style ->
                    val isSel = style == selected
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF2B3A55), Color(0xFF1A2236), Color(0xFF3B2F4A)),
                                ),
                            )
                            .border(
                                if (isSel) 2.dp else 1.dp,
                                if (isSel) accent else Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(14.dp),
                            )
                            .clickable { onSelect(style) }
                            .padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SpotifyButton(act, spotifyApp, style = style, sizeDp = 40)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            style.label,
                            color = if (isSel) Color.White else Muted,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Sideways list of fonts for the island, each written in its own font. null = same as app names. */
@Composable
private fun IslandFontChoices(selected: AppFont?, labelFont: AppFont, onSelect: (AppFont?) -> Unit) {
    val accent = LocalAccent.current
    val options: List<AppFont?> = listOf<AppFont?>(null) + AppFont.entries
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options.size) { i ->
            val f = options[i]
            val isSel = f == selected
            val family = (f ?: labelFont).family()
            Column(
                Modifier
                    .width(112.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = if (isSel) 0.12f else 0.05f))
                    .border(
                        if (isSel) 2.dp else 1.dp,
                        if (isSel) accent else Color.White.copy(alpha = 0.08f),
                        RoundedCornerShape(14.dp),
                    )
                    .clickable { onSelect(f) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text("Midnight", color = Color.White, fontSize = 17.sp, fontFamily = family, maxLines = 1)
                Text(
                    f?.label ?: "Same as apps",
                    color = if (isSel) accent else Muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
    }
}
