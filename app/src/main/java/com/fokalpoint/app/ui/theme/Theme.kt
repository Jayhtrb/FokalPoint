package com.fokalpoint.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fokalpoint.app.R

/** Aurora Noir: deep-space canvas, frosted glass, luminous violet → cyan light. */
object Aurora {
    val Void = Color(0xFF07070B)
    val Night = Color(0xFF0D0D14)
    val Elevated = Color(0xFF15151F)
    val Glass = Color(0x0FFFFFFF)          // 6% white
    val GlassStrong = Color(0x1AFFFFFF)    // 10% white
    val Hairline = Color(0x1FFFFFFF)       // 12% white

    val Violet = Color(0xFF8B5CF6)
    val VioletSoft = Color(0xFFA78BFA)
    val Cyan = Color(0xFF22D3EE)
    val Pink = Color(0xFFF472B6)
    val Mint = Color(0xFF34D399)
    val Amber = Color(0xFFFBBF24)
    val Coral = Color(0xFFF87171)

    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFFA1A1AA)
    val TextTertiary = Color(0xFF6E6E7A)

    val Signature = Brush.linearGradient(listOf(Violet, Cyan))
    val SignatureWarm = Brush.linearGradient(listOf(Pink, Violet))
    val GlassBorder = Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x08FFFFFF)))
    val ProBrush = Brush.linearGradient(listOf(Color(0xFFFDE68A), Color(0xFFF472B6), Violet))
}

val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.space_grotesk_bold, FontWeight.Bold)
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold)
)

private fun display(size: Int, weight: FontWeight = FontWeight.Bold, tracking: Double = -0.02) = TextStyle(
    fontFamily = SpaceGrotesk, fontWeight = weight, fontSize = size.sp,
    lineHeight = (size * 1.12).sp, letterSpacing = (size * tracking).sp
)

private fun body(size: Int, weight: FontWeight = FontWeight.Normal, line: Double = 1.45) = TextStyle(
    fontFamily = Inter, fontWeight = weight, fontSize = size.sp, lineHeight = (size * line).sp
)

val AuroraTypography = Typography(
    displayLarge = display(48),
    displayMedium = display(40),
    displaySmall = display(34),
    headlineLarge = display(30),
    headlineMedium = display(26),
    headlineSmall = display(22, FontWeight.SemiBold),
    titleLarge = display(20, FontWeight.SemiBold, -0.01),
    titleMedium = display(17, FontWeight.SemiBold, -0.005),
    titleSmall = body(14, FontWeight.SemiBold),
    bodyLarge = body(16),
    bodyMedium = body(14),
    bodySmall = body(12),
    labelLarge = body(15, FontWeight.SemiBold, 1.2),
    labelMedium = body(13, FontWeight.Medium, 1.2),
    labelSmall = body(11, FontWeight.SemiBold, 1.2).copy(letterSpacing = 0.6.sp)
)

private val AuroraColors = darkColorScheme(
    primary = Aurora.Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A1B4D),
    onPrimaryContainer = Aurora.VioletSoft,
    secondary = Aurora.Cyan,
    onSecondary = Aurora.Void,
    tertiary = Aurora.Pink,
    background = Aurora.Void,
    onBackground = Aurora.TextPrimary,
    surface = Aurora.Night,
    onSurface = Aurora.TextPrimary,
    surfaceVariant = Aurora.Elevated,
    onSurfaceVariant = Aurora.TextSecondary,
    surfaceContainer = Aurora.Elevated,
    surfaceContainerHigh = Color(0xFF1C1C28),
    surfaceContainerHighest = Color(0xFF23232F),
    outline = Aurora.Hairline,
    outlineVariant = Color(0x14FFFFFF),
    error = Aurora.Coral,
    onError = Aurora.Void
)

val AuroraShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun FokalTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AuroraColors, typography = AuroraTypography, shapes = AuroraShapes, content = content)
}
