package com.ridelog.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val seatOrder = listOf(SeatId.DRIVER, SeatId.FRONT_PASSENGER, SeatId.REAR_LEFT, SeatId.REAR_CENTER, SeatId.REAR_RIGHT)
private val clock = SimpleDateFormat("HH:mm", Locale.KOREA)
private fun km(v: Double) = "%.1f".format(v)

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
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                title, style = RideType.headline, color = Ride.colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).then(if (onTitleLongPress != null) Modifier.clickable(onClickLabel = "개발자 패널", onClick = onTitleLongPress) else Modifier),
            )
            trailing?.invoke(this)
        }
        content()
    }
}

/** 왼쪽 차, 오른쪽 기록 — 차가 화면의 주인공. */
@Composable
fun ColumnScope.TwoPane(left: @Composable () -> Unit, right: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) { left() }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md), content = right)
    }
}

@Composable
fun BottomBar(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), content = content)
}

/** 큰 숫자 + 단위: "40.0 km" */
@Composable
private fun Metric(value: String, unit: String, caption: String, modifier: Modifier = Modifier) {
    val c = Ride.colors
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = RideType.display, color = c.onBackground)
            Spacer(Modifier.width(6.dp))
            Text(unit, style = RideType.title, color = c.onSurfaceMuted, modifier = Modifier.padding(bottom = 8.dp))
        }
        Text(caption, style = RideType.body, color = c.onSurfaceMuted)
    }
}

/** 구분선 있는 기록 한 줄: 시각 · 내용 · 오른쪽 값 */
@Composable
private fun LogRow(time: String?, text: String, value: String? = null, emphasis: Boolean = false) {
    val c = Ride.colors
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically) {
            if (time != null) Text(time, style = RideType.figure.copy(fontSize = RideType.body.fontSize), color = c.onSurfaceMuted, modifier = Modifier.width(120.dp))
            Text(text, style = RideType.body, color = if (emphasis) c.primary else c.onBackground, modifier = Modifier.weight(1f).padding(end = Spacing.sm), maxLines = 2, overflow = TextOverflow.Ellipsis)
            value?.let { Text(it, style = RideType.figure, color = c.onBackground) }
        }
        HorizontalDivider(color = c.outline, thickness = Sizes.hairline)
    }
}

// ---------- 여행 기록(사건) 계산 ----------

private data class TripEvent(val atMillis: Long, val text: String, val value: String?)

/** 구간 경계마다 누가 탔고 내렸는지 — 좌석 센서가 만든 기록을 사람 말로. */
private fun TripState.events(): List<TripEvent> {
    val names = members.associate { it.id to it.name }
    val segments = legs.map { Triple(it.startMillis, it.startKm, it.memberIds) } +
        Triple(currentLeg.startMillis, currentLeg.startKm, currentLeg.seats.mapNotNull { assignments[it]?.id }.toSet())
    val baseKm = segments.first().second
    val out = mutableListOf<TripEvent>()
    val first = segments.first()
    out += TripEvent(startMillis, "출발 · " + seatOrder.mapNotNull { s -> assignments[s]?.takeIf { it.id in first.third }?.name }.joinToString(" · "), "0.0km")
    segments.zipWithNext().forEach { (prev, next) ->
        val off = prev.third - next.third
        val on = next.third - prev.third
        val at = "${km(next.second - baseKm)}km"
        off.forEach { out += TripEvent(next.first, "${names[it]} 내림", at) }
        on.forEach { out += TripEvent(next.first, "${names[it]} 탐", at) }
    }
    chargingStops.forEach { cs ->
        out += TripEvent(cs.startMillis, "충전 ${cs.startSocPct?.let { "%.0f".format(it) } ?: "?"}% → ${cs.endSocPct?.let { "%.0f".format(it) } ?: "?"}%", null)
    }
    if (charging) out += TripEvent(chargingSince ?: System.currentTimeMillis(), "충전 중 · 지금 ${socPct?.let { "%.0f".format(it) } ?: "?"}%", null)
    expenses.forEach { e -> out += TripEvent(e.atMillis, "${e.label} · ${names[e.payerId]} 결제", won(e.amountWon)) }
    return out.sortedBy { it.atMillis }
}

private fun TripState.batteryUsedPct(): Double = legs.sumOf { it.socConsumedPct } + currentLeg.socConsumedPct.coerceAtLeast(0.0)

// ---------- 첫 실행 ----------

@Composable
fun OnboardingScreen(settings: SettingsStore, onDone: () -> Unit) {
    val c = Ride.colors
    var name by rememberSaveable { mutableStateOf("") }
    ScreenFrame("운전자 등록") {
        TwoPane(left = { CarView(view = { if (it == SeatId.DRIVER) SeatView(name.ifBlank { null }, "운전석", SeatTone.SELECTED) else SeatView(null, null, SeatTone.EMPTY) }) }) {
            Text("이 차를 운전하는 사람", style = RideType.title, color = c.onBackground)
            RideTextField("이름", name, { name = it.take(10) }, Modifier.fillMaxWidth())
            Text("동승자는 출발할 때 좌석에서 바로 추가합니다.", style = RideType.body, color = c.onSurfaceMuted)
        }
        BottomBar {
            PrimaryButton("등록", {
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
    settings: SettingsStore,
    socPct: Double?,
    history: List<HistoryEntry>,
    onStart: () -> Unit,
    onQuickStart: (Map<SeatId, Member>) -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onDebug: () -> Unit,
) {
    val c = Ride.colors
    val members = settings.members
    val driver = members.firstOrNull()?.name.orEmpty()
    val month = remember(history) { monthlySummary(history) }
    // 지난번 좌석 배치를 지금 등록된 사람으로 되살린다
    val last = remember(members) {
        settings.lastSeating.mapNotNull { (seat, name) -> members.firstOrNull { it.name == name }?.let { seat to it } }.toMap()
    }
    ScreenFrame(
        "출발 준비", onTitleLongPress = onDebug,
        trailing = {
            PrimaryButton("지난 여행", onHistory, secondary = true)
            PrimaryButton("설정", onSettings, secondary = true)
        },
    ) {
        TwoPane(left = {
            CarView(view = { seat ->
                val m = last[seat] ?: if (seat == SeatId.DRIVER) members.firstOrNull() else null
                if (m != null) SeatView(m.name, null, if (seat == SeatId.DRIVER) SeatTone.ONBOARD else SeatTone.WAITING) else SeatView(null, null, SeatTone.EMPTY)
            })
        }) {
            Metric(socPct?.let { "%.0f".format(it) } ?: "—", "%", "배터리 · 운전자 $driver")
            HorizontalDivider(color = c.outline, thickness = Sizes.hairline)
            if (last.size >= 2 && !starting) {
                Text("지난번 자리 그대로", style = RideType.title, color = c.onBackground)
                Text(seatOrder.mapNotNull { s -> last[s]?.let { "${s.shortLabel} ${it.name}" } }.joinToString(" · "), style = RideType.body, color = c.onSurfaceMuted)
                PrimaryButton("이대로 출발", { onQuickStart(last) }, Modifier.fillMaxWidth(), icon = Icons.Filled.PlayArrow)
            } else {
                Text("탈 사람을 좌석에 배정하면 내린 시점까지 차가 기록합니다.", style = RideType.body, color = c.onSurfaceMuted)
            }
            if (month.trips > 0) {
                HorizontalDivider(color = c.outline, thickness = Sizes.hairline)
                Text("${month.month}월 · 여행 ${month.trips}번 · ${"%.0f".format(month.totalKm)}km", style = RideType.body, color = c.onSurfaceMuted)
                if (month.transfers.isEmpty()) Text("이번 달은 정산할 금액이 없습니다", style = RideType.body, color = c.onBackground)
                month.transfers.take(3).forEach { t -> AmountRow("${t.fromId} → ${t.toId}", won(t.amountWon)) }
            }
        }
        if (starting) PrimaryButton("차량 정보를 읽는 중", {}, Modifier.fillMaxWidth(), enabled = false)
        else PrimaryButton(if (last.size >= 2) "다른 사람과 출발" else "좌석 배정하고 출발", onStart, Modifier.fillMaxWidth(), secondary = last.size >= 2)
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
    var selected by remember { mutableStateOf(seatOrder.firstOrNull { it in detected && it != SeatId.DRIVER } ?: SeatId.FRONT_PASSENGER) }
    var newName by rememberSaveable { mutableStateOf("") }
    var guestCount by remember { mutableIntStateOf(0) }

    fun place(m: Member) {
        assigned.entries.removeAll { it.value.id == m.id }
        assigned[selected] = m
        selected = seatOrder.firstOrNull { it !in assigned && it in detected } ?: seatOrder.firstOrNull { it !in assigned } ?: selected
    }

    val unnamed = detected.filter { it !in assigned }
    val blocker = when {
        unnamed.isNotEmpty() -> "센서가 감지한 ${unnamed.joinToString { it.shortLabel }} 자리에 누가 탔는지 골라 주세요"
        assigned.size < 2 -> "동승자를 한 명 이상 앉혀 주세요"
        else -> null
    }

    ScreenFrame("좌석 배정", trailing = {
        Text("감지 ${detected.size - (if (SeatId.DRIVER in detected) 1 else 0)}자리", style = RideType.title, color = c.warning)
    }) {
        TwoPane(left = {
            CarView(view = { seat ->
                val m = assigned[seat]
                when {
                    seat == selected -> SeatView(m?.name, "선택", SeatTone.SELECTED)
                    m != null -> SeatView(m.name, null, SeatTone.ASSIGNED)
                    seat in detected -> SeatView(null, "감지", SeatTone.DETECTED)
                    else -> SeatView(null, null, SeatTone.EMPTY)
                }
            }, onSeat = { selected = it })
        }) {
            Text(selected.label, style = RideType.title, color = c.onBackground)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                people.forEach { m ->
                    val seatOf = assigned.entries.firstOrNull { it.value.id == m.id }?.key
                    PersonChip(m.name, seatOf) { if (seatOf == selected) assigned.remove(selected) else place(m) }
                }
                PersonChip("이름 없이", null) {
                    guestCount += 1
                    place(Member("guest$guestCount", "동승자 $guestCount"))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                RideTextField("새 동승자 이름", newName, { newName = it.take(10) }, Modifier.weight(1f))
                PrimaryButton("추가", {
                    val m = settings.addMember(newName)
                    if (people.none { it.id == m.id }) people.add(m)
                    place(m)
                    newName = ""
                }, icon = Icons.Filled.Add, enabled = newName.isNotBlank(), secondary = true)
            }
        }
        blocker?.let { Text(it, style = RideType.body, color = c.warning) }
        BottomBar {
            PrimaryButton("취소", onCancel, Modifier.weight(1f), secondary = true)
            PrimaryButton("${assigned.size}명 출발", { onConfirm(assigned.toMap()) }, Modifier.weight(2f),
                icon = Icons.Filled.Check, enabled = blocker == null)
        }
    }
}

/** 사람 고르기 — 이름이 주인공, 앉은 자리는 아래 한 줄. */
@Composable
private fun PersonChip(name: String, seat: SeatId?, onClick: () -> Unit) {
    val c = Ride.colors
    val shape = RoundedCornerShape(Sizes.buttonRadius)
    Column(
        Modifier
            .heightIn(min = Sizes.parkedTouch)
            .background(if (seat != null) c.primaryContainer else c.surface, shape)
            .border(if (seat != null) 3.dp else Sizes.hairline, if (seat != null) c.primary else c.outline, shape)
            .clickable(role = Role.Button, onClickLabel = "$name 앉히기", onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(name, style = RideType.button, color = c.onBackground, maxLines = 1)
        seat?.let { Text(it.shortLabel, style = RideType.label, color = c.onPrimaryContainer) }
    }
}

// ---------- 주행 중 (음성 전용) ----------

@Composable
fun DrivingScreen(state: UiState, onMic: () -> Unit) {
    val c = Ride.colors
    val trip = state.trip
    val total = trip?.let { it.legs.sumOf { l -> l.distanceKm } + (it.odometerKm - it.currentLeg.startKm) } ?: 0.0
    Column(
        Modifier.fillMaxSize().background(c.background).padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(km(total), style = RideType.display.copy(fontSize = RideType.display.fontSize * 1.5f, lineHeight = RideType.display.lineHeight * 1.5f), color = c.onBackground)
            Spacer(Modifier.width(8.dp))
            Text("km", style = RideType.title, color = c.onSurfaceMuted, modifier = Modifier.padding(bottom = 16.dp))
        }
        if (trip != null) {
            Text(seatOrder.filter { it in trip.onboard }.mapNotNull { trip.assignments[it]?.name }.joinToString(" · "), style = RideType.title, color = c.onSurfaceMuted)
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
        Text(if (state.listening) "듣는 중" else "경비는 말로 남기기", style = RideType.body, color = c.onSurfaceMuted)
    }
}

// ---------- 정차 중 여행 진행 ----------

private fun TripState.seatView(seat: SeatId): SeatView {
    val m = assignments[seat]
    val on = seat in onboard
    return when {
        m != null && on -> SeatView(m.name, null, SeatTone.ONBOARD)
        m != null -> SeatView(m.name, if (legs.any { m.id in it.memberIds }) "내림" else "대기", SeatTone.WAITING)
        on -> SeatView(null, "미등록", SeatTone.STRANGER)
        else -> SeatView(null, null, SeatTone.EMPTY)
    }
}

@Composable
fun ActiveParkedScreen(state: UiState, onMic: () -> Unit, onEnd: () -> Unit, onDebug: () -> Unit) {
    val trip = state.trip ?: return
    val c = Ride.colors
    val total = trip.legs.sumOf { it.distanceKm } + (trip.odometerKm - trip.currentLeg.startKm)
    ScreenFrame("기록 중", onTitleLongPress = onDebug, trailing = {
        Text("${trip.onboard.size}명 탑승", style = RideType.title, color = c.primary)
    }) {
        TwoPane(left = { CarView(view = { trip.seatView(it) }) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                Metric(km(total), "km", "계기판 기준")
                Metric("%.0f".format(trip.batteryUsedPct()), "%", "배터리 사용")
            }
            Column {
                trip.events().forEach { e -> LogRow(clock.format(Date(e.atMillis)), e.text, e.value) }
            }
        }
        BottomBar {
            PrimaryButton("경비 말하기", onMic, Modifier.weight(1f), icon = MicIcon, secondary = true)
            PrimaryButton("도착", onEnd, Modifier.weight(1f), icon = Icons.Filled.Check)
        }
    }
}

// ---------- 정산 ----------

@Composable
fun ReviewScreen(state: UiState, controller: TripController, onDone: () -> Unit) {
    val trip = state.trip ?: return
    val s = state.settlement ?: return
    val c = Ride.colors
    var showSend by rememberSaveable { mutableStateOf(false) }
    var showShares by rememberSaveable { mutableStateOf(false) }
    val names = trip.members.associate { it.id to it.name }
    if (showSend) {
        SendScreen(ShareTextBuilder.build("여행", state.totalKm, trip.members, s, controller.settings.account), s, names, controller.settings.account,
            onBack = { showSend = false }, onDone = onDone)
        return
    }
    val startKm = trip.legs.firstOrNull()?.startKm ?: trip.currentLeg.startKm
    val endKm = trip.legs.lastOrNull()?.endKm ?: trip.odometerKm
    ScreenFrame("정산", trailing = { Text("공동 경비 ${won(s.totalWon)}", style = RideType.title, color = c.onBackground) }) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            // 왼쪽: 보낼 돈 — 이 화면의 결론
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (s.transfers.isEmpty()) Text("서로 보낼 금액이 없습니다", style = RideType.title, color = c.onBackground)
                s.transfers.forEach { t ->
                    Column {
                        Row(Modifier.fillMaxWidth().heightIn(min = 104.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(names[t.fromId].orEmpty(), style = RideType.title, color = c.onBackground)
                            Icon(ArrowIcon, contentDescription = "에게", tint = c.onSurfaceMuted, modifier = Modifier.padding(horizontal = Spacing.sm).size(36.dp))
                            Text(names[t.toId].orEmpty(), style = RideType.title, color = c.onSurfaceMuted, modifier = Modifier.weight(1f))
                            Text(won(t.amountWon), style = RideType.display, color = c.onBackground)
                        }
                        HorizontalDivider(color = c.outline, thickness = Sizes.hairline)
                    }
                }
                if (s.transfers.isNotEmpty()) Text("송금액은 ${SettlementSolver.UNIT_WON}원 단위로 내림했습니다", style = RideType.body, color = c.onSurfaceMuted)
                val unassigned = trip.legs.flatMap { it.unassignedSeats }.toSet()
                if (unassigned.isNotEmpty()) Text("이름 없이 감지된 ${unassigned.joinToString { it.shortLabel }} 자리는 정산에서 뺐습니다", style = RideType.body, color = c.warning)
                Spacer(Modifier.height(Spacing.sm))
                Text(if (showShares) "사람별 몫 접기" else "사람별 몫 보기", style = RideType.body, color = c.primary,
                    modifier = Modifier.heightIn(min = 72.dp).clickable(role = Role.Button) { showShares = !showShares }.padding(vertical = Spacing.sm))
                if (showShares) trip.members.forEach { m -> AmountRow(m.name, won(s.owedByMember[m.id] ?: 0.0), muted = true) }
            }
            // 오른쪽: 근거 — 누가 몇 km를 같이 탔는지
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("탄 구간", style = RideType.title, color = c.onBackground)
                RideBars(trip)
                Text("계기판 ${"%,.0f".format(startKm)} → ${"%,.0f".format(endKm)}km · 배터리 ${"%.0f".format(trip.batteryUsedPct())}% 사용", style = RideType.body, color = c.onSurfaceMuted)
                Column {
                    val seatRank = seatOrder.mapIndexedNotNull { i, seat -> trip.assignments[seat]?.id?.let { it to i } }.toMap()
                    s.items.forEach { item ->
                        val who = item.sharedBy.sortedBy { seatRank[it] ?: 99 }.mapNotNull { names[it] }.joinToString(" · ")
                        val label = if (item.kind == CostKind.ENERGY) "${item.label.removePrefix("주행 ")} · $who" + (if (item.estimated) " · 추정" else "")
                        else "${item.label} · ${names[item.payerId]} 결제 · $who"
                        LogRow(null, label, won(item.amountWon))
                    }
                }
                trip.expenses.forEach { e ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${e.label} 기록 지우기", style = RideType.body, color = c.onSurfaceMuted, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.size(Sizes.parkedTouch).clickable(role = Role.Button, onClickLabel = "${e.label} 삭제") { controller.removeExpense(e.id) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Delete, contentDescription = "${e.label} 삭제", tint = c.onSurfaceMuted, modifier = Modifier.size(40.dp)) }
                    }
                }
            }
        }
        BottomBar {
            PrimaryButton("보관하고 닫기", onDone, Modifier.weight(1f), secondary = true)
            PrimaryButton("폰으로 보내기", { showSend = true }, Modifier.weight(2f))
        }
    }
}

/** 사람별 탑승 막대: 가로축 = 주행 거리. 중간에 내린 사람은 막대가 그 지점에서 끝난다. */
@Composable
private fun RideBars(trip: TripState) {
    val c = Ride.colors
    val legs = trip.legs.filter { it.distanceKm > 0 }
    val total = legs.sumOf { it.distanceKm }.takeIf { it > 0 } ?: return
    val members = seatOrder.mapNotNull { trip.assignments[it] }.distinctBy { it.id }
    members.forEach { m ->
        val color = avatarColor(m.name)
        val ridden = legs.filter { m.id in it.memberIds }.sumOf { it.distanceKm }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(m.name, style = RideType.body, color = c.onBackground, modifier = Modifier.width(150.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Canvas(Modifier.weight(1f).height(44.dp)) {
                drawRoundRect(c.surfaceVariant, size = size, cornerRadius = CornerRadius(8.dp.toPx()))
                var x = 0f
                legs.forEach { leg ->
                    val w = (leg.distanceKm / total * size.width).toFloat()
                    // 구간 경계는 2dp 틈으로 보여 준다
                    if (m.id in leg.memberIds) drawRoundRect(color, Offset(x, 0f), Size((w - 2.dp.toPx()).coerceAtLeast(1f), size.height), CornerRadius(8.dp.toPx()))
                    x += w
                }
            }
            Text("${km(ridden)}km", style = RideType.figure.copy(fontSize = RideType.body.fontSize), color = c.onBackground, modifier = Modifier.width(150.dp), textAlign = TextAlign.End)
        }
    }
}

@Composable
fun SendScreen(text: String, s: Settlement, names: Map<String, String>, account: String, onBack: () -> Unit, onDone: () -> Unit) {
    val c = Ride.colors
    val qr = remember(text) { qrBitmap(text, 900) }
    ScreenFrame("폰으로 받기") {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            Box(
                Modifier.fillMaxHeight().aspectRatio(1f).background(QrQuietZone, RoundedCornerShape(Sizes.cardRadius)).padding(Spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                if (qr != null) Image(qr, contentDescription = "정산 내용 QR 코드", modifier = Modifier.fillMaxSize())
                else Text("내용이 길어 QR을 만들 수 없습니다. 오른쪽 내용을 확인해 주세요.", style = RideType.body, color = c.error, textAlign = TextAlign.Center)
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("카메라로 찍으면 열리는 내용", style = RideType.title, color = c.onBackground)
                Text("앱 설치 없이 문자 그대로 열립니다.", style = RideType.body, color = c.onSurfaceMuted)
                Column {
                    s.transfers.forEach { t -> LogRow(null, "${names[t.fromId]} → ${names[t.toId]}", won(t.amountWon)) }
                }
                if (account.isNotBlank()) Text("받는 계좌 $account", style = RideType.body, color = c.onBackground)
            }
        }
        BottomBar {
            PrimaryButton("정산으로", onBack, Modifier.weight(1f), secondary = true)
            PrimaryButton("완료", onDone, Modifier.weight(2f))
        }
    }
}
