package com.ridelog.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** design-system/MASTER.md v2 §2 — raw hex는 이 파일에만 둔다. */
@Immutable
data class RideColors(
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onBackground: Color,
    val onSurfaceMuted: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val accent: Color,
    val accentContainer: Color,
    val warning: Color,
    val error: Color,
    val outline: Color,
    val carBody: Color,
    val carGlass: Color,
    val tire: Color,
    val avatars: List<Color>,
    val onAvatar: Color,
) {
    val success: Color get() = primary
}

private val LightTokens = RideColors(
    dark = false,
    background = Color(0xFFFFF9F3), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFF4EEE7),
    onBackground = Color(0xFF1F2430), onSurfaceMuted = Color(0xFF5E6573),
    primary = Color(0xFF0A7A6D), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7F4EE), onPrimaryContainer = Color(0xFF0B5E54),
    accent = Color(0xFFC23E2B), accentContainer = Color(0xFFFFE4DD),
    warning = Color(0xFF9A5B00), error = Color(0xFFB3261E), outline = Color(0xFFE2D8CC),
    carBody = Color(0xFFFFFFFF), carGlass = Color(0xFFCFE3FF), tire = Color(0xFF2B3038),
    avatars = listOf(Color(0xFFBFEDE3), Color(0xFFFFD3C9), Color(0xFFCFE3FF), Color(0xFFFFE3A8), Color(0xFFE4D7FF)),
    onAvatar = Color(0xFF1F2430),
)

private val DarkTokens = RideColors(
    dark = true,
    background = Color(0xFF121A1F), surface = Color(0xFF1B252B), surfaceVariant = Color(0xFF24313A),
    onBackground = Color(0xFFF1F5F4), onSurfaceMuted = Color(0xFFA6B3B6),
    primary = Color(0xFF4FD1BC), onPrimary = Color(0xFF062B26),
    primaryContainer = Color(0xFF1F4B44), onPrimaryContainer = Color(0xFFF1F5F4),
    accent = Color(0xFFFF8A76), accentContainer = Color(0xFF3A2420),
    warning = Color(0xFFFFC266), error = Color(0xFFFFB4AB), outline = Color(0xFF33424A),
    carBody = Color(0xFF1F2B32), carGlass = Color(0xFF26405F), tire = Color(0xFF0A0F12),
    avatars = listOf(Color(0xFF1F4B44), Color(0xFF5A2E27), Color(0xFF26405F), Color(0xFF574516), Color(0xFF3E3360)),
    onAvatar = Color(0xFFF1F5F4),
)

/** QR 코드는 카메라 인식을 위해 테마와 관계없이 흰 바탕(여백)이 필요하다. */
val QrQuietZone = Color(0xFFFFFFFF)

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
    // 둥근 형태는 차와 사람(아바타)에게만. 나머지는 단정한 16~20dp
    val cardRadius = 16.dp
    val seatRadius = 20.dp
    val buttonRadius = 20.dp
    val hairline = 1.5.dp
}

private const val TABULAR = "tnum"

object RideType {
    val headline = TextStyle(fontSize = 52.sp, fontWeight = FontWeight.Bold, lineHeight = 62.sp)
    val display = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Bold, lineHeight = 76.sp, fontFeatureSettings = TABULAR)
    val title = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.SemiBold, lineHeight = 50.sp)
    /** 금액·거리 같은 숫자 — 자리 맞춤 숫자 */
    val figure = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.SemiBold, lineHeight = 50.sp, fontFeatureSettings = TABULAR)
    val button = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.SemiBold, lineHeight = 44.sp)
    val body = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Normal, lineHeight = 42.sp)
    val label = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Medium, lineHeight = 34.sp)
}

val LocalRideColors = staticCompositionLocalOf { LightTokens }

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
    CompositionLocalProvider(LocalRideColors provides t) {
        MaterialTheme(colorScheme = scheme, typography = Typography(bodyLarge = RideType.body), content = content)
    }
}
