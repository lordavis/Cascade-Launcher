# Cascade Launcher

A minimal, text-first Android home screen launcher inspired by Niagara Launcher, with an
iPhone-style lock-screen clock and a home-screen Dynamic Island for music.

Built with Kotlin and Jetpack Compose. **Fully offline** — the app has no internet permission
and never talks to the network or nearby devices.

## Features

### Home screen
- Vertical list of your favourite apps, within thumb reach
- Show **icon + name**, **name only**, or **icon only** (same layout, the text just hides)
- Move the app list, clock and island anywhere with ← ↑ ↓ → controls
- Swipe up anywhere to open the app drawer; configurable swipe up / swipe down / double-tap
- Darken the wallpaper with a live-preview slider
- Optionally hide the status bar and/or navigation bar on the home screen only
- Smooth ease-in of the home content when returning from an app

### Clock
- Centred, iPhone lock-screen style date + time
- Style presets (Classic, Bold, Thin, Rounded, Serif, Stencil, Dot Matrix, Future…)
- Continuous thickness, size, date size, colour and numeral style
- 16 font options (11 bundled fonts plus system styles)
- Battery line under the clock; while charging it alternates the charging speed and time to full

### Dynamic Island (music)
- Shows what's playing (Spotify or any media app) **on the home screen only** — it never draws
  over other apps
- Three stages — Small → Medium → Large — with smooth morphing between them, and automatic
  shrink-back
- Per-stage width, height, size, presets, feature toggles and an inward black border
- Colourful visualiser with colour presets (album colours, rainbow, neon, sunset…), frozen when
  paused
- Medium stage extras: play/pause, progress, times, static or dynamic album glow and more
- Large stage: album art, background blur, seek bar, controls
- Its own font option, with a live preview in Settings
- When Spotify isn't running, a small Spotify button takes the island's place, with 14
  hand-drawn styles that follow your clock colour and a see-through slider

### App drawer
- Alphabetical list with section headers and an alphabet scrubber
- Search, hidden apps, adjustable background opacity

## Install

Download the APK from the [Releases](../../releases) page, or build it yourself (below) and
install it with:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Then press Home and choose **Cascade**, or set it under
*Settings → Apps → Default apps → Home app*.

To show what's playing in the island, allow **Notification access** for Cascade when asked
(Settings → Dynamic Island). If Android greys it out as a "restricted setting", open Cascade's
App info → ⋮ → *Allow restricted settings* first.

## Build

Requirements: JDK 17 and the Android SDK (compileSdk 36).

```bash
# Point Gradle at your Android SDK once (this file is not committed)
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

./gradlew assembleRelease
```

The release build is minified with R8. For convenience it is signed with the local **debug**
key, so it installs over earlier debug builds without losing settings. Use your own signing
config before publishing to a store.

- Min Android version: 8.0 (API 26)
- Target: Android 16 (API 36)

## Permissions and privacy

| Permission | Why |
|---|---|
| `QUERY_ALL_PACKAGES` | List your installed apps (every launcher needs this) |
| `REQUEST_DELETE_PACKAGES` | The "Uninstall" option in an app's long-press menu |
| `EXPAND_STATUS_BAR` | Optional swipe-down gesture that opens the notification shade |
| Notification access (optional) | Read the local media session to show what's playing in the island |

The manifest explicitly **removes** internet, network state, Wi-Fi, Bluetooth, NFC, location
and draw-over-other-apps permissions, even if a library tries to add them. Backups are
disabled. Nothing leaves your phone.

## Project layout

```
app/src/main/java/com/cascade/launcher/
  MainActivity.kt     launcher activity, app launching, system bars, lifecycle
  LauncherScreen.kt   home screen, clock, app list, app drawer
  IslandService.kt    notification listener that reads local media sessions
  IslandUi.kt         Dynamic Island stages, visualiser, Spotify button styles
  SettingsScreen.kt   all settings pages and live previews
  PositionEditor.kt   on-screen editors (move, darken, island sizes)
  Settings.kt         settings model and storage
  ClockStyle.kt       clock presets, colours, numerals
  Fonts.kt            bundled font families
  Battery.kt          battery and charging info
  AppRepository.kt    app list, icons, favourites
```

## Credits

All bundled fonts are licensed under the SIL Open Font License 1.1 — see
[`third_party/fonts`](third_party/fonts) for each font's copyright and licence: Inter, Nunito,
Oswald, Outfit, Orbitron, Space Grotesk, Playfair Display, Kaushan Script, Bebas Neue, Doto and
Big Shoulders Stencil Display.

Spotify is a trademark of Spotify AB. This project is not affiliated with or endorsed by
Spotify; the Spotify button artwork is drawn in code.
