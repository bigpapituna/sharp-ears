package com.bigpapituna.sharpears.ui

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.R

/*
 * Design system (taste-skill redesign, 2026-10-07)
 * Read: personal practice app for one adult learner at the piano; calm, instrument-like; Material 3 + Geist.
 * Dials: variance 4, motion 4, density 4.
 * Colour: cool slate neutrals, ONE accent (terracotta, from the logo). The lane's hand colours and the
 *   green/red key feedback are data, used only on the stage and keyboard, never as UI decoration.
 * Shape rule: interactive controls are full pills; surfaces (the one hero panel, sheets, dialogs) 20 dp;
 *   inner squares 12 dp; piano keys 6 px.
 * Type: Geist for everything, Geist Mono for times, scores and counts (tabular).
 */

private fun geist(weight: Int) = Font(R.font.geist, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
private fun geistMono(weight: Int) = Font(R.font.geist_mono, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Geist = FontFamily(geist(400), geist(500), geist(600), geist(700))
val GeistMono = FontFamily(geistMono(400), geistMono(500), geistMono(600))

/** Numbers that change or line up (times, percentages, counts). */
val Numeric = TextStyle(fontFamily = GeistMono, fontFeatureSettings = "tnum")

// The accent: terracotta, desaturated from the logo's orange so it sits with the slate greys.
val Accent = Color(0xFFE38E5A)
private val AccentDeep = Color(0xFFA9521F)          // light mode: 5:1 on the light background

// Stage colours (always dark): the lane, keyboard, and note feedback.
val RightHandColor = Color(0xFFE38E5A)
val LeftHandColor = Color(0xFF7FA6CF)
val Good = Color(0xFF6CC08B)
val Bad = Color(0xFFE5675F)
val Stage = Color(0xFF0E1014)
val StageRaised = Color(0xFF1A1E25)
val Ink = Color(0xFFECEEF2)
val InkDim = Color(0xFF9AA3AF)

private val darkColors = darkColorScheme(
    primary = Accent, onPrimary = Color(0xFF2A1406),
    primaryContainer = Color(0xFF3A2416), onPrimaryContainer = Color(0xFFF6D3BC),
    secondary = Color(0xFFB9C2CE), onSecondary = Color(0xFF1A1E25),
    secondaryContainer = Color(0xFF3A4350), onSecondaryContainer = Color(0xFFF4F6F9),   // selected segments, chips, nav
    tertiary = Good,
    background = Color(0xFF0F1115), onBackground = Ink,
    surface = Color(0xFF0F1115), onSurface = Ink,
    surfaceVariant = Color(0xFF1E232B), onSurfaceVariant = InkDim,
    surfaceContainerLowest = Color(0xFF0B0D10), surfaceContainerLow = Color(0xFF13161B),
    surfaceContainer = Color(0xFF171B21), surfaceContainerHigh = Color(0xFF1E232B), surfaceContainerHighest = Color(0xFF262C35),
    outline = Color(0xFF3A424D), outlineVariant = Color(0xFF262C35),
    error = Bad,
)

private val lightColors = lightColorScheme(
    primary = AccentDeep, onPrimary = Color(0xFFFFF7F2),
    primaryContainer = Color(0xFFF7E1D3), onPrimaryContainer = Color(0xFF3A1A08),
    secondary = Color(0xFF4A5361), onSecondary = Color(0xFFF7F8FA),
    secondaryContainer = Color(0xFFD3DAE2), onSecondaryContainer = Color(0xFF14171C),
    tertiary = Color(0xFF2F7D4C),
    background = Color(0xFFF4F5F7), onBackground = Color(0xFF14171C),
    surface = Color(0xFFF4F5F7), onSurface = Color(0xFF14171C),
    surfaceVariant = Color(0xFFE3E7EC), onSurfaceVariant = Color(0xFF5D6672),
    surfaceContainerLowest = Color(0xFFFBFBFC), surfaceContainerLow = Color(0xFFEFF1F4),
    surfaceContainer = Color(0xFFEAEDF1), surfaceContainerHigh = Color(0xFFE3E7EC), surfaceContainerHighest = Color(0xFFDCE1E7),
    outline = Color(0xFFB4BCC6), outlineVariant = Color(0xFFD5DAE0),
    error = Color(0xFFB3261E),
)

private val type = Typography().let { t ->
    fun TextStyle.g(w: Int, track: Double = 0.0) = copy(fontFamily = Geist, fontWeight = FontWeight(w), letterSpacing = track.em)
    t.copy(
        displayLarge = t.displayLarge.g(600, -0.03), displayMedium = t.displayMedium.g(600, -0.03), displaySmall = t.displaySmall.g(600, -0.02),
        headlineLarge = t.headlineLarge.g(600, -0.02), headlineMedium = t.headlineMedium.g(600, -0.02), headlineSmall = t.headlineSmall.g(600, -0.015),
        titleLarge = t.titleLarge.g(600, -0.01), titleMedium = t.titleMedium.g(500), titleSmall = t.titleSmall.g(500),
        bodyLarge = t.bodyLarge.g(400).copy(lineHeight = 24.sp), bodyMedium = t.bodyMedium.g(400).copy(lineHeight = 21.sp), bodySmall = t.bodySmall.g(400),
        labelLarge = t.labelLarge.g(500), labelMedium = t.labelMedium.g(500), labelSmall = t.labelSmall.g(500),
    )
}

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(20.dp),
)

/** Light or dark from the system, unless [dark] forces it (the player is always dark, like a video). */
@Composable
fun SharpEarsTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (dark) darkColors else lightColors, typography = type, shapes = shapes, content = content)

/** The system's "remove animations" setting; entry animations are skipped when it is on. */
@Composable
fun reducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}
