package com.bigpapituna.sharpears.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Night-practice palette: near-black stage, the logo's orange (right hand) and blue (left hand).
val Stage = Color(0xFF0E0E13)
val Panel = Color(0xFF17171F)
val PanelHigh = Color(0xFF22222C)
val Ink = Color(0xFFF2F2F7)
val InkDim = Color(0xFFA3A3B2)
val Good = Color(0xFF5BE37D)
val Bad = Color(0xFFFF4D5E)

private val colors = darkColorScheme(
    primary = RightHandColor, onPrimary = Color(0xFF231300),
    primaryContainer = Color(0xFF4A2A06), onPrimaryContainer = Color(0xFFFFDDB8),
    secondary = LeftHandColor, onSecondary = Color(0xFF00213F),
    secondaryContainer = Color(0xFF103A63), onSecondaryContainer = Color(0xFFD2E4FF),
    tertiary = Good, onTertiary = Color(0xFF00210B),
    background = Stage, onBackground = Ink,
    surface = Stage, onSurface = Ink, surfaceVariant = PanelHigh, onSurfaceVariant = InkDim,
    surfaceContainerLowest = Stage, surfaceContainerLow = Panel, surfaceContainer = Panel,
    surfaceContainerHigh = PanelHigh, surfaceContainerHighest = Color(0xFF2B2B37),
    outline = Color(0xFF4A4A58), outlineVariant = Color(0xFF30303C),
    error = Bad,
)

private val type = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val shapes = Shapes(
    small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp),
)

/** Dark only: the app is used at a piano, often in the evening, and the lane reads best on black. */
@Composable
fun SharpEarsTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = colors, typography = type, shapes = shapes, content = content)
