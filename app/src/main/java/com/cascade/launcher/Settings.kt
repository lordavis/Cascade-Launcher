package com.cascade.launcher

import android.content.Context
import androidx.core.content.edit

enum class ClockFormat(val label: String) { SYSTEM("Follow system"), H12("12-hour"), H24("24-hour") }
/** Fonts. System ones come with Android; the rest are bundled open-source fonts (no download). */
enum class AppFont(val label: String) {
    SYSTEM("System"),
    INTER("Inter (iPhone-like)"),
    ROUNDED("Rounded"),
    STENCIL("Stencil"),
    RAILS("Rails"),
    THIN("System thin"),
    CONDENSED("Condensed"),
    SERIF("Serif"),
    MONO("Monospace"),
    DOT("Dot matrix"),
    BEBAS("Bebas Neue"),
    OUTFIT("Outfit"),
    GROTESK("Space Grotesk"),
    ORBITRON("Orbitron"),
    PLAYFAIR("Playfair"),
    BRUSH("Brush script"),
}

/** Color presets for the island's music visualizer. */
enum class VisualizerStyle(val label: String) {
    ALBUM("Album colors"),
    RAINBOW("Rainbow"),
    NEON("Neon"),
    SUNSET("Sunset"),
    OCEAN("Ocean"),
    AURORA("Aurora"),
    FIRE("Fire"),
    CANDY("Candy"),
    ACCENT("Launcher accent"),
    WHITE("Classic white"),
}

/** How each app is shown in a list. */
enum class AppDisplay(val label: String) { ICON_AND_NAME("Icon + name"), NAME_ONLY("Name only"), ICON_ONLY("Icon only") }

/** Things the on-screen position editor can move. */
enum class EditTarget(val label: String) { APPS("Apps"), CLOCK("Clock"), ISLAND("Island") }
enum class GestureAction(val label: String) {
    NONE("Do nothing"),
    DRAWER("Open app drawer"),
    SEARCH("Open search"),
    NOTIFICATIONS("Open notifications"),
}

/** Accent choices shown in settings (ARGB). */
val AccentChoices = listOf(
    0xFFFF1F4D, 0xFFFF7A1A, 0xFFFFC21A, 0xFF34C759,
    0xFF1AB8FF, 0xFF5B6CFF, 0xFFB45BFF, 0xFFFFFFFF,
)

data class LauncherSettings(
    // Clock
    val showClock: Boolean = true,
    val clockSize: Int = 104,                 // sp
    val clockWeight: Int = 600,               // 100 (hairline) … 900 (black)
    val clockColor: Long = 0xFFFFFFFF,
    val clockNumerals: Numerals = Numerals.WESTERN,
    val clockFormat: ClockFormat = ClockFormat.SYSTEM,
    val showDate: Boolean = true,
    val dateSize: Int = 20,                   // sp, the "Saturday, September 24" line
    val showBattery: Boolean = true,
    val clockFont: AppFont = AppFont.INTER,
    val labelFont: AppFont = AppFont.SYSTEM,
    // Home list
    val homeDisplay: AppDisplay = AppDisplay.ICON_AND_NAME,
    val drawerDisplay: AppDisplay = AppDisplay.ICON_AND_NAME,
    val monochromeIcons: Boolean = true,
    val iconSize: Int = 28,                   // dp
    val labelSize: Int = 20,                  // sp
    val rowHeight: Int = 56,                  // dp
    // Free positioning (dp offsets, set with the on-screen arrow editor)
    val listX: Int = 0,                       // + moves right
    val listY: Int = 0,                       // + moves down (0 = bottom of the free area)
    val clockX: Int = 0,
    val clockY: Int = 0,
    val showAllAppsHint: Boolean = true,
    val showSearchButton: Boolean = true,
    val hideStatusBar: Boolean = false,       // top bar (time, notifications, battery icons)
    val hideNavBar: Boolean = false,          // bottom gesture pill / nav buttons
    // Appearance
    val accent: Long = AccentChoices[0],
    val darkenEnabled: Boolean = true,
    val darkenAmount: Int = 25,               // percent of black laid over the wallpaper
    val drawerOpacity: Int = 95,              // percent
    // Drawer
    val showScrubber: Boolean = true,
    val showSectionHeaders: Boolean = true,
    val autoKeyboard: Boolean = false,
    val hiddenApps: Set<String> = emptySet(),
    // Gestures
    val swipeUp: GestureAction = GestureAction.DRAWER,
    val swipeDown: GestureAction = GestureAction.NOTIFICATIONS,
    val doubleTap: GestureAction = GestureAction.NONE,
    // Dynamic island
    val islandEnabled: Boolean = false,
    val islandX: Int = 0,                     // offset from the camera hole / top center
    val islandY: Int = 0,
    val islandSpotifyOnly: Boolean = false,
    val islandHideWhenPaused: Boolean = false,
    val islandVisualizer: VisualizerStyle = VisualizerStyle.ALBUM,
    val islandMatchClock: Boolean = true,
    /** Font for the island's text; null = same as the app names. */
    val islandFont: AppFont? = null,
    val badgeStyle: BadgeStyle = BadgeStyle.CUTOUT,
    val badgeOpacity: Int = 72,               // % — for the clock-coloured styles
    val badgeSize: Int = 36,                  // dp
    // Island stage sizes. Width/height in dp, scale in % (100 = normal).
    val islandMiniWidth: Int = 84,
    val islandMiniHeight: Int = 36,
    val islandMiniScale: Int = 100,
    val islandPillWidth: Int = 220,           // used when "Match clock width" is off
    val islandPillHeight: Int = 36,
    val islandPillScale: Int = 100,
    val islandExpWidth: Int = 100,            // % of the screen width
    val islandExpHeight: Int = 0,             // dp, 0 = fit the content
    val islandExpScale: Int = 100,
    // Black border per stage, drawn on the INSIDE edge (never changes the island's size).
    val islandMiniBorderOn: Boolean = false,
    val islandMiniBorder: Int = 2,            // dp
    val islandPillBorderOn: Boolean = false,
    val islandPillBorder: Int = 2,
    val islandExpBorderOn: Boolean = false,
    val islandExpBorder: Int = 3,
    // Medium-stage extras (presets just switch these on/off)
    val pillSkip: Boolean = false,            // ⏮ ⏭ next to play/pause
    val pillTwoLines: Boolean = false,        // song + artist
    val pillProgress: Boolean = false,        // thin progress line along the bottom
    val pillTime: Boolean = false,            // remaining time
    val pillWideViz: Boolean = false,         // visualizer stretched across the free space
    val pillGlow: Boolean = false,            // static album-colour glow behind the pill
    val pillGlowDynamic: Boolean = false,     // moving, breathing album-colour glow
    val pillWash: Boolean = false,            // blurred album art behind the pill
    val pillDots: Boolean = false,            // dot-matrix visualizer
    val islandLook: IslandLook = IslandLook.BLACK,
    val islandSwipe: Boolean = false,         // swipe to skip, double-tap to play/pause
    val islandHideButtons: Boolean = false,   // hide the control buttons (needs islandSwipe)
    val pillFit: Boolean = false,             // shrink to fit the content
    val miniFeatures: Set<MiniFeature> = emptySet(),
    val expFeatures: Set<ExpFeature> = DefaultExpFeatures,
) {
    val pillStyle: PillStyle
        get() = PillStyle(
            pillSkip, pillTwoLines, pillProgress, pillTime, pillWideViz, pillGlow, pillFit, pillGlowDynamic,
            pillWash, pillDots,
        )

    fun withPillStyle(p: PillStyle) = copy(
        pillSkip = p.skip, pillTwoLines = p.twoLines, pillProgress = p.progress, pillTime = p.time,
        pillWideViz = p.wideViz, pillGlow = p.glow, pillFit = p.fit, pillGlowDynamic = p.glowDynamic,
        pillWash = p.wash, pillDots = p.dots,
    )
}

/** Optional extras for the Small island (cover + visualizer). */
enum class MiniFeature(val label: String) {
    PLAY_BUTTON("Play/pause"),
    SQUARE_COVER("Square cover"),
    SPIN("Spinning cover"),
    RING("Progress ring"),
    GLOW("Album glow"),
    WIDE_VIZ("Wide visualizer"),
    HIDE_VIZ("No visualizer"),
    DOTS("Dot matrix bars"),
}

/** Optional extras for the Large island (full card). */
enum class ExpFeature(val label: String) {
    POSTER("Big cover on top"),
    ART_BACKGROUND("Cover as background"),
    GLOW("Album glow"),
    APP_NAME("App name"),
    TIMES("Times"),
    VISUALIZER("Visualizer"),
    WIDE_VIZ("Wide visualizer"),
    SEEK("Drag to seek"),
    MINIMAL("Minimal card"),
    ACCENT_PLAY("Accent play button"),
    ACCENT_TEXT("Accent text"),
    CONTRAST("Contrast"),
    DOTS("Dot matrix bars"),
}

/** Surface of the whole island (every stage). */
enum class IslandLook(val label: String, val blurb: String) {
    BLACK("Black", "Classic solid black"),
    TINTED("Tinted", "Near-black in the song's colour"),
    GLASS("Glass", "See-through with a soft rim"),
}

val MiniPresets: List<Pair<String, Set<MiniFeature>>> = listOf(
    "Classic" to emptySet(),
    "Vinyl" to setOf(MiniFeature.SPIN, MiniFeature.RING),
    "Player" to setOf(MiniFeature.PLAY_BUTTON),
    "Glow" to setOf(MiniFeature.GLOW, MiniFeature.WIDE_VIZ),
    "Square" to setOf(MiniFeature.SQUARE_COVER),
    "Cover only" to setOf(MiniFeature.HIDE_VIZ, MiniFeature.RING),
    "Neon" to setOf(MiniFeature.GLOW, MiniFeature.RING, MiniFeature.WIDE_VIZ),
    "DJ" to setOf(MiniFeature.SPIN, MiniFeature.PLAY_BUTTON, MiniFeature.GLOW),
)

/** The original Large card = app name, times and visualizer. */
val DefaultExpFeatures = setOf(ExpFeature.APP_NAME, ExpFeature.TIMES, ExpFeature.VISUALIZER)

val ExpPresets: List<Pair<String, Set<ExpFeature>>> = listOf(
    "Classic" to DefaultExpFeatures,
    "Poster" to setOf(ExpFeature.POSTER, ExpFeature.TIMES, ExpFeature.GLOW, ExpFeature.SEEK),
    "Album art" to setOf(ExpFeature.ART_BACKGROUND, ExpFeature.TIMES, ExpFeature.VISUALIZER, ExpFeature.SEEK),
    "Minimal" to emptySet(),
    "Waveform" to setOf(ExpFeature.WIDE_VIZ, ExpFeature.GLOW, ExpFeature.TIMES),
    "Studio" to setOf(
        ExpFeature.APP_NAME, ExpFeature.TIMES, ExpFeature.VISUALIZER, ExpFeature.WIDE_VIZ,
        ExpFeature.GLOW, ExpFeature.SEEK,
    ),
    "Cinema" to setOf(ExpFeature.POSTER, ExpFeature.ART_BACKGROUND, ExpFeature.TIMES, ExpFeature.SEEK),
)

/** What the Medium island shows besides the cover, title, play/pause and visualizer. */
data class PillStyle(
    val skip: Boolean = false,
    val twoLines: Boolean = false,
    val progress: Boolean = false,
    val time: Boolean = false,
    val wideViz: Boolean = false,
    val glow: Boolean = false,                // static glow
    val fit: Boolean = false,
    val glowDynamic: Boolean = false,         // flowing + gently pulsing glow
    val wash: Boolean = false,                // blurred, darkened album art fills the pill
    val dots: Boolean = false,                // dot-matrix visualizer
)

/** Ready-made Medium layouts to try. Every extra appears in at least one. */
val PillPresets: List<Pair<String, PillStyle>> = listOf(
    "Classic" to PillStyle(),
    "Controls" to PillStyle(skip = true, progress = true),
    "Two-line" to PillStyle(twoLines = true, time = true),
    "Waveform" to PillStyle(wideViz = true, glowDynamic = true),
    "Compact" to PillStyle(fit = true),
    "Glow" to PillStyle(glow = true, twoLines = true, progress = true),
    "Live glow" to PillStyle(glowDynamic = true, twoLines = true, progress = true),
    "Mini player" to PillStyle(fit = true, skip = true, progress = true),
    "Everything" to PillStyle(skip = true, twoLines = true, progress = true, time = true, glow = true),
    "Cover wash" to PillStyle(wash = true, twoLines = true),
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("cascade_settings", Context.MODE_PRIVATE)

    fun load(): LauncherSettings {
        val d = LauncherSettings()
        return LauncherSettings(
            showClock = prefs.getBoolean("showClock", d.showClock),
            clockSize = prefs.getInt("clockSize", d.clockSize),
            clockWeight = prefs.getInt("clockWeightValue", legacyWeight() ?: d.clockWeight),
            clockColor = prefs.getLong("clockColor", d.clockColor),
            clockNumerals = enumOr(prefs.getString("clockNumerals", null), d.clockNumerals),
            clockFormat = enumOr(prefs.getString("clockFormat", null), d.clockFormat),
            showDate = prefs.getBoolean("showDate", d.showDate),
            dateSize = prefs.getInt("dateSize", d.dateSize),
            showBattery = prefs.getBoolean("showBattery", d.showBattery),
            clockFont = enumOr(prefs.getString("clockFont", null), d.clockFont),
            labelFont = enumOr(prefs.getString("labelFont", null), d.labelFont),
            // Older versions had one "Show icons" switch: carry it over to both lists.
            homeDisplay = enumOr(prefs.getString("homeDisplay", null), legacyDisplay() ?: d.homeDisplay),
            drawerDisplay = enumOr(prefs.getString("drawerDisplay", null), legacyDisplay() ?: d.drawerDisplay),
            monochromeIcons = prefs.getBoolean("monochromeIcons", d.monochromeIcons),
            iconSize = prefs.getInt("iconSize", d.iconSize),
            labelSize = prefs.getInt("labelSize", d.labelSize),
            rowHeight = prefs.getInt("rowHeight", d.rowHeight),
            listX = prefs.getInt("listX", d.listX),
            listY = prefs.getInt("listY", d.listY),
            clockX = prefs.getInt("clockX", d.clockX),
            clockY = prefs.getInt("clockY", d.clockY),
            showAllAppsHint = prefs.getBoolean("showAllAppsHint", d.showAllAppsHint),
            showSearchButton = prefs.getBoolean("showSearchButton", d.showSearchButton),
            hideStatusBar = prefs.getBoolean("hideStatusBar", d.hideStatusBar),
            hideNavBar = prefs.getBoolean("hideNavBar", d.hideNavBar),
            accent = prefs.getLong("accent", d.accent),
            darkenEnabled = prefs.getBoolean("darkenEnabled", d.darkenEnabled),
            // "wallpaperDim" is the old key for the same thing, so earlier choices carry over.
            darkenAmount = prefs.getInt("wallpaperDim", d.darkenAmount),
            drawerOpacity = prefs.getInt("drawerOpacity", d.drawerOpacity),
            showScrubber = prefs.getBoolean("showScrubber", d.showScrubber),
            showSectionHeaders = prefs.getBoolean("showSectionHeaders", d.showSectionHeaders),
            autoKeyboard = prefs.getBoolean("autoKeyboard", d.autoKeyboard),
            hiddenApps = prefs.getStringSet("hiddenApps", null)?.toSet() ?: d.hiddenApps,
            swipeUp = enumOr(prefs.getString("swipeUp", null), d.swipeUp),
            swipeDown = enumOr(prefs.getString("swipeDown", null), d.swipeDown),
            doubleTap = enumOr(prefs.getString("doubleTap", null), d.doubleTap),
            islandEnabled = prefs.getBoolean("islandEnabled", d.islandEnabled),
            islandX = prefs.getInt("islandX", d.islandX),
            islandY = prefs.getInt("islandY", d.islandY),
            islandSpotifyOnly = prefs.getBoolean("islandSpotifyOnly", d.islandSpotifyOnly),
            islandHideWhenPaused = prefs.getBoolean("islandHideWhenPaused", d.islandHideWhenPaused),
            islandVisualizer = enumOr(prefs.getString("islandVisualizer", null), d.islandVisualizer),
            islandMatchClock = prefs.getBoolean("islandMatchClock", d.islandMatchClock),
            islandFont = prefs.getString("islandFont", null)?.let { n -> AppFont.entries.firstOrNull { it.name == n } },
            badgeStyle = enumOr(prefs.getString("badgeStyle", null), d.badgeStyle),
            badgeOpacity = prefs.getInt("badgeOpacity", d.badgeOpacity),
            badgeSize = prefs.getInt("badgeSize", d.badgeSize),
            islandMiniBorderOn = prefs.getBoolean("islandMiniBorderOn", d.islandMiniBorderOn),
            islandMiniBorder = prefs.getInt("islandMiniBorder", d.islandMiniBorder),
            islandPillBorderOn = prefs.getBoolean("islandPillBorderOn", d.islandPillBorderOn),
            islandPillBorder = prefs.getInt("islandPillBorder", d.islandPillBorder),
            islandExpBorderOn = prefs.getBoolean("islandExpBorderOn", d.islandExpBorderOn),
            islandExpBorder = prefs.getInt("islandExpBorder", d.islandExpBorder),
            miniFeatures = features("miniFeatures", d.miniFeatures),
            expFeatures = features("expFeatures", d.expFeatures),
            pillSkip = prefs.getBoolean("pillSkip", d.pillSkip),
            pillTwoLines = prefs.getBoolean("pillTwoLines", d.pillTwoLines),
            pillProgress = prefs.getBoolean("pillProgress", d.pillProgress),
            pillTime = prefs.getBoolean("pillTime", d.pillTime),
            pillWideViz = prefs.getBoolean("pillWideViz", d.pillWideViz),
            pillGlow = prefs.getBoolean("pillGlow", d.pillGlow),
            pillGlowDynamic = prefs.getBoolean("pillGlowDynamic", d.pillGlowDynamic),
            pillWash = prefs.getBoolean("pillWash", d.pillWash),
            pillDots = prefs.getBoolean("pillDots", d.pillDots),
            islandSwipe = prefs.getBoolean("islandSwipe", d.islandSwipe),
            islandHideButtons = prefs.getBoolean("islandHideButtons", d.islandHideButtons),
            islandLook = enumOr(prefs.getString("islandLook", null), d.islandLook),
            pillFit = prefs.getBoolean("pillFit", d.pillFit),
            islandMiniWidth = prefs.getInt("islandMiniWidth", d.islandMiniWidth),
            islandMiniHeight = prefs.getInt("islandMiniHeight", d.islandMiniHeight),
            islandMiniScale = prefs.getInt("islandMiniScale", d.islandMiniScale),
            islandPillWidth = prefs.getInt("islandPillWidth", d.islandPillWidth),
            islandPillHeight = prefs.getInt("islandPillHeight", d.islandPillHeight),
            islandPillScale = prefs.getInt("islandPillScale", d.islandPillScale),
            islandExpWidth = prefs.getInt("islandExpWidth", d.islandExpWidth),
            islandExpHeight = prefs.getInt("islandExpHeight", d.islandExpHeight),
            islandExpScale = prefs.getInt("islandExpScale", d.islandExpScale),
        )
    }

    fun save(s: LauncherSettings) = prefs.edit {
        putBoolean("showClock", s.showClock)
        putInt("clockSize", s.clockSize)
        putInt("clockWeightValue", s.clockWeight)
        putLong("clockColor", s.clockColor)
        putString("clockNumerals", s.clockNumerals.name)
        putString("clockFormat", s.clockFormat.name)
        putBoolean("showDate", s.showDate)
        putInt("dateSize", s.dateSize)
        putBoolean("showBattery", s.showBattery)
        putString("clockFont", s.clockFont.name)
        putString("labelFont", s.labelFont.name)
        putString("homeDisplay", s.homeDisplay.name)
        putString("drawerDisplay", s.drawerDisplay.name)
        putBoolean("monochromeIcons", s.monochromeIcons)
        putInt("iconSize", s.iconSize)
        putInt("labelSize", s.labelSize)
        putInt("rowHeight", s.rowHeight)
        putInt("listX", s.listX)
        putInt("listY", s.listY)
        putInt("clockX", s.clockX)
        putInt("clockY", s.clockY)
        putBoolean("showAllAppsHint", s.showAllAppsHint)
        putBoolean("showSearchButton", s.showSearchButton)
        putBoolean("hideStatusBar", s.hideStatusBar)
        putBoolean("hideNavBar", s.hideNavBar)
        putLong("accent", s.accent)
        putBoolean("darkenEnabled", s.darkenEnabled)
        putInt("wallpaperDim", s.darkenAmount)
        putInt("drawerOpacity", s.drawerOpacity)
        putBoolean("showScrubber", s.showScrubber)
        putBoolean("showSectionHeaders", s.showSectionHeaders)
        putBoolean("autoKeyboard", s.autoKeyboard)
        putStringSet("hiddenApps", s.hiddenApps)
        putString("swipeUp", s.swipeUp.name)
        putString("swipeDown", s.swipeDown.name)
        putString("doubleTap", s.doubleTap.name)
        putBoolean("islandEnabled", s.islandEnabled)
        putInt("islandX", s.islandX)
        putInt("islandY", s.islandY)
        putBoolean("islandSpotifyOnly", s.islandSpotifyOnly)
        putBoolean("islandHideWhenPaused", s.islandHideWhenPaused)
        putString("islandVisualizer", s.islandVisualizer.name)
        putBoolean("islandMatchClock", s.islandMatchClock)
        putString("islandFont", s.islandFont?.name)
        putString("badgeStyle", s.badgeStyle.name)
        putInt("badgeOpacity", s.badgeOpacity)
        putInt("badgeSize", s.badgeSize)
        putBoolean("islandMiniBorderOn", s.islandMiniBorderOn)
        putInt("islandMiniBorder", s.islandMiniBorder)
        putBoolean("islandPillBorderOn", s.islandPillBorderOn)
        putInt("islandPillBorder", s.islandPillBorder)
        putBoolean("islandExpBorderOn", s.islandExpBorderOn)
        putInt("islandExpBorder", s.islandExpBorder)
        putString("miniFeatures", s.miniFeatures.joinToString(",") { it.name })
        putString("expFeatures", s.expFeatures.joinToString(",") { it.name })
        putBoolean("pillSkip", s.pillSkip)
        putBoolean("pillTwoLines", s.pillTwoLines)
        putBoolean("pillProgress", s.pillProgress)
        putBoolean("pillTime", s.pillTime)
        putBoolean("pillWideViz", s.pillWideViz)
        putBoolean("pillGlow", s.pillGlow)
        putBoolean("pillGlowDynamic", s.pillGlowDynamic)
        putBoolean("pillWash", s.pillWash)
        putBoolean("pillDots", s.pillDots)
        putBoolean("islandSwipe", s.islandSwipe)
        putBoolean("islandHideButtons", s.islandHideButtons)
        putString("islandLook", s.islandLook.name)
        putBoolean("pillFit", s.pillFit)
        putInt("islandMiniWidth", s.islandMiniWidth)
        putInt("islandMiniHeight", s.islandMiniHeight)
        putInt("islandMiniScale", s.islandMiniScale)
        putInt("islandPillWidth", s.islandPillWidth)
        putInt("islandPillHeight", s.islandPillHeight)
        putInt("islandPillScale", s.islandPillScale)
        putInt("islandExpWidth", s.islandExpWidth)
        putInt("islandExpHeight", s.islandExpHeight)
        putInt("islandExpScale", s.islandExpScale)
    }

    fun register(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(l)

    fun unregister(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(l)

    fun reset() = prefs.edit { clear() }

    /** Feature sets are stored as "NAME,NAME"; unknown names (from other versions) are skipped. */
    private inline fun <reified E : Enum<E>> features(key: String, fallback: Set<E>): Set<E> {
        val raw = prefs.getString(key, null) ?: return fallback
        return raw.split(',').mapNotNull { n -> enumValues<E>().firstOrNull { it.name == n } }.toSet()
    }

    private fun legacyDisplay(): AppDisplay? =
        if (prefs.contains("showIcons") && !prefs.getBoolean("showIcons", true)) AppDisplay.NAME_ONLY else null

    /** Older versions stored Thin/Regular/Bold as text. */
    private fun legacyWeight(): Int? = when (runCatching { prefs.getString("clockWeight", null) }.getOrNull()) {
        "LIGHT" -> 300
        "REGULAR" -> 400
        "BOLD" -> 700
        else -> null
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: fallback
}
