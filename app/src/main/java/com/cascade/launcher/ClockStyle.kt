package com.cascade.launcher

import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Digit systems for the time, like the iPhone lock screen offers. */
enum class Numerals(val label: String, private val zero: Int) {
    WESTERN("Western  1 2 3", '0'.code),
    ARABIC("Arabic  ١ ٢ ٣", 0x0660),
    DEVANAGARI("Devanagari  १ २ ३", 0x0966),
    BENGALI("Bengali  ১ ২ ৩", 0x09E6),
    THAI("Thai  ๑ ๒ ๓", 0x0E50),
    KHMER("Khmer  ១ ២ ៣", 0x17E0),
    BURMESE("Burmese  ၁ ၂ ၃", 0x1040);

    fun apply(text: String): String = if (this == WESTERN) text else buildString {
        text.forEach { c -> if (c in '0'..'9') appendCodePoint(zero + (c - '0')) else append(c) }
    }
}

/** One-tap clock looks, modelled on the iPhone lock-screen style picker. */
data class ClockPreset(val name: String, val font: AppFont, val weight: Int)

val ClockPresets = listOf(
    ClockPreset("Classic", AppFont.INTER, 600),
    ClockPreset("Bold", AppFont.INTER, 800),
    ClockPreset("Thin", AppFont.INTER, 200),
    ClockPreset("Rounded", AppFont.ROUNDED, 700),
    ClockPreset("Soft", AppFont.ROUNDED, 900),
    ClockPreset("Serif", AppFont.PLAYFAIR, 500),
    ClockPreset("Serif Heavy", AppFont.PLAYFAIR, 900),
    ClockPreset("Stencil", AppFont.STENCIL, 700),
    ClockPreset("Rails", AppFont.RAILS, 300),
    ClockPreset("Dot Matrix", AppFont.DOT, 700),
    ClockPreset("Display", AppFont.BEBAS, 400),
    ClockPreset("Future", AppFont.ORBITRON, 600),
)

/** Clock colours: plain white, iPhone-style "vibrant" translucent white, then tints. */
val ClockColors: List<Long> = listOf(
    0xFFFFFFFF, // White
    0xC8FFFFFF, // Vibrant (translucent, lets the wallpaper glow through)
    0xFFE8E4FF, // Lavender white (like the reference photo)
    0xFFFFE4D6, // Warm white
    0xFFFF9EB5, // Pink
    0xFFFFC27A, // Peach
    0xFFFFE27A, // Yellow
    0xFF9DF5B0, // Mint
    0xFF8FD8FF, // Sky
    0xFFB4A8FF, // Periwinkle
    0xFFD9A8FF, // Lilac
    0xFF1C1C1E, // Black (for light wallpapers)
)

private val ClockShadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 2f), 12f)

/**
 * The time, drawn with every clock setting (font, continuous thickness, colour, numerals).
 * Used by the home screen, the settings preview and the style tiles so they always match.
 */
@Composable
fun ClockTimeText(
    s: LauncherSettings,
    text: String,
    modifier: Modifier = Modifier,
    sizeSp: Float = s.clockSize.toFloat(),
    font: AppFont = s.clockFont,
    weight: Int = s.clockWeight,
    color: Color = Color(s.clockColor),
) {
    Text(
        text = s.clockNumerals.apply(text),
        color = color,
        maxLines = 1,
        softWrap = false,
        style = TextStyle(
            fontFamily = font.familyAt(weight),
            fontWeight = FontWeight(weight.coerceIn(1, 1000)),
            fontSize = sizeSp.sp,
            lineHeight = (sizeSp * 1.04f).sp,
            letterSpacing = (-sizeSp / 40f).sp,
            shadow = if (color.alpha > 0.9f && color.luminanceApprox() > 0.5f) ClockShadow else null,
        ),
        modifier = modifier.offset(y = (-sizeSp / 18f).dp),
    )
}

private fun Color.luminanceApprox() = 0.299f * red + 0.587f * green + 0.114f * blue
