package cz.promptlab.h3video.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF07070C)
val Surface1 = Color(0xFF12121B)
val Surface2 = Color(0xFF1B1B27)
val Outline1 = Color(0xFF2A2A3A)
val Violet = Color(0xFF7C5CFF)
val Cyan = Color(0xFF22D3EE)
val Rose = Color(0xFFFF6B9D)
val Amber = Color(0xFFFFB86B)
val TextHi = Color(0xFFECECF5)
val TextMid = Color(0xFF9E9EB4)
val TextLow = Color(0xFF6B6B82)
val Danger = Color(0xFFFF6B6B)
val Ok = Color(0xFF4ADE80)

val AccentBrush = Brush.linearGradient(listOf(Violet, Cyan))
val AccentSweep = Brush.sweepGradient(listOf(Violet, Cyan, Rose, Violet))

private val Scheme = darkColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Surface2,
    onPrimaryContainer = TextHi,
    secondary = Cyan,
    onSecondary = Color(0xFF04222A),
    background = Ink,
    onBackground = TextHi,
    surface = Surface1,
    onSurface = TextHi,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextMid,
    outline = Outline1,
    error = Danger,
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Normal, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

@Composable
fun H3Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography) {
        // Bez Surface by texty bez explicitní barvy dědily černou a byly nečitelné.
        Surface(modifier = Modifier.fillMaxSize(), color = Ink, contentColor = TextHi) {
            content()
        }
    }
}
