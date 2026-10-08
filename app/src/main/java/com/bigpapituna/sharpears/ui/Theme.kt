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
 * Design system (taste-skill, 2026-10-07; warmed around the Golden Coach Pup 2026-10-08)
 * Read: personal practice app for one adult learner, coached by a cartoon golden retriever. Friendly and
 *   encouraging, still grown-up: the pup brings the play, the interface stays calm around him.
 * Dials: variance 5, motion 6, density 4.
 * Colour: warm neutrals (cream by day, warm charcoal by night) so the pup's gold fur belongs. ONE accent,
 *   terracotta (his sweatband, the logo); his gold [PupGold] only as a soft tint: the glow he stands in, bubbles
 *   and celebrations. Hand colours and green/red feedback are data, used only on the stage and keyboard.
 * Shape rule: interactive controls are full pills (the main ones springy, see ChunkyButton); surfaces 20 dp;
 *   speech bubbles 18 dp with a tail toward the pup; inner squares 12 dp; piano keys 6 px.
 * Type: Nunito (rounded, like the pup) for headlines; Geist for everything else; Geist Mono for times, scores
 *   and counts (tabular).
 */

private fun geist(weight: Int) = Font(R.font.geist, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
private fun geistMono(weight: Int) = Font(R.font.geist_mono, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Geist = FontFamily(geist(400), geist(500), geist(600), geist(700))
val GeistMono = FontFamily(geistMono(400), geistMono(500), geistMono(600))
private fun nunito(weight: Int) = Font(R.font.nunito, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
val Nunito = FontFamily(nunito(700), nunito(800))

/** Numbers that change or line up (times, percentages, counts). */
val Numeric = TextStyle(fontFamily = GeistMono, fontFeatureSettings = "tnum")

// The accent: terracotta, the pup's sweatband and the logo's orange.
val Accent = Color(0xFFE38E5A)
private val AccentDeep = Color(0xFFA9521F)          // light mode: 5:1 on the cream background
/** The pup's fur: the glow he stands in and celebrations, never text or controls. */
val PupGold = Color(0xFFF2B84B)

// Stage colours (always dark): the lane, keyboard, and note feedback.
val RightHandColor = Color(0xFFE38E5A)
val LeftHandColor = Color(0xFF7FA6CF)
val Good = Color(0xFF6CC08B)
val Bad = Color(0xFFE5675F)
val Stage = Color(0xFF110F0D)
val StageRaised = Color(0xFF1E1A17)
val Ink = Color(0xFFF3EEE9)
val InkDim = Color(0xFFA89E94)

private val darkColors = darkColorScheme(
    primary = Accent, onPrimary = Color(0xFF2A1406),
    primaryContainer = Color(0xFF3A2A16), onPrimaryContainer = Color(0xFFF8DFB4),
    secondary = Color(0xFFCBBFB3), onSecondary = Color(0xFF1E1A17),
    secondaryContainer = Color(0xFF3E352E), onSecondaryContainer = Color(0xFFF8F3EE),   // selected segments, chips, nav
    tertiary = Good,
    background = Color(0xFF14110F), onBackground = Ink,
    surface = Color(0xFF14110F), onSurface = Ink,
    surfaceVariant = Color(0xFF231E1A), onSurfaceVariant = InkDim,
    surfaceContainerLowest = Color(0xFF0F0D0B), surfaceContainerLow = Color(0xFF1A1613),
    surfaceContainer = Color(0xFF1E1A17), surfaceContainerHigh = Color(0xFF241F1B), surfaceContainerHighest = Color(0xFF2E2823),
    outline = Color(0xFF4A413A), outlineVariant = Color(0xFF2E2823),
    error = Bad,
)

private val lightColors = lightColorScheme(
    primary = AccentDeep, onPrimary = Color(0xFFFFF7F2),
    primaryContainer = Color(0xFFFBE6C4), onPrimaryContainer = Color(0xFF3D2A06),
    secondary = Color(0xFF5A4E45), onSecondary = Color(0xFFFFFAF5),
    secondaryContainer = Color(0xFFEADFD2), onSecondaryContainer = Color(0xFF1F1A16),
    tertiary = Color(0xFF2F7D4C),
    background = Color(0xFFFAF6F0), onBackground = Color(0xFF1F1A16),
    surface = Color(0xFFFAF6F0), onSurface = Color(0xFF1F1A16),
    surfaceVariant = Color(0xFFF0E8DE), onSurfaceVariant = Color(0xFF6B6058),
    surfaceContainerLowest = Color(0xFFFFFCF8), surfaceContainerLow = Color(0xFFF6F0E8),
    surfaceContainer = Color(0xFFF3ECE3), surfaceContainerHigh = Color(0xFFF0E8DE), surfaceContainerHighest = Color(0xFFE9DFD3),
    outline = Color(0xFFC9BCAE), outlineVariant = Color(0xFFE3D8CB),
    error = Color(0xFFB3261E),
)

private val type = Typography().let { t ->
    fun TextStyle.g(w: Int, track: Double = 0.0) = copy(fontFamily = Geist, fontWeight = FontWeight(w), letterSpacing = track.em)
    fun TextStyle.n(w: Int, track: Double = 0.0) = copy(fontFamily = Nunito, fontWeight = FontWeight(w), letterSpacing = track.em)
    t.copy(
        displayLarge = t.displayLarge.n(800, -0.02), displayMedium = t.displayMedium.n(800, -0.02), displaySmall = t.displaySmall.n(800, -0.015),
        headlineLarge = t.headlineLarge.n(800, -0.015), headlineMedium = t.headlineMedium.n(800, -0.015), headlineSmall = t.headlineSmall.n(800, -0.01),
        titleLarge = t.titleLarge.n(700), titleMedium = t.titleMedium.g(500), titleSmall = t.titleSmall.g(500),
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
