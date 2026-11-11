package com.ridelog.app.vehicle

import com.ridelog.core.model.SeatId
import com.ridelog.core.model.VehicleEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 디버그 패널·데모 시나리오가 직접 신호를 넣는 가상 차량. */
class FakeVehicleDataSource(private val clock: () -> Long = System::currentTimeMillis) : VehicleDataSource {
    override val name = "가상 차량"
    private val _events = MutableSharedFlow<VehicleEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<VehicleEvent> = _events.asSharedFlow()
    override val capabilities = MutableStateFlow(Signal.entries.toSet()).asStateFlow()
    private val _log = MutableStateFlow<List<String>>(emptyList())
    override val log: StateFlow<List<String>> = _log.asStateFlow()

    var odometerKm = 12_480.0; private set
    var socPct = 82.0; private set
    var driving = false; private set
    val occupied = mutableSetOf(SeatId.DRIVER)
    val buckled = mutableSetOf(SeatId.DRIVER)

    override fun start() = Unit
    override fun stop() = Unit
    override suspend fun snapshot() = VehicleSnapshot(odometerKm, socPct, 77.4)

    private fun emit(e: VehicleEvent) {
        _events.tryEmit(e)
        _log.update { (listOf(e.toString()) + it).take(30) }
    }

    fun setOccupied(seat: SeatId, value: Boolean) {
        if (value) occupied += seat else { occupied -= seat; buckled -= seat }
        emit(VehicleEvent.SeatOccupancy(clock(), seat, value))
        if (!value) emit(VehicleEvent.SeatBelt(clock(), seat, false))
    }

    fun setBuckled(seat: SeatId, value: Boolean) {
        if (value) buckled += seat else buckled -= seat
        emit(VehicleEvent.SeatBelt(clock(), seat, value))
    }

    fun setDriving(value: Boolean) {
        driving = value
        emit(VehicleEvent.DrivingState(clock(), value))
    }

    /** 주행 시뮬레이션: 거리만큼 주행거리·SOC·전비를 함께 움직인다. */
    fun drive(km: Double, kmPerKwh: Double = 5.2, capacityKwh: Double = 77.4) {
        emit(VehicleEvent.Efficiency(clock(), kmPerKwh))
        val steps = 5
        repeat(steps) {
            odometerKm += km / steps
            emit(VehicleEvent.Odometer(clock(), odometerKm))
        }
        socPct = (socPct - km / kmPerKwh / capacityKwh * 100).coerceAtLeast(0.0)
        emit(VehicleEvent.Soc(clock(), socPct))
    }

    fun charge(toPct: Double) {
        emit(VehicleEvent.Charging(clock(), true))
        socPct = toPct.coerceAtMost(100.0)
        emit(VehicleEvent.Soc(clock(), socPct))
        emit(VehicleEvent.Charging(clock(), false))
    }
}
