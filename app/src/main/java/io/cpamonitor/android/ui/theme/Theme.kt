package io.cpamonitor.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object GlanceColors {
    val Background = Color(0xFFF3F4F6)
    val Surface = Color(0xFFFFFFFF)
    val Navy = Color(0xFF0F172A)
    val NavySoft = Color(0xFF1F3A5F)
    val Blue = Color(0xFF0EA5E9)
    val Green = Color(0xFF10B981)
    val Amber = Color(0xFFF59E0B)
    val Rose = Color(0xFFE11D48)
    val Slate = Color(0xFF64748B)
    val Muted = Color(0xFF94A3B8)
    val Line = Color(0xFFE2E8F0)
}

private val GlanceScheme = lightColorScheme(
    primary = GlanceColors.Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EEF7),
    onPrimaryContainer = GlanceColors.Navy,
    secondary = GlanceColors.Blue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF075985),
    tertiary = GlanceColors.Green,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFECFDF5),
    onTertiaryContainer = Color(0xFF047857),
    error = GlanceColors.Rose,
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF9F1239),
    background = GlanceColors.Background,
    onBackground = GlanceColors.Navy,
    surface = GlanceColors.Surface,
    onSurface = GlanceColors.Navy,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = GlanceColors.Slate,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF8FAFC),
    surfaceContainerHigh = Color(0xFFF1F5F9),
    surfaceContainerHighest = Color(0xFFE8EDF3),
    outline = Color(0xFFCBD5E1),
    outlineVariant = GlanceColors.Line,
)

private val GlanceTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 35.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 31.sp, letterSpacing = (-0.35).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.35.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.35.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 15.sp, letterSpacing = 0.45.sp),
)

private val GlanceShapes = Shapes(
    extraSmall = RoundedCornerShape(7.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun CpaMonitorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GlanceScheme,
        typography = GlanceTypography,
        shapes = GlanceShapes,
        content = content,
    )
}
