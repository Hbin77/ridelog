package com.ridelog.app.trip

import android.content.Context
import android.util.Log
import com.ridelog.app.vehicle.FakeVehicleDataSource
import com.ridelog.app.vehicle.PleosVehicleDataSource
import com.ridelog.app.vehicle.Signal
import com.ridelog.app.voice.SpeechIn
import com.ridelog.app.voice.SpeechOut
import com.ridelog.core.engine.EngineOutput
import com.ridelog.core.engine.TripEngine
import com.ridelog.core.engine.TripState
import com.ridelog.core.fare.EnergyConfig
import com.ridelog.core.fare.FareCalculator
import com.ridelog.core.fare.Settlement
import com.ridelog.core.model.Expense
import com.ridelog.core.model.Member
import com.ridelog.core.model.SeatId
import com.ridelog.core.model.VehicleEvent
import com.ridelog.core.voice.Payer
import com.ridelog.core.voice.VoiceCommand
import com.ridelog.core.voice.VoiceCommandParser
import com.ridelog.core.voice.VoiceExpenseParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class Phase { IDLE, ACTIVE, REVIEW }

/** 여행이 어느 차량 신호를 기준으로 기록되는지. 한 여행 안에서 섞지 않는다. */
enum class Source { PLEOS, FAKE }

data class UiState(
    val phase: Phase = Phase.IDLE,
    val driving: Boolean = false,
    val trip: TripState? = null,
    val source: Source = Source.PLEOS,
    val settlement: Settlement? = null,
    val totalKm: Double = 0.0,
    val lastMessage: String? = null,
    val listening: Boolean = false,
    /** 출발 직후 차량 정보를 읽는 동안 (최대 수 초) */
    val starting: Boolean = false,
    /** 여행과 무관하게 마지막으로 받은 차량 값 (홈 화면 상태 표시) */
    val vehicleSocPct: Double? = null,
)

/** 차량 이벤트 → 엔진 → UI 상태·음성 안내를 잇는 앱 계층. Application 범위로 하나만 산다. */
class TripController(
    context: Context,
    private val scope: CoroutineScope,
    val fake: FakeVehicleDataSource,
    val pleos: PleosVehicleDataSource,
    private val speechOut: SpeechOut,
    private val speechIn: SpeechIn,
    val settings: SettingsStore = SettingsStore(context),
    val store: TripStore = TripStore(context),
) {
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    private var batteryCapacityKwh: Double? = null
    private var listenTimeout: Job? = null

    private val _history = MutableStateFlow(store.history())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    val speechOutName get() = speechOut.engineName
    val speechInName get() = speechIn.engineName

    init {
        // 앱이 종료됐다가 다시 켜져도 진행 중이던 여행을 이어간다
        runCatching { store.loadActive()?.takeIf { it.isValid() } }
            .onFailure { Log.w(TAG, "저장된 여행 복원 실패", it) }
            .getOrNull()
            .also { if (it == null) runCatching { store.saveActive(null) } }
            ?.let { saved ->
                batteryCapacityKwh = saved.batteryCapacityKwh
                // Gson은 생성자를 거치지 않아 옛 파일에선 source 가 null 일 수 있다
                val source = (saved.source as Source?) ?: Source.PLEOS
                _ui.update { it.copy(phase = saved.phase, trip = saved.trip, source = source) }
                if (saved.phase == Phase.REVIEW) recompute(saved.trip)
            }
        // 구독을 먼저 걸고 나서 start() — 시작 직후 한 번 오는 현재 주행 상태를 놓치지 않는다
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            merge(pleos.events.map { Source.PLEOS to it }, fake.events.map { Source.FAKE to it })
                .collect { (src, e) -> onEvent(src, e) }
        }
        pleos.start()
    }

    private fun ActiveTrip.isValid(): Boolean = runCatching {
        trip.assignments.isNotEmpty() && trip.onboard.isNotEmpty() && trip.currentLeg.seats.isNotEmpty() &&
            trip.legs.all { it.memberIds.size >= 0 } && trip.expenses.all { it.sharedBy.isNotEmpty() }
    }.getOrDefault(false)

    private fun persist() {
        val s = _ui.value
        val trip = s.trip
        runCatching {
            store.saveActive(if (trip == null || s.phase == Phase.IDLE) null else ActiveTrip(s.phase, trip, batteryCapacityKwh, s.source))
        }.onFailure { Log.w(TAG, "여행 저장 실패", it) }
    }

    private fun onEvent(src: Source, e: VehicleEvent) {
        // 주행 상태는 화면 제한에 쓰이므로 어느 소스든 반영 (가상 차량은 데모 패널용)
        if (e is VehicleEvent.DrivingState) _ui.update { it.copy(driving = e.driving) }
        if (e is VehicleEvent.Soc && src == Source.PLEOS) _ui.update { it.copy(vehicleSocPct = e.percent) }
        val s = _ui.value
        val trip = s.trip ?: return
        if (s.phase != Phase.ACTIVE) return
        if (src != s.source && e !is VehicleEvent.DrivingState) return
        val step = TripEngine.reduce(trip, e)
        _ui.update { it.copy(trip = step.state) }
        step.outputs.forEach(::announce)
        // 디스크 쓰기는 구간·승하차·주행 상태가 바뀔 때만 (주행 중 CPU 절약)
        if (step.outputs.isNotEmpty() || e is VehicleEvent.DrivingState) persist()
    }

    private fun announce(o: EngineOutput) {
        val msg = when (o) {
            is EngineOutput.Boarded -> o.member?.let { "${it.name}님 탑승 기록할게요" } ?: "${o.seat.label} 탑승을 감지했어요. 정차 후 이름을 정해 주세요"
            is EngineOutput.Alighted -> o.member?.let { "${it.name}님 구간 기록을 마쳤어요" } ?: "${o.seat.label} 하차"
            is EngineOutput.ChargingRecorded -> "충전 기록을 남겼어요"
            is EngineOutput.LegClosed -> null
        } ?: return
        say(msg)
    }

    private fun say(msg: String) {
        _ui.update { it.copy(lastMessage = msg) }
        speechOut.speak(msg)
    }

    /** 좌석 센서가 지금 사람이 앉아 있다고 알려 주는 자리 (좌석 배정 화면 안내용). */
    fun detectedSeats(): Set<SeatId> =
        if (Signal.SEAT_OCCUPANCY in pleos.capabilities.value) pleos.occupiedSeats() else emptySet()

    fun startTrip(assignments: Map<SeatId, Member>) {
        if (_ui.value.phase != Phase.IDLE || _ui.value.starting) return
        _ui.update { it.copy(starting = true) }
        scope.launch {
            val pleosSnap = if (settings.forceFake) null else pleos.snapshot()
            // 실제 차의 주행거리를 못 읽으면 이번 여행은 통째로 가상 차량 기준
            val source = if (pleosSnap?.odometerKm != null) Source.PLEOS else Source.FAKE
            val snap = pleosSnap?.takeIf { source == Source.PLEOS } ?: fake.snapshot()
            batteryCapacityKwh = snap.batteryCapacityKwh ?: settings.batteryCapacityKwh
            val caps = pleos.capabilities.value
            val occupied = when {
                source == Source.FAKE -> fake.occupied.toSet()
                Signal.SEAT_OCCUPANCY in caps -> pleos.occupiedSeats()
                else -> null
            }
            val state = TripEngine.start(
                atMillis = System.currentTimeMillis(),
                assignments = assignments,
                odometerKm = snap.odometerKm ?: fake.odometerKm,
                socPct = snap.socPct,
                beltSupported = source == Source.FAKE || Signal.SEAT_BELT in caps,
                occupiedSeats = occupied,
            ).copy(driving = _ui.value.driving)
            settings.lastSeating = assignments.filterValues { !it.id.startsWith("guest") }.mapValues { it.value.name }
            _ui.update { it.copy(phase = Phase.ACTIVE, trip = state, source = source, settlement = null, starting = false) }
            persist()
            say("${assignments.size}명 여행 기록을 시작할게요")
        }
    }

    fun endTrip() {
        val trip = _ui.value.trip ?: return
        if (_ui.value.phase != Phase.ACTIVE) return
        val done = TripEngine.finish(trip, System.currentTimeMillis()).state
        _ui.update { it.copy(phase = Phase.REVIEW, trip = done) }
        recompute(done)
        persist()
        say("여행 기록을 마쳤어요. 정차 화면에서 확인해 주세요")
    }

    fun removeExpense(id: String) {
        val trip = _ui.value.trip ?: return
        val updated = TripEngine.removeExpense(trip, id)
        _ui.update { it.copy(trip = updated) }
        if (_ui.value.phase == Phase.REVIEW) recompute(updated)
        persist()
    }

    private fun recompute(done: TripState) {
        val owner = done.assignments[SeatId.DRIVER] ?: done.members.first()
        val config = EnergyConfig(settings.pricePerKwhWon, batteryCapacityKwh, settings.defaultKmPerKwh)
        val items = FareCalculator.energyItems(done.legs, owner.id, config) + FareCalculator.expenseItems(done.expenses)
        _ui.update {
            it.copy(settlement = FareCalculator.settle(done.members.map { m -> m.id }, items), totalKm = done.legs.sumOf { l -> l.distanceKm })
        }
    }

    /** 정산 화면을 닫으면 지난 여행으로 보관하고 초기화한다. */
    fun reset() {
        val s = _ui.value
        val trip = s.trip
        val settlement = s.settlement
        if (s.phase == Phase.REVIEW && trip != null && settlement != null && settlement.totalWon > 0) {
            runCatching { store.append(HistoryEntry(System.currentTimeMillis(), s.totalKm, settlement.totalWon, trip.members, settlement.transfers)) }
            _history.value = store.history()
        }
        _ui.update { UiState(driving = it.driving, vehicleSocPct = it.vehicleSocPct) }
        persist()
    }

    /** 마이크 버튼: 음성 경비 추가 또는 명령. 응답이 없으면 10초 뒤 풀어 준다. */
    fun listen() {
        if (_ui.value.listening) return
        _ui.update { it.copy(listening = true) }
        listenTimeout?.cancel()
        listenTimeout = scope.launch {
            delay(LISTEN_TIMEOUT_MS)
            if (_ui.value.listening) {
                _ui.update { it.copy(listening = false) }
                say("잘 못 들었어요. 다시 눌러 주세요")
            }
        }
        speechIn.listenOnce { text ->
            scope.launch {
                if (!_ui.value.listening) return@launch
                listenTimeout?.cancel()
                _ui.update { it.copy(listening = false) }
                if (text == null) say("잘 못 들었어요. 다시 눌러 주세요") else handleUtterance(text)
            }
        }
    }

    fun handleUtterance(text: String) {
        if (_ui.value.phase != Phase.ACTIVE) return say("여행을 먼저 시작해 주세요")
        val trip = _ui.value.trip ?: return
        val parsed = VoiceExpenseParser.parse(text, trip.members)
        if (parsed == null) {
            // 금액이 없을 때만 짧은 명령으로 해석
            when (VoiceCommandParser.parse(text)) {
                VoiceCommand.END_TRIP -> endTrip()
                VoiceCommand.START_TRIP, VoiceCommand.CANCEL -> Unit
                null -> say("금액을 못 알아들었어요. 예: 간식 3만원 내가 냈어")
            }
            return
        }
        val owner = trip.assignments[SeatId.DRIVER] ?: trip.members.first()
        val payer = when (val p = parsed.payer) {
            Payer.Me, Payer.Unknown -> owner
            is Payer.Named -> p.member
        }
        // 정차 중(휴게소)엔 직전 구간 동승자 전원, 주행 중엔 지금 탄 사람
        val lastLegMembers = trip.legs.lastOrNull()?.memberIds.orEmpty()
        val sharedBy = (if (trip.driving) trip.onboardMemberIds else trip.onboardMemberIds + lastLegMembers)
            .ifEmpty { trip.members.map { it.id }.toSet() }
        val expense = Expense(UUID.randomUUID().toString(), System.currentTimeMillis(), parsed.label, parsed.amountWon, payer.id, sharedBy)
        _ui.update { it.copy(trip = TripEngine.addExpense(trip, expense)) }
        persist()
        val who = if (parsed.payer is Payer.Unknown) "운전자" else "${payer.name}님"
        say("${parsed.label} ${parsed.amountWon}원, $who 결제로 기록했어요")
    }

    private companion object {
        const val TAG = "RideLog.Trip"
        const val LISTEN_TIMEOUT_MS = 10_000L
    }
}
