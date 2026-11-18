package com.cascade.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import java.text.Collator
import java.util.concurrent.ConcurrentHashMap

data class AppEntry(
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
) {
    /** Stable key used for favorites + icon cache. */
    val key: String = "${component.flattenToString()}#${user.hashCode()}"
    val packageName: String get() = component.packageName
}

/** An icon plus a flag telling the UI whether it should be tinted (monochrome silhouette). */
data class AppIcon(val bitmap: ImageBitmap, val tintable: Boolean)

class AppRepository(private val context: Context) {

    val launcherApps = context.getSystemService(LauncherApps::class.java)!!
    private val prefs = context.getSharedPreferences("cascade", Context.MODE_PRIVATE)
    private val iconCache = ConcurrentHashMap<String, AppIcon>()
    /** Activity infos from the last app scan, so loading an icon doesn't need another system call. */
    private val infos = ConcurrentHashMap<String, LauncherActivityInfo>()

    fun loadApps(): List<AppEntry> {
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        val profiles = launcherApps.profiles.ifEmpty { listOf(Process.myUserHandle()) }
        val all = profiles
            .flatMap { user -> launcherApps.getActivityList(null, user) }
            .filter { it.componentName.packageName != context.packageName }
        infos.clear()
        return all
            .map { info -> AppEntry(info.label.toString().trim(), info.componentName, info.user).also { infos[it.key] = info } }
            .distinctBy { it.key }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    // ---------- Icons ----------

    fun clearIconCache() = iconCache.clear()

    /** Instant cache lookup (no loading) so already-loaded icons show on the very first frame. */
    fun peekIcon(app: AppEntry, sizePx: Int, monochrome: Boolean): AppIcon? =
        iconCache["${app.key}|$monochrome|$sizePx"]

    /** Load every icon in the background so the drawer opens with all icons ready. */
    fun preloadIcons(apps: List<AppEntry>, sizePx: Int, monochrome: Boolean) {
        apps.forEach { runCatching { loadIcon(it, sizePx, monochrome) } }
    }

    fun loadIcon(app: AppEntry, sizePx: Int, monochrome: Boolean): AppIcon? {
        val cacheKey = "${app.key}|$monochrome|$sizePx"
        iconCache[cacheKey]?.let { return it }
        val info = resolve(app) ?: return null
        val drawable = runCatching { info.getIcon(context.resources.displayMetrics.densityDpi) }
            .getOrNull() ?: return null

        val icon = if (monochrome) {
            monochromeBitmap(drawable, sizePx)?.let { AppIcon(it.asImageBitmap(), tintable = true) }
                ?: AppIcon(drawable.toSizedBitmap(sizePx).asImageBitmap(), tintable = false)
        } else {
            AppIcon(drawable.toSizedBitmap(sizePx).asImageBitmap(), tintable = false)
        }
        iconCache[cacheKey] = icon
        return icon
    }

    private fun resolve(app: AppEntry): LauncherActivityInfo? =
        infos[app.key] ?: launcherApps.getActivityList(app.packageName, app.user)
            .firstOrNull { it.componentName == app.component }

    /** Android 13+ themed-icon layer, rendered as a white silhouette we can tint in Compose. */
    private fun monochromeBitmap(drawable: Drawable, size: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val mono = (drawable as? AdaptiveIconDrawable)?.monochrome ?: return null
        val bmp = createBitmap(size, size)
        val canvas = Canvas(bmp)
        // Adaptive layers are 108dp with the visible area being the inner 72dp (1.5x).
        val pad = size / 4
        mono.setBounds(-pad, -pad, size + pad, size + pad)
        mono.draw(canvas)
        return bmp
    }

    private fun Drawable.toSizedBitmap(size: Int): Bitmap {
        val bmp = createBitmap(size, size)
        val canvas = Canvas(bmp)
        val old = copyBounds()
        setBounds(0, 0, size, size)
        draw(canvas)
        bounds = old
        return bmp
    }

    // ---------- Favorites (ordered) ----------

    fun favoriteKeys(): List<String>? =
        prefs.getString(KEY_FAVS, null)?.split('\n')?.filter { it.isNotBlank() }

    fun saveFavorites(keys: List<String>) = prefs.edit { putString(KEY_FAVS, keys.joinToString("\n")) }

    fun defaultFavorites(apps: List<AppEntry>): List<String> {
        val preferred = listOf(
            "com.google.android.youtube", "com.spotify.music", "com.whatsapp",
            "com.instagram.android", "com.reddit.frontpage", "com.android.chrome",
            "com.google.android.apps.maps", "com.google.android.gm",
            "com.google.android.GoogleCamera", "com.android.camera2",
        )
        val picked = preferred.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }.toMutableList()
        for (app in apps) {
            if (picked.size >= 7) break
            if (app !in picked) picked += app
        }
        return picked.take(7).map { it.key }
    }

    companion object {
        private const val KEY_FAVS = "favorites"
    }
}
