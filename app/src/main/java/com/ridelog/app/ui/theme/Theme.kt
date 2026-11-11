package com.ridelog.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** design-system/MASTER.md §2 — raw hex는 이 파일에만 둔다. */
@Immutable
data class RideColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onBackground: Color,
    val onSurfaceMuted: Color,
    val primary: Color,
    val onPrimary: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val outline: Color,
)

private val DarkTokens = RideColors(
    background = Color(0xFF0E1116), surface = Color(0xFF1A1F27), surfaceVariant = Color(0xFF252B35),
    onBackground = Color(0xFFF2F4F7), onSurfaceMuted = Color(0xFFA9B1BD), primary = Color(0xFF4DA3FF),
    onPrimary = Color(0xFF0B1220), success = Color(0xFF3DD68C), warning = Color(0xFFF5B544),
    error = Color(0xFFFF6B6B), outline = Color(0xFF3A4250),
)

private val LightTokens = RideColors(
    background = Color(0xFFF6F7F9), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFE9ECF1),
    onBackground = Color(0xFF12161C), onSurfaceMuted = Color(0xFF4A5361), primary = Color(0xFF0B63CE),
    onPrimary = Color(0xFFFFFFFF), success = Color(0xFF11804B), warning = Color(0xFF8A5A00),
    error = Color(0xFFB42318), outline = Color(0xFFC9CFD8),
)

object Spacing {
    val xs = 8.dp
    val sm = 16.dp
    val md = 24.dp
    val lg = 32.dp
    val xl = 48.dp
}

object Sizes {
    val parkedTouch = 104.dp
    val drivingTouch = 160.dp
    val seatCell = 150.dp
    val cardRadius = 12.dp
    val buttonRadius = 20.dp
}

object RideType {
    val display = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Bold, lineHeight = 76.sp)
    val title = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.SemiBold, lineHeight = 54.sp)
    val body = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Normal, lineHeight = 44.sp)
    val label = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Medium, lineHeight = 34.sp)
}

val LocalRideColors = staticCompositionLocalOf { DarkTokens }

object Ride {
    val colors: RideColors @Composable get() = LocalRideColors.current
}

@Composable
fun RideLogTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val t = if (dark) DarkTokens else LightTokens
    val scheme = if (dark) {
        darkColorScheme(primary = t.primary, onPrimary = t.onPrimary, background = t.background, surface = t.surface,
            onBackground = t.onBackground, onSurface = t.onBackground, error = t.error, outline = t.outline)
    } else {
        lightColorScheme(primary = t.primary, onPrimary = t.onPrimary, background = t.background, surface = t.surface,
            onBackground = t.onBackground, onSurface = t.onBackground, error = t.error, outline = t.outline)
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalRideColors provides t) {
        MaterialTheme(colorScheme = scheme, typography = Typography(bodyLarge = RideType.body), content = content)
    }
}
