package com.cascade.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import kotlin.math.abs

/** How fast the phone is charging, using the same rules as Android's own lock screen. */
enum class ChargeSpeed(val label: String) {
    RAPID("Charging rapidly"),
    NORMAL("Charging"),
    SLOW("Charging slowly"),
}

data class BatteryState(
    val level: Int = 0,
    val charging: Boolean = false,
    val full: Boolean = false,
    val speed: ChargeSpeed = ChargeSpeed.NORMAL,
    /** Milliseconds until full, or null while it can't be worked out yet. */
    val timeToFullMs: Long? = null,
)

/**
 * Battery level + charging details, updated live from the system's battery broadcast.
 * All local: reads the phone's own battery state, nothing else.
 */
@Composable
fun rememberBattery(): BatteryState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(BatteryState()) }
    val bm = remember { context.getSystemService(BatteryManager::class.java) }
    // Our own estimate, used when Android can't give a time to full.
    val samples = remember { ArrayDeque<Pair<Long, Int>>() }

    DisposableEffect(context) {
        fun parse(i: Intent?) {
            i ?: return
            val raw = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (raw < 0 || scale <= 0) return
            val level = raw * 100 / scale
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            val full = status == BatteryManager.BATTERY_STATUS_FULL || (plugged && level >= 100)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || (plugged && full)

            if (!charging) samples.clear()
            else if (samples.lastOrNull()?.second != level) {
                samples.addLast(SystemClock.elapsedRealtime() to level)
                while (samples.size > 12) samples.removeFirst()
            }

            state = BatteryState(
                level = level,
                charging = charging,
                full = full,
                speed = chargeSpeed(i, bm),
                timeToFullMs = if (charging && !full) timeToFull(bm, samples, level) else null,
            )
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) = parse(i)
        }
        parse(context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Refresh the time estimate every 30 s while charging (the broadcast only fires on changes).
    LaunchedEffect(state.charging) {
        while (state.charging && !state.full) {
            delay(30_000)
            state = state.copy(timeToFullMs = timeToFull(bm, samples, state.level), speed = state.speed)
        }
    }
    return state
}

/**
 * Android's own lock screen works out charging speed from the charger's max current × voltage:
 * under 5 W is "slowly", over 7.5 W is "rapidly". Those values ride along in the battery
 * broadcast. If a phone doesn't report them, fall back to the current flowing in right now.
 */
private fun chargeSpeed(i: Intent, bm: BatteryManager?): ChargeSpeed {
    val maxMicroAmp = i.getIntExtra("max_charging_current", -1)
    var maxMicroVolt = i.getIntExtra("max_charging_voltage", -1)
    if (maxMicroAmp > 0) {
        if (maxMicroVolt <= 0) maxMicroVolt = 5_000_000
        val microWatt = (maxMicroAmp / 1000L) * (maxMicroVolt / 1000L)
        return when {
            microWatt <= 0 -> ChargeSpeed.NORMAL
            microWatt < 5_000_000 -> ChargeSpeed.SLOW
            microWatt > 7_500_000 -> ChargeSpeed.RAPID
            else -> ChargeSpeed.NORMAL
        }
    }
    val now = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
    if (now == Int.MIN_VALUE || now == 0) return ChargeSpeed.NORMAL
    // Most phones report microamps; a few report milliamps.
    val milliAmp = abs(now).let { if (it > 20_000) it / 1000 else it }
    return when {
        milliAmp >= 1500 -> ChargeSpeed.RAPID
        milliAmp < 500 -> ChargeSpeed.SLOW
        else -> ChargeSpeed.NORMAL
    }
}

/** Android's estimate if it has one, otherwise our own from how fast the % has been rising. */
private fun timeToFull(bm: BatteryManager?, samples: ArrayDeque<Pair<Long, Int>>, level: Int): Long? {
    if (Build.VERSION.SDK_INT >= 28) {
        val sys = bm?.computeChargeTimeRemaining() ?: -1
        if (sys > 0) return sys
    }
    if (samples.size < 2) return null
    val (t0, l0) = samples.first()
    val (t1, l1) = samples.last()
    if (l1 <= l0 || t1 <= t0) return null
    val msPerPercent = (t1 - t0).toDouble() / (l1 - l0)
    return (msPerPercent * (100 - level)).toLong()
}

fun formatDuration(ms: Long): String {
    val totalMin = ((ms + 59_999) / 60_000).coerceAtLeast(1)
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h == 0L -> "$m min"
        m == 0L -> "$h h"
        else -> "$h h $m min"
    }
}

/**
 * The text next to the battery icon. While charging it switches every few seconds between
 * the charging speed and the time left until full (only that part animates).
 */
@Composable
fun BatteryStatusText(battery: BatteryState, font: FontFamily) {
    val lines: List<String> = when {
        battery.full -> listOf("Fully charged")
        battery.charging -> listOf(
            battery.speed.label,
            battery.timeToFullMs?.let { "Full in ${formatDuration(it)}" } ?: "Estimating time to full…",
        )
        else -> emptyList()
    }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(lines.size, battery.charging) {
        index = 0
        while (lines.size > 1) {
            delay(SWITCH_EVERY_MS)
            index = (index + 1) % lines.size
        }
    }
    val right = lines.getOrNull(index % lines.size.coerceAtLeast(1))

    // Only the part after the dot animates; "73%  ·" stays perfectly still.
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
        StatusPart("${battery.level}%" + if (right != null) "  ·  " else "", font)
        if (right != null) {
            AnimatedContent(
                targetState = right,
                transitionSpec = {
                    (fadeIn(tween(450)) + slideInVertically(tween(450)) { it / 3 })
                        .togetherWith(fadeOut(tween(350)))
                },
                contentAlignment = Alignment.CenterStart,
                label = "battery",
            ) { t -> StatusPart(t, font) }
        }
    }
}

/** How long each charging detail stays on screen before switching. */
private const val SWITCH_EVERY_MS = 5000L

@Composable
private fun StatusPart(text: String, font: FontFamily) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.9f),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = font,
        maxLines = 1,
    )
}
