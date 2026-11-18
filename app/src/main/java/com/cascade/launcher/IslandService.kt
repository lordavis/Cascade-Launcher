package com.cascade.launcher

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.compose.runtime.mutableStateOf
import androidx.core.graphics.scale

/** What the island shows. */
data class MediaInfo(
    val title: String,
    val artist: String,
    val art: Bitmap?,
    /** A few vivid colors pulled from the album art (for the "Album colors" visualizer). */
    val artColors: List<Int>,
    val playing: Boolean,
    val durationMs: Long,
    val positionMs: Long,
    val positionUpdatedAt: Long,   // SystemClock.elapsedRealtime() at positionMs
    val speed: Float,
    val packageName: String,
    val appLabel: String,
) {
    fun currentPosition(nowElapsed: Long = SystemClock.elapsedRealtime()): Long {
        if (!playing || positionMs < 0) return positionMs.coerceAtLeast(0)
        val p = positionMs + ((nowElapsed - positionUpdatedAt) * speed).toLong()
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

/**
 * Shared "now playing" state. The [IslandService] fills it in; the launcher's home screen
 * draws the island from it. Nothing is ever drawn over other apps.
 */
object NowPlaying {
    val media = mutableStateOf<MediaInfo?>(null)
    internal var service: IslandService? = null

    val connected: Boolean get() = service != null

    fun playPause() = service?.playPause()
    fun next() = service?.next()
    fun previous() = service?.previous()
    fun seekTo(ms: Long) = service?.seekTo(ms)

    /** Opens the music app (Spotify etc.). Returns false if nothing could be opened. */
    fun openPlayer(context: Context): Boolean = service?.openPlayerApp(context) ?: false
}

/**
 * A notification listener. Android only lets apps read the active media sessions (what song is
 * playing, play/pause controls) through a notification listener, so that's all this does —
 * it reads local media sessions on the phone; it has no network access and draws nothing.
 */
class IslandService : NotificationListenerService() {

    companion object {
        const val SPOTIFY = "com.spotify.music"
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: SettingsStore
    private var settings = LauncherSettings()
    private var sessionManager: MediaSessionManager? = null
    /** Every session we're watching, each with its own callback so we know which one died. */
    private val controllers = mutableMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private var controller: MediaController? = null
    private var connected = false

    private var lastArt: Bitmap? = null
    private var lastArtColors: List<Int> = emptyList()

    private val listenerComponent by lazy { ComponentName(this, IslandService::class.java) }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        settings = store.load()
        pickController()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        onSessions(list.orEmpty())
    }

    private fun callbackFor(token: MediaSession.Token) = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = pickController()
        override fun onMetadataChanged(metadata: MediaMetadata?) = pickController()
        override fun onSessionDestroyed() {
            // The app really closed (e.g. Spotify swiped away): forget it.
            drop(token)
            pickController()
        }
    }

    private fun drop(token: MediaSession.Token) {
        controllers.remove(token)?.let { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
        if (controller?.sessionToken == token) controller = null
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        NowPlaying.service = this
        store = SettingsStore(applicationContext)
        settings = store.load()
        store.register(prefListener)
        sessionManager = getSystemService(MediaSessionManager::class.java)
        try {
            sessionManager?.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
            onSessions(sessionManager?.getActiveSessions(listenerComponent).orEmpty())
        } catch (_: SecurityException) {
            // Notification access not granted (yet).
        }
    }

    override fun onListenerDisconnected() {
        teardown()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        if (!connected) return
        connected = false
        runCatching { store.unregister(prefListener) }
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        controllers.values.forEach { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
        controllers.clear()
        controller = null
        NowPlaying.media.value = null
        if (NowPlaying.service === this) NowPlaying.service = null
    }

    /** Re-evaluate (called from the launcher on resume). */
    fun refresh() = handler.post { pickController() }

    // ------------------------------------------------------------------ media sessions

    private fun onSessions(list: List<MediaController>) {
        val newTokens = list.associateBy { it.sessionToken }
        controllers.keys.filter { it !in newTokens }.forEach { key ->
            // Some apps (Spotify included) mark their session "inactive" when paused, which drops
            // it from the active list. Keep watching a paused session as long as the app keeps it
            // alive, so the island stays put instead of vanishing on pause.
            val (c, _) = controllers.getValue(key)
            val state = runCatching { c.playbackState }.getOrNull()
            if (!(isPaused(state) || isActive(state))) drop(key)
        }
        newTokens.forEach { (key, c) ->
            if (key !in controllers) {
                val cb = callbackFor(key)
                controllers[key] = c to cb
                c.registerCallback(cb, handler)
            }
        }
        pickController()
    }

    private fun isActive(state: PlaybackState?): Boolean = when (state?.state) {
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_CONNECTING,
        PlaybackState.STATE_FAST_FORWARDING,
        PlaybackState.STATE_REWINDING,
        PlaybackState.STATE_SKIPPING_TO_NEXT,
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> true
        else -> false
    }

    private fun isPaused(state: PlaybackState?) = state?.state == PlaybackState.STATE_PAUSED

    private fun pickController() {
        if (!connected) return
        val candidates = controllers.values.map { it.first }.filter {
            (!settings.islandSpotifyOnly || it.packageName == SPOTIFY) && it.metadata != null
        }
        val current = controller?.takeIf { c -> candidates.any { it.sessionToken == c.sessionToken } }
        controller = when {
            current != null && isActive(current.playbackState) -> current
            candidates.any { isActive(it.playbackState) } -> candidates.first { isActive(it.playbackState) }
            current != null && isPaused(current.playbackState) -> current
            else -> candidates.firstOrNull { isPaused(it.playbackState) }
        }
        refreshInfo()
    }

    private fun refreshInfo() {
        val c = controller
        val meta = c?.metadata
        val state = c?.playbackState
        if (c == null || meta == null || !(isActive(state) || isPaused(state))) {
            NowPlaying.media.value = null
            return
        }
        val art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (art !== lastArt) {
            lastArt = art
            lastArtColors = art?.let { vividColors(it) }.orEmpty()
        }
        val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(c.packageName, 0)).toString()
        }.getOrDefault(c.packageName)

        NowPlaying.media.value = MediaInfo(
            title = title,
            artist = artist,
            art = art,
            artColors = lastArtColors,
            playing = isActive(state),
            durationMs = meta.getLong(MediaMetadata.METADATA_KEY_DURATION),
            positionMs = state?.position ?: 0,
            positionUpdatedAt = state?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
            speed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            packageName = c.packageName,
            appLabel = label,
        )
    }

    /**
     * Up to 4 vivid, clearly different colors from the album art, brightened so they glow on
     * black. Falls back to hue-shifted versions of the strongest color for dull covers.
     */
    private fun vividColors(bmp: Bitmap): List<Int> {
        val small = runCatching { bmp.scale(12, 12) }.getOrNull() ?: return emptyList()
        val hsvs = mutableListOf<FloatArray>()
        for (y in 0 until small.height) for (x in 0 until small.width) {
            val hsv = FloatArray(3)
            AColor.colorToHSV(small.getPixel(x, y), hsv)
            hsvs += hsv
        }
        val ranked = hsvs.filter { it[1] > 0.22f && it[2] > 0.2f }.sortedByDescending { it[1] * it[2] }
        val picked = mutableListOf<FloatArray>()
        for (c in ranked) {
            if (picked.size >= 4) break
            if (picked.none { hueDistance(it[0], c[0]) < 28f }) picked += c
        }
        if (picked.isEmpty()) {
            picked += hsvs.maxByOrNull { it[1] } ?: floatArrayOf(330f, 0.8f, 1f)
        }
        val base = picked.first()
        var shift = 40f
        while (picked.size < 3) {
            picked += floatArrayOf((base[0] + shift) % 360f, base[1], base[2])
            shift += 40f
        }
        return picked.map { hsv ->
            AColor.HSVToColor(floatArrayOf(hsv[0], hsv[1].coerceIn(0.55f, 1f), hsv[2].coerceAtLeast(0.85f)))
        }
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val d = kotlin.math.abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }

    // ------------------------------------------------------------------ controls

    fun playPause() {
        val c = controller ?: return
        if (isActive(c.playbackState)) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() = controller?.transportControls?.skipToNext()
    fun previous() = controller?.transportControls?.skipToPrevious()
    fun seekTo(ms: Long) = controller?.transportControls?.seekTo(ms)

    fun openPlayerApp(context: Context): Boolean {
        val c = controller ?: return false
        return try {
            val pi = c.sessionActivity
            if (pi != null) {
                sendPendingIntent(pi)
            } else {
                val launch = packageManager.getLaunchIntentForPackage(c.packageName) ?: return false
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun sendPendingIntent(pi: PendingIntent) {
        if (Build.VERSION.SDK_INT >= 34) {
            val opts = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            pi.send(this, 0, null, null, null, null, opts.toBundle())
        } else {
            pi.send()
        }
    }
}
