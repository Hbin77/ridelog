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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ridelog.app.trip.HistoryEntry
import com.ridelog.app.trip.TripController
import com.ridelog.app.trip.UiState
import com.ridelog.app.trip.monthlySummary
import com.ridelog.app.ui.theme.QrQuietZone
import com.ridelog.app.ui.theme.Ride
import com.ridelog.app.ui.theme.RideType
import com.ridelog.app.ui.theme.Sizes
import com.ridelog.app.ui.theme.Spacing
import com.ridelog.core.engine.TripState
import com.ridelog.core.fare.CostKind
import com.ridelog.core.fare.Settlement
import com.ridelog.core.fare.SettlementSolver
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
            title, style = RideType.headline, color = Ride.colors.onBackground,
            modifier = if (onTitleLongPress != null) Modifier.clickable(onClickLabel = "개발자 패널", onClick = onTitleLongPress) else Modifier,
        )
        content()
    }
}

/** 하단 고정 버튼 줄 — 모든 화면에서 같은 위치. */
@Composable
fun BottomBar(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), content = content)
}

// ---------- 홈 ----------

@Composable
fun HomeScreen(
    starting: Boolean,
    history: List<HistoryEntry>,
    onStart: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onDebug: () -> Unit,
) {
    val c = Ride.colors
    ScreenFrame("라이드로그", onTitleLongPress = onDebug) {
        Text("함께 탄 이동의 비용을, 차가 기록하고 나눠드려요.", style = RideType.body, color = c.onSurfaceMuted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StepCard("1", "좌석에 이름 배정", "출발 전에 한 번", Modifier.weight(1f))
            StepCard("2", "그냥 운전", "좌석·계기판·배터리를 차가 기록", Modifier.weight(1f))
            StepCard("3", "도착 후 보내기", "구간별로 나눈 정산을 QR로", Modifier.weight(1f))
        }
        val month = remember(history) { monthlySummary(history) }
        if (month.trips > 0) {
            Card {
                Text("${month.month}월 합산 · 여행 ${month.trips}번 · ${"%.0f".format(month.totalKm)}km", style = RideType.label, color = c.onSurfaceMuted)
                if (month.transfers.isEmpty()) Text("이번 달 보낼 금액이 없어요", style = RideType.body, color = c.onBackground)
                month.transfers.take(3).forEach { t -> AmountRow("${t.fromId} → ${t.toId}", won(t.amountWon)) }
            }
        }
        Spacer(Modifier.weight(1f))
        BottomBar {
            PrimaryButton("지난 여행", onHistory, Modifier.weight(1f), secondary = true)
            PrimaryButton("설정", onSettings, Modifier.weight(1f), secondary = true)
        }
        if (starting) PrimaryButton("차량 정보를 읽는 중…", {}, Modifier.fillMaxWidth(), enabled = false)
        else PrimaryButton("여행 시작", onStart, Modifier.fillMaxWidth(), icon = Icons.Filled.PlayArrow)
    }
}

@Composable
private fun StepCard(number: String, title: String, desc: String, modifier: Modifier) {
    val c = Ride.colors
    Column(
        modifier.background(c.surface, RoundedCornerShape(Sizes.cardRadius)).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(Modifier.size(64.dp).background(c.primary, CircleShape), contentAlignment = Alignment.Center) {
            Text(number, style = RideType.title, color = c.onPrimary)
        }
        Text(title, style = RideType.title, color = c.onBackground)
        Text(desc, style = RideType.label, color = c.onSurfaceMuted)
    }
}

// ---------- 좌석 배치도 (공통) ----------

private val seatRows = listOf(
    listOf(SeatId.DRIVER, SeatId.FRONT_PASSENGER),
    listOf(SeatId.REAR_LEFT, SeatId.REAR_CENTER, SeatId.REAR_RIGHT),
)
private val seatOrder = seatRows.flatten()

enum class SeatTone { EMPTY, SELECTED, ASSIGNED, ONBOARD, WAITING, STRANGER }

data class SeatView(val name: String?, val status: String?, val tone: SeatTone)

/** 차 윤곽 안의 좌석 배치 — 앞이 위, 운전석이 왼쪽 앞. */
@Composable
fun SeatMap(view: (SeatId) -> SeatView, modifier: Modifier = Modifier, onSeat: ((SeatId) -> Unit)? = null) {
    val c = Ride.colors
    Column(
        modifier
            .border(3.dp, c.outline, RoundedCornerShape(topStart = 72.dp, topEnd = 72.dp, bottomStart = 36.dp, bottomEnd = 36.dp))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("차량 앞", style = RideType.label, color = c.onSurfaceMuted)
        seatRows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEach { seat -> SeatCell(seat, view(seat), Modifier.weight(1f), onSeat) }
            }
        }
        Text("뒤", style = RideType.label, color = c.onSurfaceMuted)
    }
}

@Composable
private fun SeatCell(seat: SeatId, v: SeatView, modifier: Modifier, onSeat: ((SeatId) -> Unit)?) {
    val c = Ride.colors
    val border: Pair<Int, Color> = when (v.tone) {
        SeatTone.SELECTED -> 6 to c.primary
        SeatTone.ASSIGNED, SeatTone.ONBOARD -> 3 to c.success
        SeatTone.STRANGER -> 3 to c.warning
        SeatTone.WAITING, SeatTone.EMPTY -> 2 to c.outline
    }
    val statusColor = when (v.tone) {
        SeatTone.SELECTED -> c.primary
        SeatTone.ONBOARD, SeatTone.ASSIGNED -> c.success
        SeatTone.STRANGER -> c.warning
        else -> c.onSurfaceMuted
    }
    Column(
        modifier
            .heightIn(min = Sizes.seatCell)
            .background(c.surfaceVariant, RoundedCornerShape(Sizes.cardRadius))
            .border(border.first.dp, border.second, RoundedCornerShape(Sizes.cardRadius))
            .then(if (onSeat != null) Modifier.clickable(role = Role.RadioButton) { onSeat(seat) } else Modifier)
            .padding(Spacing.sm)
            .semantics { contentDescription = "${seat.label} ${v.name ?: "비어 있음"} ${v.status.orEmpty()}" },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(seat.label, style = RideType.label, color = c.onSurfaceMuted)
        Text(v.name ?: "—", style = RideType.title, color = if (v.tone == SeatTone.WAITING) c.onSurfaceMuted else c.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        v.status?.let { Text(it, style = RideType.label, color = statusColor) }
    }
}

// ---------- 좌석 배정 ----------

@Composable
fun StartTripScreen(members: List<Member>, onConfirm: (Map<SeatId, Member>) -> Unit, onCancel: () -> Unit) {
    val c = Ride.colors
    val assigned = remember { mutableStateMapOf<SeatId, Member>().apply { members.firstOrNull()?.let { put(SeatId.DRIVER, it) } } }
    var selectedSeat by remember { mutableStateOf<SeatId?>(SeatId.FRONT_PASSENGER) }
    ScreenFrame("누가 어디에 탔나요?") {
        Text("자리를 고른 뒤 아래에서 이름을 눌러 주세요.", style = RideType.body, color = c.onSurfaceMuted)
        SeatMap(view = { seat ->
            val m = assigned[seat]
            when {
                seat == selectedSeat -> SeatView(m?.name, "선택됨", SeatTone.SELECTED)
                m != null -> SeatView(m.name, if (seat == SeatId.DRIVER) "운전자" else null, SeatTone.ASSIGNED)
                else -> SeatView(null, null, SeatTone.EMPTY)
            }
        }, modifier = Modifier.fillMaxWidth(), onSeat = { selectedSeat = it })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            members.forEach { m ->
                val seatOfMember = assigned.entries.firstOrNull { it.value == m }?.key
                MemberChip(m.name, assignedTo = seatOfMember, modifier = Modifier.weight(1f)) {
                    if (seatOfMember != null) {
                        assigned.remove(seatOfMember)
                        selectedSeat = seatOfMember
                    } else {
                        val seat = selectedSeat ?: return@MemberChip
                        assigned[seat] = m
                        selectedSeat = seatOrder.firstOrNull { it !in assigned }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        BottomBar {
            PrimaryButton("취소", onCancel, Modifier.weight(1f), secondary = true)
            PrimaryButton("${assigned.size}명 출발", { onConfirm(assigned.toMap()) }, Modifier.weight(2f),
                icon = Icons.Filled.Check, enabled = assigned.containsKey(SeatId.DRIVER) && assigned.size >= 2)
        }
    }
}

/** 배정 전: 테두리 버튼 / 배정 후: 체크 + 좌석 이름(다시 누르면 해제). */
@Composable
private fun MemberChip(name: String, assignedTo: SeatId?, modifier: Modifier, onClick: () -> Unit) {
    val c = Ride.colors
    Column(
        modifier
            .heightIn(min = Sizes.parkedTouch)
            .background(if (assignedTo != null) c.surfaceVariant else c.surface, RoundedCornerShape(Sizes.buttonRadius))
            .border(2.dp, if (assignedTo != null) c.surfaceVariant else c.primary, RoundedCornerShape(Sizes.buttonRadius))
            .clickable(role = Role.Button, onClickLabel = if (assignedTo != null) "$name 배정 해제" else "$name 배정", onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (assignedTo != null) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = c.success, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(name, style = RideType.button, color = if (assignedTo != null) c.onSurfaceMuted else c.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        assignedTo?.let { Text(it.label, style = RideType.label, color = c.onSurfaceMuted) }
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

private fun TripState.seatView(seat: SeatId): SeatView {
    val m = assignments[seat]
    val on = seat in onboard
    return when {
        m != null && on -> SeatView(m.name, if (seat == SeatId.DRIVER) "운전 중" else "탑승", SeatTone.ONBOARD)
        // 앞 구간에 탔던 사람이면 "내림", 아직 한 번도 안 탔으면 "아직 안 탐"
        m != null -> SeatView(m.name, if (legs.any { m.id in it.memberIds }) "내림" else "아직 안 탐", SeatTone.WAITING)
        on -> SeatView("미등록", "탑승 감지", SeatTone.STRANGER)
        else -> SeatView(null, null, SeatTone.EMPTY)
    }
}

private fun TripState.namesOf(seats: Collection<SeatId>) =
    seats.sortedBy { it.ordinal }.map { assignments[it]?.name ?: "미등록" }.joinToString(" · ")

@Composable
fun ActiveParkedScreen(state: UiState, onMic: () -> Unit, onEnd: () -> Unit, onDebug: () -> Unit) {
    val trip = state.trip ?: return
    val c = Ride.colors
    val names = trip.members.associate { it.id to it.name }
    ScreenFrame("여행 기록 중", onTitleLongPress = onDebug) {
        state.lastMessage?.let { Text(it, style = RideType.body, color = c.success) }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            // 왼쪽: 지금 누가 타 있는지 (좌석 센서 실시간)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("좌석 센서", style = RideType.label, color = c.onSurfaceMuted)
                SeatMap(view = { trip.seatView(it) }, modifier = Modifier.fillMaxWidth())
            }
            // 오른쪽: 구간 기록과 경비
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("구간 기록", style = RideType.label, color = c.onSurfaceMuted)
                trip.legs.forEachIndexed { i, leg ->
                    Card {
                        AmountRow("구간 ${i + 1}", "${"%.1f".format(leg.distanceKm)}km")
                        Text(leg.memberIds.mapNotNull { names[it] }.joinToString(" · "), style = RideType.label, color = c.onSurfaceMuted)
                    }
                }
                Card {
                    AmountRow("구간 ${trip.legs.size + 1} · 진행 중", "${"%.1f".format(trip.odometerKm - trip.currentLeg.startKm)}km")
                    Text(trip.namesOf(trip.currentLeg.seats), style = RideType.label, color = c.onSurfaceMuted)
                }
                if (trip.expenses.isNotEmpty()) Text("경비", style = RideType.label, color = c.onSurfaceMuted)
                trip.expenses.forEach { e ->
                    Card {
                        AmountRow(e.label, won(e.amountWon))
                        Text("${names[e.payerId]} 결제 · ${e.sharedBy.size}명 분담", style = RideType.label, color = c.onSurfaceMuted)
                    }
                }
            }
        }
        BottomBar {
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
            s, names, controller.settings.account, onBack = { showSend = false }, onDone = onDone,
        )
        return
    }
    ScreenFrame("공동 경비 정산") {
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text("총 ${"%.1f".format(state.totalKm)}km · 총 경비 ${won(s.totalWon)}", style = RideType.body, color = c.onSurfaceMuted)
            TransferCard(s, names)
            Card {
                Text("내역", style = RideType.label, color = c.onSurfaceMuted)
                s.items.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(item.label, style = RideType.body, color = c.onBackground, modifier = Modifier.weight(1f))
                        if (item.kind == CostKind.ENERGY) Badge(if (item.estimated) "추정" else "차량 실측", if (item.estimated) c.warning else c.success)
                        Text(won(item.amountWon), style = RideType.body, color = c.onBackground)
                    }
                    Text(
                        (if (item.kind == CostKind.EXPENSE) "${names[item.payerId]} 결제 · " else "") + item.sharedBy.mapNotNull { names[it] }.joinToString(" · ") + " 분담",
                        style = RideType.label, color = c.onSurfaceMuted,
                    )
                }
                trip.expenses.forEach { e ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${e.label} 기록 지우기", style = RideType.label, color = c.onSurfaceMuted, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.size(Sizes.parkedTouch).clickable(role = Role.Button, onClickLabel = "${e.label} 삭제") { controller.removeExpense(e.id) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Delete, contentDescription = "${e.label} 삭제", tint = c.onSurfaceMuted) }
                    }
                }
            }
            Card {
                Text("사람별 몫 (원 단위 그대로)", style = RideType.label, color = c.onSurfaceMuted)
                trip.members.forEach { m -> AmountRow(m.name, won(s.owedByMember[m.id] ?: 0.0), muted = true) }
            }
            val unassigned = trip.legs.flatMap { it.unassignedSeats }.toSet()
            if (unassigned.isNotEmpty()) {
                Text("이름 없는 탑승: ${unassigned.joinToString { it.label }} — 정산에서 제외했어요", style = RideType.body, color = c.warning)
            }
        }
        BottomBar {
            PrimaryButton("닫기", onDone, Modifier.weight(1f), secondary = true)
            PrimaryButton("폰으로 보내기", { showSend = true }, Modifier.weight(2f))
        }
    }
}

/** 가장 중요한 숫자: 누가 누구에게 얼마. */
@Composable
private fun TransferCard(s: Settlement, names: Map<String, String>) {
    val c = Ride.colors
    Card {
        Text("누가 누구에게", style = RideType.label, color = c.onSurfaceMuted)
        if (s.transfers.isEmpty()) Text("보낼 금액이 없어요", style = RideType.title, color = c.onBackground)
        s.transfers.forEach { t ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${names[t.fromId]} → ${names[t.toId]}", style = RideType.title, color = c.onBackground, modifier = Modifier.weight(1f))
                Text(won(t.amountWon), style = RideType.display, color = c.onBackground)
            }
        }
        if (s.transfers.isNotEmpty()) {
            Text("송금은 ${SettlementSolver.UNIT_WON}원 단위로 맞췄어요 (사람별 몫과 몇 원 차이 날 수 있어요)", style = RideType.label, color = c.onSurfaceMuted)
        }
    }
}

@Composable
fun SendScreen(text: String, s: Settlement, names: Map<String, String>, account: String, onBack: () -> Unit, onDone: () -> Unit) {
    val c = Ride.colors
    val qr = remember(text) { qrBitmap(text, 720) }
    ScreenFrame("폰으로 받기") {
        Text("각자 폰 카메라로 찍으면 정산 내용이 열려요.", style = RideType.body, color = c.onSurfaceMuted)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (qr != null) {
                Image(qr, contentDescription = "정산 내용 QR 코드", modifier = Modifier.size(560.dp).background(QrQuietZone, RoundedCornerShape(Sizes.cardRadius)).padding(Spacing.sm))
            } else {
                Text("내용이 길어 QR을 만들 수 없어요. 아래 내용을 확인해 주세요", style = RideType.body, color = c.warning, textAlign = TextAlign.Center)
            }
        }
        Card {
            s.transfers.forEach { t -> AmountRow("${names[t.fromId]} → ${names[t.toId]}", won(t.amountWon)) }
            if (account.isNotBlank()) Text("받는 계좌 $account", style = RideType.label, color = c.onSurfaceMuted)
        }
        Spacer(Modifier.weight(1f))
        BottomBar {
            PrimaryButton("뒤로", onBack, Modifier.weight(1f), secondary = true)
            PrimaryButton("완료", onDone, Modifier.weight(2f))
        }
    }
}
