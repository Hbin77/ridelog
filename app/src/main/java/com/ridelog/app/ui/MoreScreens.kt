package com.ridelog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ridelog.app.trip.HistoryEntry
import com.ridelog.app.trip.SettingsStore
import com.ridelog.app.trip.monthlySummary
import com.ridelog.app.ui.theme.Ride
import com.ridelog.app.ui.theme.RideType
import com.ridelog.app.ui.theme.Spacing
import com.ridelog.core.model.Member
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 주행 중 여행이 없을 때 — 조작 대신 안내만. */
@Composable
fun DrivingIdleScreen() {
    val c = Ride.colors
    Column(
        Modifier.fillMaxSize().background(c.background).padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("정차 후에 확인할 수 있어요", style = RideType.title, color = c.onBackground)
    }
}

@Composable
fun RideTextField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, number: Boolean = false) {
    val c = Ride.colors
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier, singleLine = true,
        label = { Text(label, style = RideType.label) },
        textStyle = RideType.body,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = c.onBackground, unfocusedTextColor = c.onBackground,
            focusedBorderColor = c.primary, unfocusedBorderColor = c.outline,
            focusedLabelColor = c.primary, unfocusedLabelColor = c.onSurfaceMuted,
            focusedContainerColor = c.surface, unfocusedContainerColor = c.surface,
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(com.ridelog.app.ui.theme.Sizes.seatRadius),
    )
}

/** 자주 타는 멤버·전력 단가·받는 계좌. 정차 중에만 열린다. */
@Composable
fun SettingsScreen(settings: SettingsStore, onDone: () -> Unit) {
    val c = Ride.colors
    val names = remember { mutableStateListOf<String>().apply { addAll(settings.members.map { it.name }) } }
    var price by remember { mutableStateOf("%.1f".format(settings.pricePerKwhWon)) }
    var account by remember { mutableStateOf(settings.account) }
    Column(
        Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("설정", style = RideType.headline, color = c.onBackground)
        Text("함께 타는 사람", style = RideType.title, color = c.onBackground)
        names.forEachIndexed { i, name ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Avatar(name.ifBlank { "?" }, size = 72.dp)
                RideTextField(if (i == 0) "운전자 (차주)" else "동승자 $i", name, { names[i] = it.take(10) }, Modifier.weight(1f))
                if (i > 0) PrimaryButton("빼기", { names.removeAt(i) }, secondary = true)
            }
        }
        if (names.size < 8) PrimaryButton("사람 추가", { names.add("") }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add, secondary = true)
        Text("요금", style = RideType.title, color = c.onBackground)
        RideTextField("충전 단가 (원/kWh, 직접 입력)", price, { price = it.filter { ch -> ch.isDigit() || ch == '.' } }, Modifier.fillMaxWidth(), number = true)
        val priceOk = price.toDoubleOrNull()?.let { it in 50.0..2000.0 } == true
        val namesOk = names.firstOrNull()?.isNotBlank() == true
        if (!priceOk) Text("전력 단가는 50~2,000원 사이로 입력해 주세요", style = RideType.label, color = c.error)
        if (!namesOk) Text("운전자 이름을 입력해 주세요", style = RideType.label, color = c.error)
        RideTextField("받는 계좌 (정산 문자에 함께 표시)", account, { account = it.take(40) }, Modifier.fillMaxWidth())
        Spacer(Modifier.padding(Spacing.xs))
        PrimaryButton("저장", {
            val cleaned = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            settings.members = cleaned.mapIndexed { i, n -> Member("m$i", n) }
            settings.pricePerKwhWon = price.toDouble()
            settings.account = account.trim()
            onDone()
        }, Modifier.fillMaxWidth(), enabled = priceOk && namesOk)
    }
}

/** 지난 여행과 이번 달 합산 정산 (출퇴근 카풀은 달마다 한 번에). */
@Composable
fun HistoryScreen(history: List<HistoryEntry>, onBack: () -> Unit) {
    val c = Ride.colors
    val month = remember(history) { monthlySummary(history) }
    val fmt = remember { SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA) }
    ScreenFrame("지난 여행") {
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (history.isEmpty()) {
                Text("끝난 여행이 아직 없습니다. 정산을 보관하면 여기에 쌓이고 달마다 합산됩니다.", style = RideType.body, color = c.onSurfaceMuted)
            } else {
                Text("${month.month}월 합산 · 여행 ${month.trips}번 · ${"%.0f".format(month.totalKm)}km", style = RideType.title, color = c.onBackground)
                if (month.transfers.isEmpty()) Text("이번 달은 정산할 금액이 없습니다", style = RideType.body, color = c.onBackground)
                month.transfers.forEach { t -> AmountRow("${t.fromId} → ${t.toId}", won(t.amountWon)) }
                Spacer(Modifier.height(Spacing.md))
            }
            history.forEach { e ->
                Column {
                    Row(Modifier.fillMaxWidth().heightIn(min = 96.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.members.joinToString(" · ") { it.name }, style = RideType.body, color = c.onBackground, maxLines = 1)
                            Text("${fmt.format(Date(e.endedAtMillis))} · ${"%.1f".format(e.totalKm)}km", style = RideType.label, color = c.onSurfaceMuted)
                        }
                        Text(won(e.totalWon), style = RideType.figure, color = c.onBackground)
                    }
                    androidx.compose.material3.HorizontalDivider(color = c.outline, thickness = com.ridelog.app.ui.theme.Sizes.hairline)
                }
            }
        }
        PrimaryButton("뒤로", onBack, Modifier.fillMaxWidth(), secondary = true)
    }
}
