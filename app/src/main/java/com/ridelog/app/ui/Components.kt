package com.ridelog.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.ridelog.app.ui.theme.Ride
import com.ridelog.app.ui.theme.RideType
import com.ridelog.app.ui.theme.Sizes
import com.ridelog.app.ui.theme.Spacing
import com.ridelog.core.model.SeatId
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

private val wonFormat = NumberFormat.getNumberInstance(Locale.KOREA)
fun won(amount: Long): String = "${wonFormat.format(amount)}원"
fun won(amount: Double): String = won(Math.round(amount))

private fun vector(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
    addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
}.build()

/** Material Symbols 경로 (아이콘 라이브러리 전체 대신 필요한 것만). */
val MicIcon: ImageVector = vector(
    "mic",
    "M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3z" +
        "M17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z",
)
val RouteIcon: ImageVector = vector(
    "route",
    "M19,15.18V7c0,-2.21 -1.79,-4 -4,-4s-4,1.79 -4,4v10c0,1.1 -0.9,2 -2,2s-2,-0.9 -2,-2V8.82C8.16,8.4 9,7.3 9,6c0,-1.66 -1.34,-3 -3,-3S3,4.34 3,6c0,1.3 0.84,2.4 2,2.82V17c0,2.21 1.79,4 4,4s4,-1.79 4,-4V7c0,-1.1 0.9,-2 2,-2s2,0.9 2,2v8.18c-1.16,0.41 -2,1.51 -2,2.82c0,1.66 1.34,3 3,3s3,-1.34 3,-3C21,16.7 20.16,15.6 19,15.18z",
)
val ReceiptIcon: ImageVector = vector(
    "receipt",
    "M18,17H6v-2h12V17zM18,13H6v-2h12V13zM18,9H6V7h12V9zM3,22l1.5,-1.5L6,22l1.5,-1.5L9,22l1.5,-1.5L12,22l1.5,-1.5L15,22l1.5,-1.5L18,22l1.5,-1.5L21,22V2l-1.5,1.5L18,2l-1.5,1.5L15,2l-1.5,1.5L12,2l-1.5,1.5L9,2L7.5,3.5L6,2L4.5,3.5L3,2V22z",
)
val BoltIcon: ImageVector = vector("bolt", "M11,21h-1l1,-7H7.5c-0.58,0 -0.57,-0.32 -0.38,-0.66c0.19,-0.34 0.05,-0.08 0.07,-0.12C8.48,10.94 10.42,7.54 13,3h1l-1,7h3.5c0.49,0 0.56,0.33 0.47,0.51l-0.07,0.15C12.96,17.55 11,21 11,21z")
val ArrowIcon: ImageVector = vector("arrow", "M12,4l-1.41,1.41L16.17,11H4v2h12.17l-5.58,5.59L12,20l8,-8z")

// ---------- 기본 요소 ----------

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    minHeight: Dp = Sizes.parkedTouch,
    secondary: Boolean = false,
) {
    val c = Ride.colors
    val shape = RoundedCornerShape(Sizes.buttonRadius)
    val bg = when {
        !enabled -> c.surfaceVariant
        secondary -> c.surface
        else -> c.primary
    }
    val fg = when {
        !enabled -> c.onSurfaceMuted
        secondary -> c.onBackground
        else -> c.onPrimary
    }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .heightIn(min = minHeight)
            .background(bg, shape)
            .then(if (secondary) Modifier.border(2.dp, c.outline, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(text, style = RideType.button, color = fg, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Badge(text: String, color: Color, container: Color? = null) {
    Box(
        Modifier
            .background(container ?: Color.Transparent, RoundedCornerShape(Sizes.buttonRadius))
            .then(if (container == null) Modifier.border(2.dp, color, RoundedCornerShape(Sizes.buttonRadius)) else Modifier)
            .padding(horizontal = 14.dp, vertical = 2.dp),
    ) { Text(text, style = RideType.label, color = color) }
}

@Composable
fun Card(modifier: Modifier = Modifier, container: Color? = null, content: @Composable () -> Unit) {
    val c = Ride.colors
    val shape = RoundedCornerShape(Sizes.cardRadius)
    Column(
        modifier
            .fillMaxWidth()
            .background(container ?: c.surface, shape)
            .then(if (container == null) Modifier.border(Sizes.hairline, c.outline, shape) else Modifier)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) { content() }
}

@Composable
fun AmountRow(name: String, amount: String, muted: Boolean = false) {
    val c = Ride.colors
    Row(Modifier.fillMaxWidth().semantics { contentDescription = "$name $amount" }, verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = RideType.body, color = if (muted) c.onSurfaceMuted else c.onBackground, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(amount, style = RideType.title, color = c.onBackground)
    }
}

/** 사람 아바타: 이름 첫 글자 + 이름마다 고정된 색. */
@Composable
fun Avatar(name: String, size: Dp = 72.dp, dimmed: Boolean = false) {
    val c = Ride.colors
    val color = avatarColor(name)
    Box(
        Modifier.size(size).alpha(if (dimmed) 0.45f else 1f).background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1), color = c.onAvatar, fontSize = (size.value * 0.46f).sp, style = RideType.title)
    }
}

/** 등록된 사람의 순서(운전자 0번부터). 같은 여행 안에서 색이 겹치지 않게 순서로 색을 준다. */
val LocalCrewOrder = androidx.compose.runtime.compositionLocalOf<Map<String, Int>> { emptyMap() }

/** 사람별 고정 색: 등록 순서대로 팔레트를 돌고, 목록에 없는 이름(이름 없는 동승자 등)만 글자 합으로 고른다. */
@Composable
fun avatarColor(name: String): Color {
    val c = Ride.colors
    val index = LocalCrewOrder.current[name] ?: abs(name.sumOf { it.code })
    return c.avatars[index % c.avatars.size]
}

// ---------- 위에서 본 차 + 좌석 ----------

enum class SeatTone { EMPTY, SELECTED, ASSIGNED, ONBOARD, WAITING, STRANGER, DETECTED }

data class SeatView(val name: String?, val status: String?, val tone: SeatTone)

private val frontRow = listOf(SeatId.DRIVER, SeatId.FRONT_PASSENGER)
private val rearRow = listOf(SeatId.REAR_LEFT, SeatId.REAR_CENTER, SeatId.REAR_RIGHT)

/**
 * 위에서 내려다본 차 일러스트. 앞이 위, 운전석이 왼쪽 앞(국내 기준).
 * 차체·유리·바퀴는 Canvas, 좌석은 터치 가능한 컴포저블로 얹는다.
 */
@Composable
fun CarView(view: (SeatId) -> SeatView, modifier: Modifier = Modifier, onSeat: ((SeatId) -> Unit)? = null) {
    val c = Ride.colors
    BoxWithConstraints(modifier.aspectRatio(0.62f, matchHeightConstraintsFirst = true)) {
        val w = maxWidth
        val h = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val W = size.width
            val H = size.height
            fun p(x: Float, y: Float) = Offset(W * x, H * y)
            val line = 3.dp.toPx()
            // 바퀴 (휠 아치 밖으로 살짝 보임)
            val tw = W * 0.075f
            val th = H * 0.12f
            listOf(0.10f, 0.70f).forEach { y ->
                drawRoundRect(c.tire, p(0.055f, y), Size(tw, th), CornerRadius(tw * 0.35f))
                drawRoundRect(c.tire, Offset(W * 0.945f - tw, H * y), Size(tw, th), CornerRadius(tw * 0.35f))
            }
            // 차체: 앞은 둥글게 좁아지고 뒤는 각이 살짝 선 실루엣
            val body = Path().apply {
                moveTo(W * 0.30f, H * 0.018f)
                quadraticTo(W * 0.50f, H * 0.004f, W * 0.70f, H * 0.018f)
                cubicTo(W * 0.86f, H * 0.026f, W * 0.905f, H * 0.075f, W * 0.905f, H * 0.14f)
                lineTo(W * 0.905f, H * 0.885f)
                cubicTo(W * 0.905f, H * 0.95f, W * 0.86f, H * 0.986f, W * 0.72f, H * 0.986f)
                lineTo(W * 0.28f, H * 0.986f)
                cubicTo(W * 0.14f, H * 0.986f, W * 0.095f, H * 0.95f, W * 0.095f, H * 0.885f)
                lineTo(W * 0.095f, H * 0.14f)
                cubicTo(W * 0.095f, H * 0.075f, W * 0.14f, H * 0.026f, W * 0.30f, H * 0.018f)
                close()
            }
            drawPath(body, c.carBody)
            drawPath(body, c.outline, style = Stroke(width = line))
            // 사이드미러
            listOf(0.035f to 0.10f, 0.865f to 0.10f).forEach { (x, mw) ->
                drawRoundRect(c.carBody, p(x, 0.25f), Size(W * mw, H * 0.03f), CornerRadius(W * 0.03f))
                drawRoundRect(c.outline, p(x, 0.25f), Size(W * mw, H * 0.03f), CornerRadius(W * 0.03f), style = Stroke(width = line * 0.8f))
            }
            // 헤드램프 · 테일램프
            drawLine(c.lamp, p(0.17f, 0.052f), p(0.35f, 0.034f), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
            drawLine(c.lamp, p(0.65f, 0.034f), p(0.83f, 0.052f), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
            drawLine(c.accent, p(0.18f, 0.968f), p(0.82f, 0.968f), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
            // 앞유리 (위가 둥근 사다리꼴)
            Path().apply {
                moveTo(W * 0.155f, H * 0.262f); lineTo(W * 0.845f, H * 0.262f)
                lineTo(W * 0.775f, H * 0.158f)
                quadraticTo(W * 0.50f, H * 0.134f, W * 0.225f, H * 0.158f); close()
            }.let { drawPath(it, c.carGlass) }
            // 실내 (파노라마 루프 너머로 보이는 좌석 공간)
            drawRoundRect(c.carGlass.copy(alpha = 0.28f), p(0.135f, 0.272f), Size(W * 0.73f, H * 0.575f), CornerRadius(W * 0.06f))
            // 센터 콘솔
            drawRoundRect(c.outline, p(0.475f, 0.31f), Size(W * 0.05f, H * 0.19f), CornerRadius(W * 0.025f))
            // B필러 이음새
            drawLine(c.outline, p(0.095f, 0.555f), p(0.135f, 0.555f), strokeWidth = line)
            drawLine(c.outline, p(0.865f, 0.555f), p(0.905f, 0.555f), strokeWidth = line)
            // 뒷유리
            Path().apply {
                moveTo(W * 0.19f, H * 0.857f); lineTo(W * 0.81f, H * 0.857f)
                lineTo(W * 0.745f, H * 0.918f)
                quadraticTo(W * 0.50f, H * 0.932f, W * 0.255f, H * 0.918f); close()
            }.let { drawPath(it, c.carGlass) }
        }
        // 좌석: 앞줄(콘솔 사이 간격) 30~52%, 뒷줄 58~82%
        Row(
            Modifier.offset(x = w * 0.165f, y = h * 0.295f).width(w * 0.67f),
            horizontalArrangement = Arrangement.spacedBy(w * 0.09f),
        ) { frontRow.forEach { SeatSlot(it, view(it), Modifier.weight(1f).height(h * 0.225f), onSeat) } }
        Row(
            Modifier.offset(x = w * 0.155f, y = h * 0.585f).width(w * 0.69f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { rearRow.forEach { SeatSlot(it, view(it), Modifier.weight(1f).height(h * 0.235f), onSeat) } }
    }
}

@Composable
private fun SeatSlot(seat: SeatId, v: SeatView, modifier: Modifier, onSeat: ((SeatId) -> Unit)?) {
    val c = Ride.colors
    val shape = RoundedCornerShape(Sizes.seatRadius)
    val bg = when (v.tone) {
        SeatTone.ONBOARD, SeatTone.ASSIGNED -> c.primaryContainer
        SeatTone.SELECTED -> c.surface
        else -> c.surfaceVariant
    }
    val borderColor = when (v.tone) {
        SeatTone.SELECTED -> c.primary
        SeatTone.STRANGER, SeatTone.DETECTED -> c.warning
        SeatTone.ONBOARD, SeatTone.ASSIGNED -> c.primary
        else -> c.outline
    }
    val borderWidth = if (v.tone == SeatTone.SELECTED) 6.dp else 3.dp
    // 점선은 "센서가 감지했지만 아직 이름이 없는 자리"에만 — 의미 있는 신호로 남긴다
    val dashed = v.tone == SeatTone.DETECTED
    val backrest = when (v.tone) {
        SeatTone.ONBOARD, SeatTone.ASSIGNED, SeatTone.SELECTED -> c.primary.copy(alpha = 0.22f)
        SeatTone.STRANGER, SeatTone.DETECTED -> c.warning.copy(alpha = 0.18f)
        else -> c.outline
    }
    Column(
        modifier
            .background(bg, shape)
            // 위에서 본 좌석: 앞쪽(위)이 방석, 아래쪽 띠가 등받이
            .drawBehind {
                val r = CornerRadius(Sizes.seatRadius.toPx())
                val band = size.height * 0.17f
                drawRoundRect(backrest, Offset(0f, size.height - band), Size(size.width, band), r)
            }
            .then(
                if (dashed) Modifier.dashedBorder(borderColor, Sizes.seatRadius, 3.dp)
                else Modifier.border(borderWidth, borderColor, shape),
            )
            .then(if (onSeat != null) Modifier.clickable(role = Role.RadioButton, onClickLabel = "${seat.label} 선택") { onSeat(seat) } else Modifier)
            .padding(top = 8.dp, bottom = 30.dp, start = 4.dp, end = 4.dp)
            .semantics { contentDescription = "${seat.label} ${v.name ?: "비어 있음"} ${v.status.orEmpty()}" },
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (v.name != null && v.tone != SeatTone.STRANGER) {
            Avatar(v.name, size = 64.dp, dimmed = v.tone == SeatTone.WAITING)
            Text(v.name, style = RideType.label, color = c.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Text(if (v.tone == SeatTone.STRANGER) "?" else "+", style = RideType.title, color = if (v.tone == SeatTone.STRANGER) c.warning else c.onSurfaceMuted)
            Text(seat.shortLabel, style = RideType.label, color = c.onSurfaceMuted, maxLines = 1)
        }
        v.status?.let {
            val color = when (v.tone) {
                SeatTone.SELECTED, SeatTone.ONBOARD, SeatTone.ASSIGNED -> c.onPrimaryContainer
                SeatTone.STRANGER, SeatTone.DETECTED -> c.warning
                else -> c.onSurfaceMuted
            }
            Text(it, style = RideType.label, color = if (v.tone == SeatTone.SELECTED) c.primary else color, maxLines = 1)
        }
    }
}

val SeatId.shortLabel: String
    get() = when (this) {
        SeatId.DRIVER -> "운전석"
        SeatId.FRONT_PASSENGER -> "조수석"
        SeatId.REAR_LEFT -> "뒤 왼쪽"
        SeatId.REAR_CENTER -> "뒤 가운데"
        SeatId.REAR_RIGHT -> "뒤 오른쪽"
    }

private fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp) = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))),
    )
}

// ---------- QR ----------

fun qrBitmap(text: String, sizePx: Int = 512): ImageBitmap? = runCatching { encodeQr(text, sizePx) }.getOrNull()

private fun encodeQr(text: String, sizePx: Int): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 1))
    val pixels = IntArray(sizePx * sizePx) { i -> if (matrix[i % sizePx, i / sizePx]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
}
