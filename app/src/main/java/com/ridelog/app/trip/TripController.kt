package com.ridelog.app.trip

import android.content.Context
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class Phase { IDLE, ACTIVE, REVIEW }

data class UiState(
    val phase: Phase = Phase.IDLE,
    val driving: Boolean = false,
    val trip: TripState? = null,
    val settlement: Settlement? = null,
    val totalKm: Double = 0.0,
    val lastMessage: String? = null,
    val listening: Boolean = false,
)

/** 차량 이벤트 → 엔진 → UI 상태·음성 안내를 잇는 앱 계층. */
class TripController(
    context: Context,
    private val scope: CoroutineScope,
    val fake: FakeVehicleDataSource,
    val pleos: PleosVehicleDataSource,
    private val speechOut: SpeechOut,
    private val speechIn: SpeechIn,
    val settings: SettingsStore = SettingsStore(context),
) {
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    private var batteryCapacityKwh: Double? = null

    val speechOutName get() = speechOut.engineName
    val speechInName get() = speechIn.engineName

    init {
        pleos.start()
        scope.launch { merge(pleos.events, fake.events).collect(::onEvent) }
    }

    private fun onEvent(e: VehicleEvent) {
        if (e is VehicleEvent.DrivingState) _ui.update { it.copy(driving = e.driving) }
        val trip = _ui.value.trip ?: return
        if (_ui.value.phase != Phase.ACTIVE) return
        val step = TripEngine.reduce(trip, e)
        _ui.update { it.copy(trip = step.state) }
        step.outputs.forEach(::announce)
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

    fun startTrip(assignments: Map<SeatId, Member>) {
        scope.launch {
            val usingFake = settings.forceFake || Signal.ODOMETER !in pleos.capabilities.value
            val snap = if (usingFake) fake.snapshot() else pleos.snapshot()
            batteryCapacityKwh = snap.batteryCapacityKwh ?: settings.batteryCapacityKwh
            val belt = Signal.SEAT_BELT in pleos.capabilities.value || usingFake
            val state = TripEngine.start(
                atMillis = System.currentTimeMillis(),
                assignments = assignments,
                odometerKm = snap.odometerKm ?: fake.odometerKm,
                socPct = snap.socPct,
                beltSupported = belt,
            )
            _ui.update { it.copy(phase = Phase.ACTIVE, trip = state, settlement = null) }
            say("${assignments.size}명 여행 기록을 시작할게요")
        }
    }

    fun endTrip() {
        val trip = _ui.value.trip ?: return
        val done = TripEngine.finish(trip, System.currentTimeMillis()).state
        val owner = done.assignments[SeatId.DRIVER] ?: done.members.first()
        val config = EnergyConfig(settings.pricePerKwhWon, batteryCapacityKwh, settings.defaultKmPerKwh)
        val items = FareCalculator.energyItems(done.legs, owner.id, config) + FareCalculator.expenseItems(done.expenses)
        val settlement = FareCalculator.settle(done.members.map { it.id }, items)
        _ui.update {
            it.copy(phase = Phase.REVIEW, trip = done, settlement = settlement, totalKm = done.legs.sumOf { l -> l.distanceKm })
        }
        say("여행 기록을 마쳤어요. 정차 화면에서 확인해 주세요")
    }

    fun removeExpense(id: String) {
        val trip = _ui.value.trip ?: return
        val updated = TripEngine.removeExpense(trip, id)
        _ui.update { it.copy(trip = updated) }
        if (_ui.value.phase == Phase.REVIEW) recompute(updated)
    }

    private fun recompute(done: TripState) {
        val owner = done.assignments[SeatId.DRIVER] ?: done.members.first()
        val config = EnergyConfig(settings.pricePerKwhWon, batteryCapacityKwh, settings.defaultKmPerKwh)
        val items = FareCalculator.energyItems(done.legs, owner.id, config) + FareCalculator.expenseItems(done.expenses)
        _ui.update { it.copy(settlement = FareCalculator.settle(done.members.map { m -> m.id }, items)) }
    }

    fun reset() = _ui.update { UiState(driving = it.driving) }

    /** 마이크 버튼: 음성 경비 추가 또는 명령. */
    fun listen() {
        if (_ui.value.listening) return
        _ui.update { it.copy(listening = true) }
        speechIn.listenOnce { text ->
            scope.launch {
                _ui.update { it.copy(listening = false) }
                if (text == null) say("잘 못 들었어요. 다시 눌러 주세요") else handleUtterance(text)
            }
        }
    }

    fun handleUtterance(text: String) {
        when (VoiceCommandParser.parse(text)) {
            VoiceCommand.END_TRIP -> { if (_ui.value.phase == Phase.ACTIVE) endTrip(); return }
            VoiceCommand.START_TRIP, VoiceCommand.CANCEL -> return
            null -> Unit
        }
        val trip = _ui.value.trip ?: return say("여행을 먼저 시작해 주세요")
        val parsed = VoiceExpenseParser.parse(text, trip.members) ?: return say("금액을 못 알아들었어요. 예: 간식 3만원 내가 냈어")
        val owner = trip.assignments[SeatId.DRIVER] ?: trip.members.first()
        val payer = when (val p = parsed.payer) {
            Payer.Me, Payer.Unknown -> owner
            is Payer.Named -> p.member
        }
        val sharedBy = trip.onboardMemberIds.ifEmpty { trip.members.map { it.id }.toSet() }
        val expense = Expense(UUID.randomUUID().toString(), System.currentTimeMillis(), parsed.label, parsed.amountWon, payer.id, sharedBy)
        _ui.update { it.copy(trip = TripEngine.addExpense(trip, expense)) }
        say("${parsed.label} ${parsed.amountWon}원 기록했어요")
    }
}
