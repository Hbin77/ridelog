package com.ridelog.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.ridelog.app.ui.theme.Ride
import com.ridelog.app.ui.theme.RideType
import com.ridelog.app.ui.theme.Sizes
import com.ridelog.app.ui.theme.Spacing
import java.text.NumberFormat
import java.util.Locale

private val wonFormat = NumberFormat.getNumberInstance(Locale.KOREA)
fun won(amount: Long): String = "${wonFormat.format(amount)}원"
fun won(amount: Double): String = won(Math.round(amount))

/** Material "mic" 아이콘 (아이콘 라이브러리 전체를 넣지 않으려고 경로만 둔다). */
val MicIcon: ImageVector = ImageVector.Builder("mic", 24.dp, 24.dp, 24f, 24f).apply {
    addPath(
        PathParser().parsePathString(
            "M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3z" +
                "M17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z",
        ).toNodes(),
        fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
    )
}.build()

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
            .background(bg, RoundedCornerShape(Sizes.buttonRadius))
            .then(if (secondary) Modifier.border(2.dp, c.outline, RoundedCornerShape(Sizes.buttonRadius)) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(text, style = RideType.button, color = fg, textAlign = TextAlign.Center)
    }
}

@Composable
fun Badge(text: String, color: Color) {
    Box(
        Modifier
            .border(2.dp, color, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) { Text(text, style = RideType.label, color = color) }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Ride.colors.surface, RoundedCornerShape(Sizes.cardRadius))
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) { content() }
}

@Composable
fun AmountRow(name: String, amount: String, muted: Boolean = false) {
    val c = Ride.colors
    Row(Modifier.fillMaxWidth().semantics { contentDescription = "$name $amount" }, verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = RideType.body, color = if (muted) c.onSurfaceMuted else c.onBackground, modifier = Modifier.weight(1f))
        Text(amount, style = RideType.title, color = c.onBackground)
    }
}

fun qrBitmap(text: String, sizePx: Int = 512): ImageBitmap? = runCatching { encodeQr(text, sizePx) }.getOrNull()

private fun encodeQr(text: String, sizePx: Int): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 1))
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    for (x in 0 until sizePx) for (y in 0 until sizePx) {
        bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    return bmp.asImageBitmap()
}
