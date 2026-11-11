package com.ridelog.app.vehicle

import com.ridelog.core.model.VehicleEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 데이터 소스가 실제로 제공하는 신호 — 미지원 신호는 엔진이 폴백한다. */
enum class Signal { SEAT_OCCUPANCY, SEAT_BELT, ODOMETER, SOC, EFFICIENCY, BATTERY_CAPACITY, DRIVING_STATE }

data class VehicleSnapshot(
    val odometerKm: Double?,
    val socPct: Double?,
    val batteryCapacityKwh: Double?,
)

/** 차량 신호의 단일 출입구. Pleos 타입은 구현체 밖으로 나오지 않는다. */
interface VehicleDataSource {
    val name: String
    val events: Flow<VehicleEvent>
    val capabilities: StateFlow<Set<Signal>>
    /** 신호별 마지막 로그 (디버그 패널·스파이크 검증용). */
    val log: StateFlow<List<String>>
    fun start()
    fun stop()
    suspend fun snapshot(): VehicleSnapshot
}
