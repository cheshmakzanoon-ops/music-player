package io.github.musicplayer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

internal val Ink = Color(0xFF080C13)
internal val Panel = Color(0xFF111924)
internal val PanelRaised = Color(0xFF182330)
internal val Lime = Color(0xFFE8FF39)
internal val Milk = Color(0xFFF1F5F7)
internal val Muted = Color(0xFF9AAABD)
internal val Hairline = Color(0xFF2A3949)
internal val Cyan = Color(0xFF53E9ED)
internal val Magenta = Color(0xFFFF5DAD)
internal val Lilac = Cyan
internal val CyberPanel = CutCornerShape(topStart = 0.dp, topEnd = 16.dp, bottomEnd = 0.dp, bottomStart = 16.dp)
internal val CyberSmall = CutCornerShape(topStart = 0.dp, topEnd = 8.dp, bottomEnd = 0.dp, bottomStart = 8.dp)

@Composable
fun PlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Lime,
            onPrimary = Ink,
            primaryContainer = Color(0xFF303811),
            onPrimaryContainer = Lime,
            secondary = Lilac,
            tertiary = Magenta,
            background = Ink,
            onBackground = Milk,
            surface = Panel,
            onSurface = Milk,
            surfaceVariant = PanelRaised,
            onSurfaceVariant = Muted,
            outline = Hairline,
            error = Color(0xFFFF9B9B),
        ),
        shapes = Shapes(extraSmall = CyberSmall, small = CyberSmall, medium = CyberPanel, large = CyberPanel, extraLarge = CyberPanel),
        typography = Typography(
            displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 43.sp, lineHeight = 43.sp, letterSpacing = (-1.8).sp),
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-1).sp),
            headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 27.sp, lineHeight = 32.sp, letterSpacing = (-0.7).sp),
            titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp, letterSpacing = (-0.4).sp),
            titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
            bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
            bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 18.sp),
            labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
            labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 15.sp, letterSpacing = 1.1.sp),
        ),
        content = content,
    )
}
