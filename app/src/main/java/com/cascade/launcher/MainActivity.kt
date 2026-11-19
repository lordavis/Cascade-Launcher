package com.cascade.launcher

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.UserHandle
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    lateinit var repo: AppRepository
        private set
    private lateinit var store: SettingsStore

    /** Every installed launchable app (including hidden ones). */
    var allApps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var favoriteKeys by mutableStateOf<List<String>>(emptyList())
        private set
    var settings by mutableStateOf(LauncherSettings())
        private set

    var drawerOpen by mutableStateOf(false)
    var drawerFocusSearch by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)

    /** True while the background-darkness slider is shown over the home screen. */
    var darkenEditing by mutableStateOf(false)
        private set

    /** Settings page to open next time settings is shown (e.g. after a live preview). */
    var settingsReturnPage: String? = null

    /** Non-null while the island size editor is open (which stage is being sized). */
    var islandSizeStage by mutableStateOf<IslandStage?>(null)

    fun startIslandSizing() {
        settingsOpen = false
        drawerOpen = false
        stopEditing()
        darkenEditing = false
        islandSizeStage = IslandStage.MINI
    }

    fun finishIslandSizing() {
        islandSizeStage = null
        settingsReturnPage = "ISLAND"
        settingsOpen = true
    }

    /** Non-null while the on-screen position editor is open. */
    var editTarget by mutableStateOf<EditTarget?>(null)
        private set

    /** Bumped on every resume so permission status rows re-check themselves. */
    var resumeTick by mutableIntStateOf(0)
    /** Home content entrance, 0 → 1. Reset when you come back from an app, then eased in. */
    var homeEnter by androidx.compose.runtime.mutableFloatStateOf(1f)
    var returnTick by mutableIntStateOf(0)
        private set

    /** Bumped whenever icons need to be re-fetched (package updates, setting changes). */
    var iconGeneration by mutableIntStateOf(0)
        private set

    /** Apps shown in the drawer (hidden apps removed). */
    val drawerApps: List<AppEntry> get() = allApps.filter { it.key !in settings.hiddenApps }

    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackagesAvailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = refresh()
        override fun onPackagesUnavailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        repo = AppRepository(applicationContext)
        store = SettingsStore(applicationContext)
        settings = store.load()
        repo.launcherApps.registerCallback(packageCallback)
        refresh()

        setContent { LauncherScreen(this) }
        watchKeyboard()
    }

    // ---------- Keyboard vs. hidden system bars ----------

    /** True while the keyboard is sliding in or out. */
    private var imeAnimating = false
    /** When the keyboard was last seen on screen (uptime ms). */
    private var imeSeenAt = 0L

    /**
     * Gboard's window is sized around the status bar. If we hide the status bar while the keyboard
     * is still on screen (e.g. swiping home with the drawer's search open), its window resizes
     * mid-animation and a light strip of it flashes at the top. So we track the keyboard and only
     * hide the bars once it has completely gone.
     */
    private fun watchKeyboard() {
        val ime = androidx.core.view.WindowInsetsCompat.Type.ime()
        androidx.core.view.ViewCompat.setWindowInsetsAnimationCallback(
            window.decorView,
            object : androidx.core.view.WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                override fun onPrepare(animation: androidx.core.view.WindowInsetsAnimationCompat) {
                    if (animation.typeMask and ime != 0) {
                        imeAnimating = true
                        imeSeenAt = android.os.SystemClock.uptimeMillis()
                    }
                }
                override fun onProgress(
                    insets: androidx.core.view.WindowInsetsCompat,
                    running: MutableList<androidx.core.view.WindowInsetsAnimationCompat>,
                ) = insets
                override fun onEnd(animation: androidx.core.view.WindowInsetsAnimationCompat) {
                    if (animation.typeMask and ime != 0) {
                        imeAnimating = false
                        imeSeenAt = android.os.SystemClock.uptimeMillis()
                        applySystemBars()
                    }
                }
            },
        )
    }

    private fun keyboardOnScreen(): Boolean {
        val up = androidx.core.view.ViewCompat.getRootWindowInsets(window.decorView)
            ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        val now = android.os.SystemClock.uptimeMillis()
        if (up) imeSeenAt = now
        // A short grace period after it's gone: its window can still be animating away.
        return up || imeAnimating || now - imeSeenAt < 400
    }

    private val retryBars = Runnable { applySystemBars() }

    /** True when Cascade is the phone's chosen home app. */
    fun isDefaultHome(): Boolean = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val roles = getSystemService(android.app.role.RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                return@runCatching roles.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)
            }
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == packageName
    }.getOrDefault(true)

    override fun onStop() {
        super.onStop()
        // When another launcher is the default, don't linger as a second home screen in the
        // background: the system's swipe-home animation targets the real default launcher, and
        // a leftover Cascade home task sitting on top of it leaves the app card frozen on screen.
        // Never while the screen is off or locked: that is the launcher simply going to sleep,
        // and finishing then would leave the phone with no home screen when it wakes up.
        val power = getSystemService(android.os.PowerManager::class.java)
        val keyguard = getSystemService(android.app.KeyguardManager::class.java)
        val asleep = power?.isInteractive == false || keyguard?.isKeyguardLocked == true
        if (!isChangingConfigurations && !asleep && !isDefaultHome()) finishAndRemoveTask()
    }

    override fun onRestart() {
        super.onRestart()
        // Set synchronously (no coroutine) so the very first frame back is already the start
        // of the entrance, then the home screen animates it in while the app slides away.
        // A plain state write: never block the main thread here. (The old runBlocking +
        // Animatable could wait forever on an entrance animation that was paused while the
        // screen was off, freezing the launcher on a black screen.)
        homeEnter = 0f
        returnTick++
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        // Safety net: the home content must always end up fully visible.
        window.decorView.postDelayed({ if (homeEnter < 1f) homeEnter = 1f }, 900)
        applySystemBars()
        // Let the island pick up permission changes made in system settings.
        NowPlaying.service?.refresh()
        if (settings.islandEnabled && hasNotificationAccess() && NowPlaying.service == null) {
            runCatching {
                android.service.notification.NotificationListenerService.requestRebind(
                    android.content.ComponentName(this, IslandService::class.java),
                )
            }
        }
    }

    override fun onDestroy() {
        repo.launcherApps.unregisterCallback(packageCallback)
        super.onDestroy()
    }

    /** Pressing Home while already on the launcher returns to the home screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (Intent.ACTION_MAIN == intent.action) {
            closeDrawerAfterKeyboard()
            settingsOpen = false
            stopEditing()
            darkenEditing = false
            islandSizeStage = null
        }
    }

    /**
     * Swiping home with the drawer's keyboard up: lower the keyboard first and only then close
     * the drawer. Doing both in the same instant makes the system flash a white bar at the top.
     */
    private fun closeDrawerAfterKeyboard() {
        if (!drawerOpen) return
        val root = window.decorView
        val ime = androidx.core.view.WindowInsetsCompat.Type.ime()
        fun imeUp() = androidx.core.view.ViewCompat.getRootWindowInsets(root)?.isVisible(ime) == true
        if (!imeUp()) {
            drawerOpen = false
            return
        }
        currentFocus?.clearFocus()
        androidx.core.view.WindowCompat.getInsetsController(window, root).hide(ime)
        lifecycleScope.launch {
            kotlinx.coroutines.withTimeoutOrNull(500) {
                while (imeUp()) kotlinx.coroutines.delay(16)
            }
            drawerOpen = false
        }
    }

    fun refresh() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { repo.loadApps() }
            repo.clearIconCache()
            allApps = loaded
            val saved = repo.favoriteKeys()
            favoriteKeys = saved ?: repo.defaultFavorites(loaded).also { repo.saveFavorites(it) }
            iconGeneration++
            preloadIcons()
        }
    }

    /** Icon bitmap size used everywhere (must match AppIconImage). */
    val iconSizePx: Int get() = (40 * resources.displayMetrics.density).toInt()

    /** Warm the icon cache off the main thread so the app drawer never waits for icons. */
    fun preloadIcons() {
        val apps = allApps
        val mono = settings.monochromeIcons
        lifecycleScope.launch(Dispatchers.Default) { repo.preloadIcons(apps, iconSizePx, mono) }
    }

    // ---------- Navigation ----------

    fun openDrawer(focusSearch: Boolean) {
        drawerFocusSearch = focusSearch || settings.autoKeyboard
        drawerOpen = true
    }

    fun openSettings() {
        drawerOpen = false
        settingsOpen = true
    }

    // ---------- Position editor ----------

    fun startEditing(target: EditTarget) {
        settingsOpen = false
        drawerOpen = false
        selectEditTarget(target)
    }

    fun selectEditTarget(target: EditTarget) {
        editTarget = target
    }

    fun startDarkenPreview() {
        settingsOpen = false
        drawerOpen = false
        stopEditing()
        darkenEditing = true
    }

    /** Close the darkness slider and go back to Settings → Appearance. */
    fun finishDarkenPreview() {
        darkenEditing = false
        settingsReturnPage = "APPEARANCE"
        settingsOpen = true
    }

    fun stopEditing() {
        if (editTarget == null) return
        editTarget = null
    }

    /** Move the selected element by (dx, dy) dp. */
    fun nudge(dx: Int, dy: Int) = updateSettings {
        when (editTarget) {
            EditTarget.APPS -> it.copy(listX = (it.listX + dx).coerceIn(-28, 400), listY = (it.listY + dy).coerceIn(-1200, 400))
            EditTarget.CLOCK -> it.copy(clockX = (it.clockX + dx).coerceIn(-400, 400), clockY = (it.clockY + dy).coerceIn(-200, 1200))
            EditTarget.ISLAND -> it.copy(islandX = (it.islandX + dx).coerceIn(-500, 500), islandY = (it.islandY + dy).coerceIn(-400, 900))
            null -> it
        }
    }

    fun resetPosition() = updateSettings {
        when (editTarget) {
            EditTarget.APPS -> it.copy(listX = 0, listY = 0)
            EditTarget.CLOCK -> it.copy(clockX = 0, clockY = 0)
            EditTarget.ISLAND -> it.copy(islandX = 0, islandY = 0)
            null -> it
        }
    }

    fun currentOffset(): Pair<Int, Int> = when (editTarget) {
        EditTarget.APPS -> settings.listX to settings.listY
        EditTarget.CLOCK -> settings.clockX to settings.clockY
        EditTarget.ISLAND -> settings.islandX to settings.islandY
        null -> 0 to 0
    }

    fun runGesture(action: GestureAction) {
        when (action) {
            GestureAction.NONE -> Unit
            GestureAction.DRAWER -> openDrawer(false)
            GestureAction.SEARCH -> openDrawer(true)
            GestureAction.NOTIFICATIONS -> expandNotifications()
        }
    }

    // ---------- Hiding the system bars (Cascade's own screen only) ----------

    /**
     * Hides the status bar and/or navigation bar on Cascade's home screen only, per settings.
     * Swiping in from the edge brings them back for a moment. The app drawer and settings
     * always show them. Other apps are never affected; no permission is needed.
     */
    fun applySystemBars() {
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        val status = androidx.core.view.WindowInsetsCompat.Type.statusBars()
        val nav = androidx.core.view.WindowInsetsCompat.Type.navigationBars()
        // Hidden only on the home screen itself: the drawer and settings bring them back.
        val onHome = !settingsOpen && !drawerOpen
        val wantHide = onHome && (settings.hideStatusBar || settings.hideNavBar)
        if (wantHide && keyboardOnScreen()) {
            // Leave the bars as they are until the keyboard has fully gone, then try again.
            window.decorView.removeCallbacks(retryBars)
            window.decorView.postDelayed(retryBars, 120)
            return
        }
        if (settings.hideStatusBar && onHome) controller.hide(status) else controller.show(status)
        if (settings.hideNavBar && onHome) controller.hide(nav) else controller.show(nav)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars()
    }


    @SuppressLint("WrongConstant")
    private fun expandNotifications() {
        runCatching {
            val sb = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
        }.onFailure { toast("Can't open notifications") }
    }

    // ---------- Settings ----------

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        val old = settings
        val new = transform(old)
        settings = new
        store.save(new)
        if (old.monochromeIcons != new.monochromeIcons) {
            repo.clearIconCache()
            iconGeneration++
            preloadIcons()
        }
    }

    fun resetSettings() {
        store.reset()
        settings = LauncherSettings()
        repo.clearIconCache()
        iconGeneration++
    }

    fun isHidden(app: AppEntry) = app.key in settings.hiddenApps

    fun toggleHidden(app: AppEntry) = updateSettings {
        it.copy(hiddenApps = if (app.key in it.hiddenApps) it.hiddenApps - app.key else it.hiddenApps + app.key)
    }

    // ---------- Dynamic island permissions ----------

    fun hasNotificationAccess(): Boolean =
        androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    fun openNotificationAccess() {
        val component = android.content.ComponentName(this, IslandService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                return
            } catch (_: Exception) {
            }
        }
        safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    fun openOwnAppInfo() =
        safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))

    // ---------- Favorites ----------

    fun isFavorite(app: AppEntry) = app.key in favoriteKeys

    fun toggleFavorite(app: AppEntry) {
        favoriteKeys = if (isFavorite(app)) favoriteKeys - app.key else favoriteKeys + app.key
        repo.saveFavorites(favoriteKeys)
    }

    fun moveFavorite(app: AppEntry, delta: Int) {
        val list = favoriteKeys.toMutableList()
        val i = list.indexOf(app.key)
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list.add(j, list.removeAt(i))
        favoriteKeys = list
        repo.saveFavorites(list)
    }

    // ---------- Actions ----------

    fun launch(app: AppEntry, bounds: Rect? = null) {
        try {
            repo.launcherApps.startMainActivity(app.component, app.user, bounds, null)
        } catch (e: Exception) {
            toast("Can't open ${app.label}")
        }
    }

    fun openAppInfo(app: AppEntry) {
        runCatching { repo.launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }
    }

    fun uninstall(app: AppEntry) {
        safeStart(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
    }

    fun openClock() = safeStart(Intent(AlarmClock.ACTION_SHOW_ALARMS))

    fun openCalendar() = safeStart(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR))

    fun openBatterySettings() = safeStart(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))

    fun openWallpaperPicker() =
        safeStart(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Choose wallpaper"))

    fun openDefaultHomeSettings() = safeStart(Intent(Settings.ACTION_HOME_SETTINGS))

    val versionName: String
        get() = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"

    private fun safeStart(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            toast("No app found for that")
        } catch (e: SecurityException) {
            toast("Not allowed")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
