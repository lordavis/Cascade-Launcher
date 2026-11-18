package com.cascade.launcher

import android.graphics.Typeface as AndroidTypeface
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface

/**
 * Turns an [AppFont] choice into a Compose [FontFamily]. Every font is either built into
 * Android or bundled inside the APK (res/font), so nothing is ever downloaded.
 */
private val cache = mutableMapOf<AppFont, FontFamily>()
private val weightCache = mutableMapOf<Pair<AppFont, Int>, FontFamily>()

/** Bundled variable fonts: these can be any thickness, not just a few fixed weights. */
private val variableRes: Map<AppFont, Int> = mapOf(
    AppFont.INTER to R.font.inter,
    AppFont.ROUNDED to R.font.nunito,
    AppFont.STENCIL to R.font.stencil,
    AppFont.RAILS to R.font.oswald,
    AppFont.DOT to R.font.doto,
    AppFont.OUTFIT to R.font.outfit,
    AppFont.GROTESK to R.font.space_grotesk,
    AppFont.ORBITRON to R.font.orbitron,
    AppFont.PLAYFAIR to R.font.playfair,
)

val AppFont.isVariable: Boolean get() = this in variableRes

fun AppFont.family(): FontFamily = cache.getOrPut(this) {
    variableRes[this]?.let { return@getOrPut variable(it) }
    when (this) {
        AppFont.SYSTEM -> FontFamily.SansSerif
        AppFont.THIN -> system("sans-serif-thin")
        AppFont.CONDENSED -> system("sans-serif-condensed")
        AppFont.SERIF -> FontFamily.Serif
        AppFont.MONO -> FontFamily.Monospace
        AppFont.BEBAS -> FontFamily(Font(R.font.bebas_neue))
        AppFont.BRUSH -> FontFamily(Font(R.font.kaushan))
        else -> FontFamily.SansSerif
    }
}

/**
 * Family at an exact thickness (100–900). Variable fonts get a true instance at that weight,
 * so the thickness slider moves smoothly like the iPhone's; other fonts use the nearest weight.
 */
@OptIn(ExperimentalTextApi::class)
fun AppFont.familyAt(weight: Int): FontFamily {
    val res = variableRes[this] ?: return family()
    val w = (weight / 10 * 10).coerceIn(100, 900)
    return weightCache.getOrPut(this to w) {
        FontFamily(
            Font(res, weight = FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))),
        )
    }
}

private fun system(name: String) = FontFamily(Typeface(AndroidTypeface.create(name, AndroidTypeface.NORMAL)))

/** Variable fonts: register a real instance per weight so Thin…Black all look right. */
@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int): FontFamily = FontFamily(
    (1..9).map { it * 100 }.map { w ->
        Font(
            res,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    },
)
