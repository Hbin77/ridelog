package com.ridelog.core.model

/** 차량 좌석. Pleos SeatValue(ROW_n_LEFT/CENTER/RIGHT)와 1:1로 매핑된다. */
enum class SeatId(val label: String) {
    DRIVER("운전석"),
    FRONT_PASSENGER("조수석"),
    REAR_LEFT("뒷좌석 왼쪽"),
    REAR_CENTER("뒷좌석 가운데"),
    REAR_RIGHT("뒷좌석 오른쪽"),
}

data class Member(val id: String, val name: String)

/** 차량에서 들어오는 신호. 모든 이벤트는 발생 시각(ms)을 가진다 — 엔진은 벽시계를 쓰지 않는다. */
sealed interface VehicleEvent {
    val atMillis: Long

    data class SeatOccupancy(override val atMillis: Long, val seat: SeatId, val occupied: Boolean) : VehicleEvent
    data class SeatBelt(override val atMillis: Long, val seat: SeatId, val buckled: Boolean) : VehicleEvent

    /** 계기판 누적 주행거리(km). */
    data class Odometer(override val atMillis: Long, val km: Double) : VehicleEvent

    /** 배터리 충전율(%). */
    data class Soc(override val atMillis: Long, val percent: Double) : VehicleEvent

    /** 순간 전비(km/kWh). */
    data class Efficiency(override val atMillis: Long, val kmPerKwh: Double) : VehicleEvent

    data class Charging(override val atMillis: Long, val active: Boolean) : VehicleEvent

    /** 주행 규제 상태. true = 주행 중(화면 제한), false = 정차. */
    data class DrivingState(override val atMillis: Long, val driving: Boolean) : VehicleEvent
}

/** 탑승자 구성이 바뀌지 않은 한 구간. */
data class Leg(
    val startMillis: Long,
    val endMillis: Long,
    val startKm: Double,
    val endKm: Double,
    /** 이 구간에 탑승한 등록 멤버 id. */
    val memberIds: Set<String>,
    /** 점유됐지만 멤버가 배정되지 않은 좌석. */
    val unassignedSeats: Set<SeatId>,
    /** 충전으로 인한 증가분을 제외한 SOC 감소량(%p). */
    val socConsumedPct: Double,
    /** 순간 전비로 적분한 소비 에너지(kWh)와 그 적분이 커버한 거리(km). */
    val integratedKwh: Double,
    val integratedKm: Double,
) {
    val distanceKm: Double get() = (endKm - startKm).coerceAtLeast(0.0)
}

data class ChargingStop(
    val startMillis: Long,
    val endMillis: Long,
    val startSocPct: Double?,
    val endSocPct: Double?,
)

data class Expense(
    val id: String,
    val atMillis: Long,
    val label: String,
    val amountWon: Long,
    val payerId: String,
    val sharedBy: Set<String>,
)
