package com.ridelog.app.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.EvBatteryEfficiencyListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.ridelog.core.model.SeatId
import com.ridelog.core.model.VehicleEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Pleos Connect Vehicle SDK(조회 권한만) + AAOS CarUxRestrictions(주행 상태) 연결.
 * 리스너 기반이라 폴링이 없다 (주행 중 CPU 5% 제약).
 */
class PleosVehicleDataSource(
    private val context: Context,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : VehicleDataSource {
    override val name = "Pleos 차량"
    private val _events = MutableSharedFlow<VehicleEvent>(extraBufferCapacity = 128)
    override val events: SharedFlow<VehicleEvent> = _events.asSharedFlow()
    private val _caps = MutableStateFlow<Set<Signal>>(emptySet())
    override val capabilities: StateFlow<Set<Signal>> = _caps.asStateFlow()
    private val _log = MutableStateFlow<List<String>>(emptyList())
    override val log: StateFlow<List<String>> = _log.asStateFlow()

    private var vehicle: Vehicle? = null
    private var car: Car? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private val unregisters: MutableList<() -> Unit> = java.util.Collections.synchronizedList(mutableListOf())
    private val lastOccupancy = java.util.concurrent.ConcurrentHashMap<SeatId, Boolean>()

    /**
     * Pleos Connect Emulator 2.1.0 Virtual Console은 좌석 점유를 반대로 전달한다
     * (콘솔 "Occupied" → SDK false). 실차 VHAL 의미와 맞추기 위해 에뮬레이터에서만 뒤집는다.
     * docs/SPIKE_RESULT.md 참고.
     */
    private val invertOccupancy = Build.HARDWARE == "ranchu" || Build.FINGERPRINT.contains("emu")

    private val seatMap = mapOf(
        SeatId.DRIVER to SeatValue.ROW_1_LEFT,
        SeatId.FRONT_PASSENGER to SeatValue.ROW_1_RIGHT,
        SeatId.REAR_LEFT to SeatValue.ROW_2_LEFT,
        SeatId.REAR_CENTER to SeatValue.ROW_2_CENTER,
        SeatId.REAR_RIGHT to SeatValue.ROW_2_RIGHT,
    )

    private fun log(msg: String) {
        Log.i(TAG, msg)
        _log.update { (listOf(msg) + it).take(40) }
    }

    private fun emit(e: VehicleEvent) {
        _events.tryEmit(e)
        log(e.toString())
    }

    private fun addCap(s: Signal) = _caps.update { it + s }

    override fun start() {
        if (vehicle != null) return
        val v = runCatching { Vehicle(context).also { it.initialize() } }
            .onFailure { log("Vehicle 초기화 실패: ${it.message}") }
            .getOrNull()
        vehicle = v
        startDrivingState()
        if (v == null) return
        // initialize()는 서비스 바인딩이 비동기이므로 잠시 후 등록하고, 실패하면 몇 번 다시 시도한다.
        scope.launch {
            for (attempt in 1..4) {
                delay(1_000L * attempt)
                val ok = runCatching { registerSeats(v); registerEnergy(v) }
                    .onFailure { log("리스너 등록 실패($attempt): ${it.message}"); unregisters.toList().forEach { u -> runCatching(u) }; unregisters.clear() }
                    .isSuccess
                if (ok) break
            }
        }
    }

    private fun registerSeats(v: Vehicle) {
        val seat = v.getSeat()
        seat.checkSeatOccupancyCapabilities({ _, areas ->
            val supported = areas.flatMap { it.values.toList() }.toSet()
            log("좌석 점유 지원: $supported")
            if (supported.isNotEmpty()) addCap(Signal.SEAT_OCCUPANCY)
            seatMap.filterValues { it in supported }.forEach { (id, value) ->
                val area = SeatArea(value)
                val l = object : SeatOccupancyListener {
                    override fun onSeatOccupancyUpdated(p0: Boolean?) {
                        p0?.let {
                            val occupied = it != invertOccupancy
                            lastOccupancy[id] = occupied
                            emit(VehicleEvent.SeatOccupancy(clock(), id, occupied))
                        }
                    }
                    override fun onFailed(p0: Exception) = log("점유 리스너 실패 $id: ${p0.message}")
                }
                seat.registerSeatOccupancy(area, l)
                unregisters += { seat.unregisterSeatOccupancy(area, l) }
            }
        }, { log("좌석 점유 capability 실패: ${it.message}") })

        seat.checkSeatBeltBuckledCapabilities({ _, areas ->
            val supported = areas.flatMap { it.values.toList() }.toSet()
            log("벨트 지원: $supported")
            if (supported.isNotEmpty()) addCap(Signal.SEAT_BELT)
            seatMap.filterValues { it in supported }.forEach { (id, value) ->
                val area = SeatArea(value)
                val l = object : SeatBeltBuckledStateListener {
                    override fun onSeatBeltBuckledStateUpdated(p0: Boolean?) {
                        p0?.let { emit(VehicleEvent.SeatBelt(clock(), id, it)) }
                    }
                    override fun onFailed(p0: Exception) = log("벨트 리스너 실패 $id: ${p0.message}")
                }
                seat.registerSeatBeltBuckledState(area, l)
                unregisters += { seat.unregisterSeatBeltBuckledState(area, l) }
            }
        }, { log("벨트 capability 실패: ${it.message}") })
    }

    private fun registerEnergy(v: Vehicle) {
        val odo = v.getOdometer()
        val odoL = object : DistanceDrivenListener {
            override fun onDistanceDrivenUpdated(p0: Float?) {
                p0?.let { addCap(Signal.ODOMETER); emit(VehicleEvent.Odometer(clock(), it.toDouble())) }
            }
            override fun onFailed(p0: Exception) = log("주행거리 리스너 실패: ${p0.message}")
        }
        odo.registerDistanceDriven(odoL)
        unregisters += { odo.unregisterDistanceDriven(odoL) }

        val ev = v.getEvBattery()
        val socL = object : EvBatteryStateOfChargeListener {
            override fun onEvBatteryStateOfChargeUpdated(p0: Int?) {
                p0?.let { addCap(Signal.SOC); emit(VehicleEvent.Soc(clock(), it.toDouble())) }
            }
            override fun onFailed(p0: Exception) = log("SOC 리스너 실패: ${p0.message}")
        }
        ev.registerEvBatteryStateOfCharge(socL)
        unregisters += { ev.unregisterEvBatteryStateOfCharge(socL) }

        val effL = object : EvBatteryEfficiencyListener {
            override fun onEvBatteryEfficiencyUpdated(p0: Float?) {
                p0?.let { addCap(Signal.EFFICIENCY); emit(VehicleEvent.Efficiency(clock(), it.toDouble())) }
            }
            override fun onFailed(p0: Exception) = log("전비 리스너 실패: ${p0.message}")
        }
        ev.registerInstantaneousEvEfficiency(effL)
        unregisters += { ev.unregisterInstantaneousEvEfficiency(effL) }
    }

    private fun startDrivingState() {
        runCatching {
            val c = Car.createCar(context)
            val mgr = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            car = c
            uxManager = mgr
            mgr.currentCarUxRestrictions?.let {
                addCap(Signal.DRIVING_STATE)
                emit(VehicleEvent.DrivingState(clock(), it.isRequiresDistractionOptimization))
            }
            mgr.registerListener { r ->
                addCap(Signal.DRIVING_STATE)
                emit(VehicleEvent.DrivingState(clock(), r.isRequiresDistractionOptimization))
            }
        }.onFailure { log("주행 상태(CarUxRestrictions) 연결 실패: ${it.message}") }
    }

    /** 마지막으로 받은 좌석 점유 상태 (출발 시점 좌석 확인용). */
    fun occupiedSeats(): Set<SeatId> = lastOccupancy.filterValues { it }.keys + SeatId.DRIVER

    override suspend fun snapshot(): VehicleSnapshot {
        val v = vehicle ?: return VehicleSnapshot(null, null, null)
        val km = withTimeoutOrNull(2_000) {
            suspendCancellableCoroutine { c ->
                v.getOdometer().getCurrentDistanceDriven({ c.resume(it?.toDouble()) }, { c.resume(null) })
            }
        }
        val soc = withTimeoutOrNull(2_000) {
            suspendCancellableCoroutine { c ->
                v.getEvBattery().getEvBatteryStateOfCharge({ c.resume(it?.toDouble()) }, { c.resume(null) })
            }
        }
        val cap = withTimeoutOrNull(2_000) {
            suspendCancellableCoroutine { c ->
                v.getEvBattery().getEvBatteryCapacity({ c.resume(it?.toDouble()) }, { c.resume(null) })
            }
        }
        // SDK는 Wh 단위로 준다 (에뮬레이터 77400.0 = 77.4kWh)
        val capKwh = cap?.let { if (it > 1_000) it / 1_000 else it }?.takeIf { it > 0 }
        if (capKwh != null) addCap(Signal.BATTERY_CAPACITY)
        log("스냅샷: 주행거리=$km, SOC=$soc, 배터리용량=${capKwh}kWh (원시값 $cap)")
        return VehicleSnapshot(km, soc, capKwh)
    }

    override fun stop() {
        unregisters.forEach { runCatching(it) }
        unregisters.clear()
        runCatching { uxManager?.unregisterListener() }
        runCatching { car?.disconnect() }
        runCatching { vehicle?.release() }
        vehicle = null; car = null; uxManager = null
    }

    private companion object { const val TAG = "RideLog.Pleos" }
}
