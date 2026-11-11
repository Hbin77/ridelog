package com.ridelog.core.engine

import com.ridelog.core.model.ChargingStop
import com.ridelog.core.model.Expense
import com.ridelog.core.model.Leg
import com.ridelog.core.model.Member
import com.ridelog.core.model.SeatId
import com.ridelog.core.model.VehicleEvent

/** 엔진이 바깥(UI·음성·저장소)에 알리는 일. */
sealed interface EngineOutput {
    data class Boarded(val seat: SeatId, val member: Member?) : EngineOutput
    data class Alighted(val seat: SeatId, val member: Member?) : EngineOutput
    data class LegClosed(val leg: Leg) : EngineOutput
    data class ChargingRecorded(val stop: ChargingStop) : EngineOutput
}

data class OpenLeg(
    val startMillis: Long,
    val startKm: Double,
    val seats: Set<SeatId>,
    val socConsumedPct: Double = 0.0,
    val integratedKwh: Double = 0.0,
    val integratedKm: Double = 0.0,
)

/** 여행 한 건의 전체 상태. 불변 값이라 그대로 저장·복원할 수 있다. */
data class TripState(
    val startMillis: Long,
    val assignments: Map<SeatId, Member>,
    val beltSupported: Boolean,
    val driving: Boolean = false,
    val charging: Boolean = false,
    val chargingSince: Long? = null,
    val chargingStartSoc: Double? = null,
    val occupied: Set<SeatId> = emptySet(),
    val buckled: Set<SeatId> = emptySet(),
    val onboard: Set<SeatId>,
    val odometerKm: Double,
    val socPct: Double?,
    val efficiencyKmPerKwh: Double? = null,
    val currentLeg: OpenLeg,
    val legs: List<Leg> = emptyList(),
    val chargingStops: List<ChargingStop> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val ended: Boolean = false,
) {
    val onboardMemberIds: Set<String> get() = onboard.mapNotNull { assignments[it]?.id }.toSet()
    val members: List<Member> get() = assignments.values.distinctBy { it.id }
}

data class Step(val state: TripState, val outputs: List<EngineOutput>)

/**
 * 여행 기록 엔진 — 순수 리듀서.
 *
 * 규칙
 * - 운전석은 여행 내내 탑승으로 본다.
 * - 승차: 좌석 점유 + (벨트 체결 | 벨트 신호 미지원 | 주행 시작).
 * - 하차: 정차 중 점유 해제. 주행 중 점유 깜빡임은 무시하고, 정차하는 순간 다시 맞춘다.
 * - 탑승 좌석 집합이 바뀔 때마다 구간(Leg)을 닫고 새로 연다.
 * - SOC 증가분(충전)은 소비량에서 제외한다. 주행거리 역행 값은 무시한다.
 */
object TripEngine {

    fun start(
        atMillis: Long,
        assignments: Map<SeatId, Member>,
        odometerKm: Double,
        socPct: Double?,
        beltSupported: Boolean,
    ): TripState {
        val onboard = setOf(SeatId.DRIVER) + assignments.keys
        return TripState(
            startMillis = atMillis,
            assignments = assignments,
            beltSupported = beltSupported,
            onboard = onboard,
            occupied = onboard,
            odometerKm = odometerKm,
            socPct = socPct,
            currentLeg = OpenLeg(atMillis, odometerKm, onboard),
        )
    }

    fun reduce(state: TripState, event: VehicleEvent): Step {
        if (state.ended) return Step(state, emptyList())
        return when (event) {
            is VehicleEvent.SeatOccupancy -> onOccupancy(state, event)
            is VehicleEvent.SeatBelt -> onBelt(state, event)
            is VehicleEvent.Odometer -> Step(onOdometer(state, event.km), emptyList())
            is VehicleEvent.Soc -> onSoc(state, event)
            is VehicleEvent.Efficiency ->
                if (event.kmPerKwh > 0) Step(state.copy(efficiencyKmPerKwh = event.kmPerKwh), emptyList())
                else Step(state, emptyList())
            is VehicleEvent.Charging -> onCharging(state, event)
            is VehicleEvent.DrivingState -> onDriving(state, event)
        }
    }

    fun addExpense(state: TripState, expense: Expense): TripState =
        state.copy(expenses = state.expenses + expense)

    fun removeExpense(state: TripState, expenseId: String): TripState =
        state.copy(expenses = state.expenses.filterNot { it.id == expenseId })

    /** 여행 종료 — 열린 구간과 충전을 닫는다. */
    fun finish(state: TripState, atMillis: Long): Step {
        if (state.ended) return Step(state, emptyList())
        val outputs = mutableListOf<EngineOutput>()
        var s = state
        if (s.charging) {
            val (closed, out) = closeCharging(s, atMillis)
            s = closed; outputs += out
        }
        val leg = closeLeg(s, atMillis)
        outputs += EngineOutput.LegClosed(leg)
        s = s.copy(legs = s.legs + leg, ended = true)
        return Step(s, outputs)
    }

    // ---- 좌석 ----

    private fun onOccupancy(state: TripState, e: VehicleEvent.SeatOccupancy): Step {
        if (e.seat == SeatId.DRIVER) return Step(state, emptyList())
        val occupied = if (e.occupied) state.occupied + e.seat else state.occupied - e.seat
        val s = state.copy(occupied = occupied)
        return if (e.occupied) tryBoard(s, e.seat, e.atMillis) else tryAlight(s, e.seat, e.atMillis)
    }

    private fun onBelt(state: TripState, e: VehicleEvent.SeatBelt): Step {
        val buckled = if (e.buckled) state.buckled + e.seat else state.buckled - e.seat
        val s = state.copy(buckled = buckled)
        return if (e.buckled && e.seat in s.occupied) tryBoard(s, e.seat, e.atMillis) else Step(s, emptyList())
    }

    private fun canBoard(s: TripState, seat: SeatId): Boolean =
        seat in s.occupied && (seat in s.buckled || !s.beltSupported || s.driving)

    private fun tryBoard(s: TripState, seat: SeatId, at: Long): Step {
        if (seat in s.onboard || !canBoard(s, seat)) return Step(s, emptyList())
        return changeOnboard(s, s.onboard + seat, at, listOf(EngineOutput.Boarded(seat, s.assignments[seat])))
    }

    private fun tryAlight(s: TripState, seat: SeatId, at: Long): Step {
        if (seat !in s.onboard || s.driving) return Step(s, emptyList())
        return changeOnboard(s, s.onboard - seat, at, listOf(EngineOutput.Alighted(seat, s.assignments[seat])))
    }

    private fun changeOnboard(s: TripState, newOnboard: Set<SeatId>, at: Long, events: List<EngineOutput>): Step {
        val leg = closeLeg(s, at)
        val next = s.copy(
            onboard = newOnboard,
            legs = if (leg.distanceKm > 0 || leg.socConsumedPct > 0) s.legs + leg else s.legs,
            currentLeg = OpenLeg(at, s.odometerKm, newOnboard),
        )
        val outputs = if (leg.distanceKm > 0 || leg.socConsumedPct > 0) events + EngineOutput.LegClosed(leg) else events
        return Step(next, outputs)
    }

    private fun closeLeg(s: TripState, at: Long): Leg {
        val open = s.currentLeg
        return Leg(
            startMillis = open.startMillis,
            endMillis = at,
            startKm = open.startKm,
            endKm = s.odometerKm,
            memberIds = open.seats.mapNotNull { s.assignments[it]?.id }.toSet(),
            unassignedSeats = open.seats.filter { it !in s.assignments && it != SeatId.DRIVER }.toSet(),
            socConsumedPct = open.socConsumedPct,
            integratedKwh = open.integratedKwh,
            integratedKm = open.integratedKm,
        )
    }

    // ---- 주행 상태 ----

    private fun onDriving(state: TripState, e: VehicleEvent.DrivingState): Step {
        if (state.driving == e.driving) return Step(state, emptyList())
        var s = state.copy(driving = e.driving)
        val outputs = mutableListOf<EngineOutput>()
        if (e.driving) {
            // 주행 시작: 점유됐지만 벨트 미체결인 좌석도 탑승으로 확정
            for (seat in s.occupied - s.onboard) {
                val step = tryBoard(s, seat, e.atMillis)
                s = step.state; outputs += step.outputs
            }
        } else {
            // 정차: 주행 중 무시했던 하차를 반영
            for (seat in s.onboard - s.occupied - SeatId.DRIVER) {
                val step = tryAlight(s, seat, e.atMillis)
                s = step.state; outputs += step.outputs
            }
        }
        return Step(s, outputs)
    }

    // ---- 에너지 ----

    private fun onOdometer(state: TripState, km: Double): TripState {
        val delta = km - state.odometerKm
        if (delta <= 0) return state
        val eff = state.efficiencyKmPerKwh
        val leg = state.currentLeg
        val newLeg = if (eff != null) {
            leg.copy(integratedKwh = leg.integratedKwh + delta / eff, integratedKm = leg.integratedKm + delta)
        } else leg
        return state.copy(odometerKm = km, currentLeg = newLeg)
    }

    private fun onSoc(state: TripState, e: VehicleEvent.Soc): Step {
        val prev = state.socPct ?: return Step(state.copy(socPct = e.percent), emptyList())
        val drop = prev - e.percent
        val leg = if (drop > 0) state.currentLeg.copy(socConsumedPct = state.currentLeg.socConsumedPct + drop) else state.currentLeg
        var s = state.copy(socPct = e.percent, currentLeg = leg)
        // 충전 신호가 없더라도 정차 중 SOC가 오르면 충전으로 본다
        if (drop < 0 && !s.driving && !s.charging) {
            s = s.copy(charging = true, chargingSince = e.atMillis, chargingStartSoc = prev)
        }
        return Step(s, emptyList())
    }

    private fun onCharging(state: TripState, e: VehicleEvent.Charging): Step {
        if (e.active == state.charging) return Step(state, emptyList())
        if (e.active) {
            return Step(state.copy(charging = true, chargingSince = e.atMillis, chargingStartSoc = state.socPct), emptyList())
        }
        val (s, out) = closeCharging(state, e.atMillis)
        return Step(s, out)
    }

    private fun closeCharging(state: TripState, at: Long): Pair<TripState, List<EngineOutput>> {
        val stop = ChargingStop(state.chargingSince ?: at, at, state.chargingStartSoc, state.socPct)
        val s = state.copy(
            charging = false, chargingSince = null, chargingStartSoc = null,
            chargingStops = state.chargingStops + stop,
        )
        return s to listOf(EngineOutput.ChargingRecorded(stop))
    }
}
