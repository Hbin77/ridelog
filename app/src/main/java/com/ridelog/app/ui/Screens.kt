package com.ridelog.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ridelog.app.trip.TripController
import com.ridelog.app.trip.UiState
import com.ridelog.app.ui.theme.Ride
import com.ridelog.app.ui.theme.RideType
import com.ridelog.app.ui.theme.Sizes
import com.ridelog.app.ui.theme.Spacing
import com.ridelog.core.fare.CostKind
import com.ridelog.core.model.Member
import com.ridelog.core.model.SeatId
import com.ridelog.core.share.ShareTextBuilder

@Composable
fun ScreenFrame(title: String, onTitleLongPress: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Ride.colors.background).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            title, style = RideType.title, color = Ride.colors.onBackground,
            modifier = if (onTitleLongPress != null) Modifier.clickable(onClickLabel = "개발자 패널", onClick = onTitleLongPress) else Modifier,
        )
        content()
    }
}

// ---------- 홈 ----------

@Composable
fun HomeScreen(onStart: () -> Unit, onHistory: () -> Unit, onSettings: () -> Unit, onDebug: () -> Unit) {
    ScreenFrame("라이드로그", onTitleLongPress = onDebug) {
        Text("함께 탄 이동의 비용을, 차가 기록하고 나눠드려요.", style = RideType.body, color = Ride.colors.onSurfaceMuted)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PrimaryButton("지난 여행", onHistory, Modifier.weight(1f), secondary = true)
            PrimaryButton("설정", onSettings, Modifier.weight(1f), secondary = true)
        }
        PrimaryButton("여행 시작", onStart, Modifier.fillMaxWidth(), icon = Icons.Filled.PlayArrow)
    }
}

// ---------- 좌석 배정 ----------

private val seatLayout = listOf(
    listOf(SeatId.DRIVER, SeatId.FRONT_PASSENGER),
    listOf(SeatId.REAR_LEFT, SeatId.REAR_CENTER, SeatId.REAR_RIGHT),
)

@Composable
fun StartTripScreen(members: List<Member>, onConfirm: (Map<SeatId, Member>) -> Unit, onCancel: () -> Unit) {
    val assigned = remember { mutableStateMapOf<SeatId, Member>().apply { members.firstOrNull()?.let { put(SeatId.DRIVER, it) } } }
    var selectedSeat by remember { mutableStateOf<SeatId?>(SeatId.FRONT_PASSENGER) }
    ScreenFrame("누가 어디에 탔나요?") {
        Text("좌석을 고르고 이름을 눌러 주세요. 다음부터는 같은 자리로 기억해요.", style = RideType.body, color = Ride.colors.onSurfaceMuted)
        seatLayout.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEach { seat ->
                    SeatCell(seat, assigned[seat], selected = seat == selectedSeat, modifier = Modifier.weight(1f)) {
                        selectedSeat = seat
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            members.forEach { m ->
                val used = assigned.containsValue(m)
                PrimaryButton(m.name, {
                    val seat = selectedSeat ?: return@PrimaryButton
                    if (used) assigned.entries.removeAll { it.value == m } else {
                        assigned[seat] = m
                        selectedSeat = seatLayout.flatten().firstOrNull { it !in assigned }
                    }
                }, Modifier.weight(1f), secondary = !used)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PrimaryButton("취소", onCancel, Modifier.weight(1f), secondary = true)
            PrimaryButton("${assigned.size}명 출발", { onConfirm(assigned.toMap()) }, Modifier.weight(2f),
                icon = Icons.Filled.Check, enabled = assigned.containsKey(SeatId.DRIVER) && assigned.size >= 2)
        }
    }
}

@Composable
private fun SeatCell(seat: SeatId, member: Member?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ride.colors
    Column(
        modifier
            .heightIn(min = Sizes.seatCell)
            .background(c.surfaceVariant, RoundedCornerShape(Sizes.cardRadius))
            .border(if (selected) 4.dp else 2.dp, if (selected) c.primary else if (member != null) c.success else c.outline, RoundedCornerShape(Sizes.cardRadius))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(Spacing.sm)
            .semantics { contentDescription = "${seat.label} ${member?.name ?: "비어 있음"}" },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(seat.label, style = RideType.label, color = c.onSurfaceMuted)
        Text(member?.name ?: "—", style = RideType.title, color = c.onBackground)
    }
}

// ---------- 주행 중 (음성 전용) ----------

@Composable
fun DrivingScreen(state: UiState, onMic: () -> Unit) {
    val c = Ride.colors
    val onboard = state.trip?.onboard?.size ?: 0
    Column(
        Modifier.fillMaxSize().background(c.background).padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("기록 중 · ${onboard}명", style = RideType.display, color = c.onBackground)
        Spacer(Modifier.height(Spacing.xl))
        MicButton(state.listening, onMic)
        Spacer(Modifier.height(Spacing.md))
        Text(if (state.listening) "듣고 있어요" else "\"간식 3만원 내가 냈어\"", style = RideType.body, color = c.onSurfaceMuted)
    }
}

@Composable
private fun MicButton(listening: Boolean, onClick: () -> Unit) {
    val c = Ride.colors
    Box(
        Modifier
            .size(Sizes.drivingTouch * 1.5f)
            .background(if (listening) c.success else c.primary, CircleShape)
            .clickable(role = Role.Button, onClickLabel = "음성으로 경비 추가", onClick = onClick)
            .semantics { contentDescription = "음성으로 경비 추가" },
        contentAlignment = Alignment.Center,
    ) { Icon(MicIcon, contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(72.dp)) }
}

// ---------- 정차 중 여행 진행 ----------

@Composable
fun ActiveParkedScreen(state: UiState, onMic: () -> Unit, onEnd: () -> Unit, onDebug: () -> Unit) {
    val trip = state.trip ?: return
    val c = Ride.colors
    ScreenFrame("여행 기록 중", onTitleLongPress = onDebug) {
        Card {
            Text("탑승", style = RideType.label, color = c.onSurfaceMuted)
            Text(trip.onboard.mapNotNull { trip.assignments[it]?.name ?: "${it.label}(미등록)" }.joinToString(" · "), style = RideType.body, color = c.onBackground)
            val km = trip.legs.sumOf { it.distanceKm } + (trip.odometerKm - trip.currentLeg.startKm)
            Text("주행 ${"%.1f".format(km)}km · 구간 ${trip.legs.size + 1}개 · 경비 ${trip.expenses.size}건", style = RideType.body, color = c.onSurfaceMuted)
        }
        state.lastMessage?.let { Text(it, style = RideType.body, color = c.onSurfaceMuted) }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PrimaryButton("경비 말하기", onMic, Modifier.weight(1f), icon = MicIcon, secondary = true)
            PrimaryButton("여행 끝", onEnd, Modifier.weight(1f), icon = Icons.Filled.Check)
        }
    }
}

// ---------- 정산 검토 ----------

@Composable
fun ReviewScreen(state: UiState, controller: TripController, onDone: () -> Unit) {
    val trip = state.trip ?: return
    val s = state.settlement ?: return
    val c = Ride.colors
    var showSend by rememberSaveable { mutableStateOf(false) }
    val names = trip.members.associate { it.id to it.name }
    if (showSend) {
        SendScreen(
            ShareTextBuilder.build("여행", state.totalKm, trip.members, s, controller.settings.account),
            onBack = { showSend = false }, onDone = onDone,
        )
        return
    }
    Column(
        Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("공동 경비 정산", style = RideType.title, color = c.onBackground)
        Text("총 ${"%.1f".format(state.totalKm)}km · 총 경비 ${won(s.totalWon)}", style = RideType.body, color = c.onSurfaceMuted)

        Card {
            Text("누가 누구에게", style = RideType.label, color = c.onSurfaceMuted)
            if (s.transfers.isEmpty()) Text("보낼 금액이 없어요", style = RideType.body, color = c.onBackground)
            s.transfers.forEach { t -> AmountRow("${names[t.fromId]} → ${names[t.toId]}", won(t.amountWon)) }
        }
        Card {
            Text("사람별 부담", style = RideType.label, color = c.onSurfaceMuted)
            trip.members.forEach { m -> AmountRow(m.name, won(s.owedByMember[m.id] ?: 0.0), muted = true) }
        }
        Card {
            Text("내역", style = RideType.label, color = c.onSurfaceMuted)
            s.items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(item.label, style = RideType.body, color = c.onBackground, modifier = Modifier.weight(1f))
                    if (item.kind == CostKind.ENERGY) Badge(if (item.estimated) "추정" else "차량 실측", if (item.estimated) c.warning else c.success)
                    Text(won(item.amountWon), style = RideType.body, color = c.onBackground)
                }
                Text(item.sharedBy.mapNotNull { names[it] }.joinToString(" · "), style = RideType.label, color = c.onSurfaceMuted)
            }
            trip.expenses.forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${e.label} (${names[e.payerId]} 결제)", style = RideType.label, color = c.onSurfaceMuted, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(Sizes.parkedTouch).clickable(role = Role.Button, onClickLabel = "${e.label} 삭제") { controller.removeExpense(e.id) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Delete, contentDescription = "${e.label} 삭제", tint = c.onSurfaceMuted) }
                }
            }
        }
        val unassigned = trip.legs.flatMap { it.unassignedSeats }.toSet()
        if (unassigned.isNotEmpty()) {
            Text("이름 없는 탑승: ${unassigned.joinToString { it.label }} — 정산에서 제외했어요", style = RideType.body, color = c.warning)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PrimaryButton("닫기", onDone, Modifier.weight(1f), secondary = true)
            PrimaryButton("보내기", { showSend = true }, Modifier.weight(2f))
        }
    }
}

@Composable
fun SendScreen(text: String, onBack: () -> Unit, onDone: () -> Unit) {
    val c = Ride.colors
    val qr = remember(text) { qrBitmap(text) }
    ScreenFrame("폰으로 받기") {
        Text("각자 폰 카메라로 찍으면 정산 내용이 열려요.", style = RideType.body, color = c.onSurfaceMuted)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (qr != null) Image(qr, contentDescription = "정산 내용 QR 코드", modifier = Modifier.size(320.dp).background(c.surface))
            else Text("내용이 길어 QR을 만들 수 없어요. 아래 내용을 확인해 주세요", style = RideType.body, color = c.warning)
        }
        Text(text, style = RideType.label, color = c.onSurfaceMuted, textAlign = TextAlign.Start)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PrimaryButton("뒤로", onBack, Modifier.weight(1f), secondary = true)
            PrimaryButton("완료", onDone, Modifier.weight(2f), icon = Icons.Filled.Check)
        }
    }
}
