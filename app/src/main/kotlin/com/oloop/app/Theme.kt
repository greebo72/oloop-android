package com.oloop.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Fixed M3 scheme from seed #4C5C92, per the design handoff. Dynamic color is off so the
 * tonal containers mean what the spec says (todo = primaryContainer, feature = tertiaryContainer).
 */
private val OloopLightColors = lightColorScheme(
    primary = Color(0xFF4C5C92),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE1FF),
    onPrimaryContainer = Color(0xFF344479),
    secondaryContainer = Color(0xFFDFE1F9),
    onSecondaryContainer = Color(0xFF414659),
    tertiaryContainer = Color(0xFFFFD7F5),
    onTertiaryContainer = Color(0xFF5B3D57),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    onSurfaceVariant = Color(0xFF45464F),
    surfaceContainerLow = Color(0xFFF5F2FA),
    surfaceContainer = Color(0xFFEFEDF4),
    surfaceContainerHigh = Color(0xFFEAE7EF),
    surfaceContainerHighest = Color(0xFFE4E1E9),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC6C5D0),
)

private val OloopDarkColors = darkColorScheme(
    primary = Color(0xFFB5C4FF),
    onPrimary = Color(0xFF1D2D61),
    primaryContainer = Color(0xFF344479),
    onPrimaryContainer = Color(0xFFDCE1FF),
    secondaryContainer = Color(0xFF414659),
    onSecondaryContainer = Color(0xFFDFE1F9),
    tertiaryContainer = Color(0xFF5B3D57),
    onTertiaryContainer = Color(0xFFFFD7F5),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE3E1E9),
    onSurfaceVariant = Color(0xFFC6C5D0),
    surfaceContainerLow = Color(0xFF1B1B21),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF292A2F),
    surfaceContainerHighest = Color(0xFF34343A),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF46464F),
)

/** Status dot for "open", light / dark per the token table. */
@Composable
fun statusOpenColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFFA2D398) else Color(0xFF3D6838)

/** 4 dp grid radii: chips 8, fields 10, icon container 12, FAB 16, dialogs 28. */
private val OloopShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Hierarchy from size and color, no bold. */
private val OloopTypography = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.15.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
)

@Composable
fun OloopTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) OloopDarkColors else OloopLightColors
    MaterialTheme(colorScheme = colors, shapes = OloopShapes, typography = OloopTypography, content = content)
}
