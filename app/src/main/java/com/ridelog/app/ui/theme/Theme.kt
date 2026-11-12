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

// Pleos 야간 시스템 표면(#000000 바, #151515 패널)과 이어지도록 중성 회색 계열
private val DarkTokens = RideColors(
    background = Color(0xFF121212), surface = Color(0xFF1E1E1E), surfaceVariant = Color(0xFF2A2A2A),
    onBackground = Color(0xFFF2F2F2), onSurfaceMuted = Color(0xFFAAAAAA), primary = Color(0xFF4DA3FF),
    onPrimary = Color(0xFF0B1220), success = Color(0xFF3DD68C), warning = Color(0xFFF5B544),
    error = Color(0xFFFF6B6B), outline = Color(0xFF444444),
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
    /** 화면 제목 — 버튼 글자보다 커야 위계가 선다. */
    val headline = TextStyle(fontSize = 52.sp, fontWeight = FontWeight.Bold, lineHeight = 62.sp)
    val button = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.SemiBold, lineHeight = 44.sp)
    val display = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Bold, lineHeight = 76.sp)
    val title = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.SemiBold, lineHeight = 54.sp)
    val body = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Normal, lineHeight = 44.sp)
    val label = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Medium, lineHeight = 34.sp)
}

/** QR 코드는 카메라 인식을 위해 테마와 관계없이 흰 바탕(여백)이 필요하다. */
val QrQuietZone = Color(0xFFFFFFFF)

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
