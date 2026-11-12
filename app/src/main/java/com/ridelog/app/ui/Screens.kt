package com.ridelog.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ridelog.app.trip.HistoryEntry
import com.ridelog.app.trip.SettingsStore
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

private val seatOrder = listOf(SeatId.DRIVER, SeatId.FRONT_PASSENGER, SeatId.REAR_LEFT, SeatId.REAR_CENTER, SeatId.REAR_RIGHT)

// ---------- 공통 틀 ----------

@Composable
fun ScreenFrame(
    title: String,
    onTitleLongPress: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Ride.colors.background).padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                title, style = RideType.headline, color = Ride.colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).then(if (onTitleLongPress != null) Modifier.clickable(onClickLabel = "개발자 패널", onClick = onTitleLongPress) else Modifier),
            )
            trailing?.invoke(this)
        }
        content()
    }
}

/** 2단: 왼쪽 차, 오른쪽 정보. */
@Composable
fun ColumnScope.TwoPane(left: @Composable () -> Unit, right: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) { left() }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm), content = right)
    }
}

@Composable
fun BottomBar(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), content = content)
}

@Composable
private fun SectionLabel(text: String) = Text(text, style = RideType.label, color = Ride.colors.onSurfaceMuted)

// ---------- 첫 실행: 운전자 이름 ----------

@Composable
fun OnboardingScreen(settings: SettingsStore, onDone: () -> Unit) {
    val c = Ride.colors
    var name by rememberSaveable { mutableStateOf("") }
    ScreenFrame("반가워요") {
        TwoPane(
            left = { CarView(view = { if (it == SeatId.DRIVER) SeatView(name.ifBlank { null }, "운전자", SeatTone.SELECTED) else SeatView(null, null, SeatTone.EMPTY) }) },
        ) {
            Text("라이드로그는 함께 탄 이동의 비용을 차가 기록하고 나눠 주는 앱이에요.", style = RideType.body, color = c.onBackground)
            Text("먼저 운전하시는 분 이름만 알려 주세요. 동승자는 탈 때 추가하면 돼요.", style = RideType.body, color = c.onSurfaceMuted)
            Spacer(Modifier.height(Spacing.sm))
            RideTextField("운전자 이름", name, { name = it.take(10) }, Modifier.fillMaxWidth())
        }
        BottomBar {
            PrimaryButton("시작하기", {
                settings.members = listOf(Member("m0", name.trim()))
                onDone()
            }, Modifier.weight(1f), enabled = name.isNotBlank())
        }
    }
}

// ---------- 홈 ----------

@Composable
fun HomeScreen(
    starting: Boolean,
    driverName: String,
    history: List<HistoryEntry>,
    onStart: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onDebug: () -> Unit,
) {
    val c = Ride.colors
    val month = remember(history) { monthlySummary(history) }
    ScreenFrame(
        "안녕하세요, ${driverName}님", onTitleLongPress = onDebug,
        trailing = {
            PrimaryButton("지난 여행", onHistory, secondary = true)
            PrimaryButton("설정", onSettings, secondary = true)
        },
    ) {
        TwoPane(left = { CarView(view = { if (it == SeatId.DRIVER) SeatView(driverName, "운전자", SeatTone.ASSIGNED) else SeatView(null, null, SeatTone.EMPTY) }) }) {
            Text("오늘은 누구와 함께 가나요?", style = RideType.title, color = c.onBackground)
            Step("1", "출발 전 좌석에 이름 배정", "좌석 센서가 앉은 자리를 먼저 알려 줘요")
            Step("2", "그냥 운전하기", "누가 언제 내렸는지, 얼마나 달렸는지 차가 기록해요")
            Step("3", "도착하면 폰으로 보내기", "구간별로 나눈 공동 경비를 QR로 전달해요")
            if (month.trips > 0) {
                Card(container = c.accentContainer) {
                    SectionLabel("${month.month}월 합산 · 여행 ${month.trips}번 · ${"%.0f".format(month.totalKm)}km")
                    if (month.transfers.isEmpty()) Text("이번 달 보낼 금액이 없어요", style = RideType.body, color = c.onBackground)
                    month.transfers.take(3).forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${t.fromId} → ${t.toId}", style = RideType.body, color = c.onBackground, modifier = Modifier.weight(1f))
                            Text(won(t.amountWon), style = RideType.title, color = c.accent)
                        }
                    }
                }
            }
        }
        if (starting) PrimaryButton("차량 정보를 읽는 중…", {}, Modifier.fillMaxWidth(), enabled = false)
        else PrimaryButton("여행 시작", onStart, Modifier.fillMaxWidth(), icon = Icons.Filled.PlayArrow)
    }
}

@Composable
private fun Step(number: String, title: String, desc: String) {
    val c = Ride.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Box(Modifier.size(64.dp).background(c.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Text(number, style = RideType.title, color = c.onPrimaryContainer)
        }
        Column {
            Text(title, style = RideType.body, color = c.onBackground)
            Text(desc, style = RideType.label, color = c.onSurfaceMuted)
        }
    }
}

// ---------- 좌석 배정 ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StartTripScreen(
    settings: SettingsStore,
    detected: Set<SeatId>,
    onConfirm: (Map<SeatId, Member>) -> Unit,
    onCancel: () -> Unit,
) {
    val c = Ride.colors
    val people = remember { mutableStateListOf<Member>().apply { addAll(settings.members) } }
    val assigned = remember { mutableStateMapOf<SeatId, Member>().apply { people.firstOrNull()?.let { put(SeatId.DRIVER, it) } } }
    // 좌석 센서가 감지한 빈 자리부터 고르게 한다
    var selected by remember { mutableStateOf(seatOrder.firstOrNull { it in detected && it != SeatId.DRIVER } ?: SeatId.FRONT_PASSENGER) }
    var newName by rememberSaveable { mutableStateOf("") }
    var guestCount by remember { mutableIntStateOf(0) }

    fun place(m: Member) {
        assigned.entries.removeAll { it.value.id == m.id }
        assigned[selected] = m
        selected = seatOrder.firstOrNull { it !in assigned && it in detected } ?: seatOrder.firstOrNull { it !in assigned } ?: selected
    }

    ScreenFrame("누가 어디에 탔나요?") {
        TwoPane(left = {
            CarView(view = { seat ->
                val m = assigned[seat]
                when {
                    seat == selected -> SeatView(m?.name, "선택됨", SeatTone.SELECTED)
                    m != null -> SeatView(m.name, if (seat == SeatId.DRIVER) "운전자" else null, SeatTone.ASSIGNED)
                    seat in detected -> SeatView(null, "감지됨", SeatTone.DETECTED)
                    else -> SeatView(null, null, SeatTone.EMPTY)
                }
            }, onSeat = { selected = it })
        }) {
            val others = detected - SeatId.DRIVER
            if (others.isNotEmpty()) StatusPill("좌석 센서가 동승자 ${others.size}자리를 감지했어요")
            Text("${selected.label}에 누가 앉았나요?", style = RideType.title, color = c.onBackground)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                people.forEach { m ->
                    val seatOf = assigned.entries.firstOrNull { it.value.id == m.id }?.key
                    PersonChip(m.name, seatOf) { if (seatOf == selected) assigned.remove(selected) else place(m) }
                }
            }
            SectionLabel("처음 타는 사람")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                RideTextField("이름", newName, { newName = it.take(10) }, Modifier.weight(1f))
                PrimaryButton("추가", {
                    val m = settings.addMember(newName)
                    if (people.none { it.id == m.id }) people.add(m)
                    place(m)
                    newName = ""
                }, icon = Icons.Filled.Add, enabled = newName.isNotBlank())
            }
            PrimaryButton("이름 없이 앉히기", {
                guestCount += 1
                place(Member("guest$guestCount", "동승자 $guestCount"))
            }, Modifier.fillMaxWidth(), secondary = true)
        }
        BottomBar {
            PrimaryButton("취소", onCancel, Modifier.weight(1f), secondary = true)
            PrimaryButton("${assigned.size}명 출발", { onConfirm(assigned.toMap()) }, Modifier.weight(2f),
                icon = Icons.Filled.Check, enabled = assigned.containsKey(SeatId.DRIVER) && assigned.size >= 2)
        }
    }
}

/** 사람 고르기: 아바타 + 이름, 이미 앉은 사람은 좌석 이름 표시. */
@Composable
private fun PersonChip(name: String, seat: SeatId?, onClick: () -> Unit) {
    val c = Ride.colors
    val shape = RoundedCornerShape(Sizes.buttonRadius)
    Row(
        Modifier
            .heightIn(min = Sizes.parkedTouch)
            .background(if (seat != null) c.primaryContainer else c.surface, shape)
            .border(2.dp, if (seat != null) c.primary else c.outline, shape)
            .clickable(role = Role.Button, onClickLabel = "$name 앉히기", onClick = onClick)
            .padding(start = 12.dp, end = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Avatar(name, size = 72.dp)
        Column {
            Text(name, style = RideType.button, color = c.onBackground, maxLines = 1)
            seat?.let { Text(it.shortLabel, style = RideType.label, color = c.onPrimaryContainer) }
        }
    }
}

// ---------- 주행 중 (음성 전용) ----------

@Composable
fun DrivingScreen(state: UiState, onMic: () -> Unit) {
    val c = Ride.colors
    val trip = state.trip
    val onboard = trip?.onboard?.size ?: 0
    Column(
        Modifier.fillMaxSize().background(c.background).padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("${onboard}명이 함께 가는 중", style = RideType.display, color = c.onBackground)
        Spacer(Modifier.height(Spacing.md))
        if (trip != null) {
            Row(horizontalArrangement = Arrangement.spacedBy((-12).dp)) {
                trip.onboard.sortedBy { it.ordinal }.mapNotNull { trip.assignments[it]?.name }.forEach { Avatar(it, size = 88.dp) }
            }
        }
        Spacer(Modifier.height(Spacing.xl))
        Box(
            Modifier
                .size(Sizes.drivingTouch * 1.5f)
                .background(if (state.listening) c.accent else c.primary, CircleShape)
                .clickable(role = Role.Button, onClickLabel = "음성으로 경비 추가", onClick = onMic)
                .semantics { contentDescription = "음성으로 경비 추가" },
            contentAlignment = Alignment.Center,
        ) { Icon(MicIcon, contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(96.dp)) }
        Spacer(Modifier.height(Spacing.md))
        Text(if (state.listening) "듣고 있어요" else "\"간식 3만원 내가 냈어\"", style = RideType.body, color = c.onSurfaceMuted)
    }
}

// ---------- 정차 중 여행 진행 ----------

private fun TripState.seatView(seat: SeatId): SeatView {
    val m = assignments[seat]
    val on = seat in onboard
    return when {
        m != null && on -> SeatView(m.name, if (seat == SeatId.DRIVER) "운전 중" else "탑승", SeatTone.ONBOARD)
        m != null -> SeatView(m.name, if (legs.any { m.id in it.memberIds }) "내림" else "아직 안 탐", SeatTone.WAITING)
        on -> SeatView(null, "미등록 탑승", SeatTone.STRANGER)
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
    val km = trip.legs.sumOf { it.distanceKm } + (trip.odometerKm - trip.currentLeg.startKm)
    ScreenFrame("여행 기록 중", onTitleLongPress = onDebug) {
        TwoPane(left = { CarView(view = { trip.seatView(it) }) }) {
            StatusPill("${trip.onboard.size}명이 함께 가는 중 · ${"%.1f".format(km)}km", RouteIcon)
            state.lastMessage?.let { Text(it, style = RideType.body, color = c.onSurfaceMuted) }
            trip.legs.forEachIndexed { i, leg ->
                TimelineItem(RouteIcon, "구간 ${i + 1}", "${"%.1f".format(leg.distanceKm)}km", leg.memberIds.mapNotNull { names[it] }.joinToString(" · "))
            }
            TimelineItem(RouteIcon, "구간 ${trip.legs.size + 1} · 진행 중", "${"%.1f".format(trip.odometerKm - trip.currentLeg.startKm)}km", trip.namesOf(trip.currentLeg.seats), active = true)
            trip.chargingStops.forEach { stop ->
                val from = stop.startSocPct?.let { "%.0f".format(it) } ?: "?"
                val to = stop.endSocPct?.let { "%.0f".format(it) } ?: "?"
                TimelineItem(BoltIcon, "충전", "$from → $to%", "충전으로 늘어난 배터리는 비용에서 빼요")
            }
            if (trip.charging) {
                val from = trip.chargingStartSoc?.let { "%.0f".format(it) } ?: "?"
                val to = trip.socPct?.let { "%.0f".format(it) } ?: "?"
                TimelineItem(BoltIcon, "충전 중", "$from → $to%", "충전으로 늘어난 배터리는 비용에서 빼요", active = true)
            }
            trip.expenses.forEach { e ->
                TimelineItem(ReceiptIcon, e.label, won(e.amountWon), "${names[e.payerId]} 결제 · ${e.sharedBy.size}명 분담")
            }
        }
        BottomBar {
            PrimaryButton("경비 말하기", onMic, Modifier.weight(1f), icon = MicIcon, secondary = true)
            PrimaryButton("여행 끝", onEnd, Modifier.weight(1f), icon = Icons.Filled.Check)
        }
    }
}

@Composable
private fun TimelineItem(icon: ImageVector, title: String, value: String, sub: String, active: Boolean = false) {
    val c = Ride.colors
    Card(container = if (active) c.primaryContainer else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(Modifier.size(56.dp).background(if (active) c.surface else c.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = c.primary, modifier = Modifier.size(32.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = RideType.body, color = c.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, style = RideType.label, color = c.onSurfaceMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(value, style = RideType.title, color = c.onBackground)
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
        SendScreen(ShareTextBuilder.build("여행", state.totalKm, trip.members, s, controller.settings.account), s, names, controller.settings.account,
            onBack = { showSend = false }, onDone = onDone)
        return
    }
    ScreenFrame("공동 경비 정산") {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatusPill("총 ${"%.1f".format(state.totalKm)}km · 공동 경비 ${won(s.totalWon)}", RouteIcon)
                SectionLabel("누가 누구에게")
                if (s.transfers.isEmpty()) Card { Text("보낼 금액이 없어요", style = RideType.title, color = c.onBackground) }
                s.transfers.forEach { t -> TransferCard(names[t.fromId].orEmpty(), names[t.toId].orEmpty(), t.amountWon) }
                if (s.transfers.isNotEmpty()) SectionLabel("송금은 ${SettlementSolver.UNIT_WON}원 단위로 맞췄어요")
                val unassigned = trip.legs.flatMap { it.unassignedSeats }.toSet()
                if (unassigned.isNotEmpty()) Text("이름 없는 탑승(${unassigned.joinToString { it.shortLabel }})은 정산에서 뺐어요", style = RideType.label, color = c.warning)
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SectionLabel("이렇게 나눴어요")
                s.items.forEach { item ->
                    Card {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Icon(if (item.kind == CostKind.ENERGY) BoltIcon else ReceiptIcon, contentDescription = null, tint = c.primary, modifier = Modifier.size(32.dp))
                            Text(item.label, style = RideType.body, color = c.onBackground, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(won(item.amountWon), style = RideType.title, color = c.onBackground)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            if (item.kind == CostKind.ENERGY) {
                                Badge(if (item.estimated) "추정" else "차량 실측", if (item.estimated) c.warning else c.onPrimaryContainer, if (item.estimated) null else c.primaryContainer)
                            }
                            Text(
                                (if (item.kind == CostKind.EXPENSE) "${names[item.payerId]} 결제 · " else "") + item.sharedBy.mapNotNull { names[it] }.joinToString(" · ") + " 분담",
                                style = RideType.label, color = c.onSurfaceMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                trip.expenses.forEach { e ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${e.label} 기록 지우기", style = RideType.label, color = c.onSurfaceMuted, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.size(Sizes.parkedTouch).clickable(role = Role.Button, onClickLabel = "${e.label} 삭제") { controller.removeExpense(e.id) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Delete, contentDescription = "${e.label} 삭제", tint = c.onSurfaceMuted, modifier = Modifier.size(36.dp)) }
                    }
                }
                Card {
                    SectionLabel("사람별 몫 (원 단위 그대로)")
                    trip.members.forEach { m -> AmountRow(m.name, won(s.owedByMember[m.id] ?: 0.0), muted = true) }
                }
            }
        }
        BottomBar {
            PrimaryButton("닫기", onDone, Modifier.weight(1f), secondary = true)
            PrimaryButton("폰으로 보내기", { showSend = true }, Modifier.weight(2f))
        }
    }
}

/** 보내는 사람 → 받는 사람, 코랄 큰 금액. */
@Composable
private fun TransferCard(from: String, to: String, amount: Long) {
    val c = Ride.colors
    Card(container = c.accentContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Avatar(from, size = 72.dp)
            Icon(ArrowIcon, contentDescription = "에게", tint = c.onSurfaceMuted, modifier = Modifier.size(36.dp))
            Avatar(to, size = 72.dp)
            Text("$from → $to", style = RideType.body, color = c.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = Spacing.xs))
            Text(won(amount), style = RideType.display, color = c.accent)
        }
    }
}

@Composable
fun SendScreen(text: String, s: Settlement, names: Map<String, String>, account: String, onBack: () -> Unit, onDone: () -> Unit) {
    val c = Ride.colors
    val qr = remember(text) { qrBitmap(text, 720) }
    ScreenFrame("폰으로 받기") {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(600.dp).background(QrQuietZone, RoundedCornerShape(Sizes.cardRadius)).padding(Spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                if (qr != null) Image(qr, contentDescription = "정산 내용 QR 코드", modifier = Modifier.fillMaxSize())
                else Text("내용이 길어 QR을 만들 수 없어요", style = RideType.body, color = c.warning, textAlign = TextAlign.Center)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("각자 폰 카메라로 찍으면 정산 내용이 열려요", style = RideType.title, color = c.onBackground)
                s.transfers.forEach { t -> TransferCard(names[t.fromId].orEmpty(), names[t.toId].orEmpty(), t.amountWon) }
                if (account.isNotBlank()) SectionLabel("받는 계좌 $account")
            }
        }
        BottomBar {
            PrimaryButton("뒤로", onBack, Modifier.weight(1f), secondary = true)
            PrimaryButton("완료", onDone, Modifier.weight(2f))
        }
    }
}
