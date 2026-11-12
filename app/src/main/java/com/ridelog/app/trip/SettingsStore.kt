package com.ridelog.app.trip

import android.content.Context
import com.ridelog.core.model.Member

/** 저장된 동승 멤버와 정산 설정. 기기 안에만 저장한다. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("ridelog", Context.MODE_PRIVATE)

    var pricePerKwhWon: Double
        get() = prefs.getFloat("price", 347.2f).toDouble()
        set(v) = prefs.edit().putFloat("price", v.toFloat()).apply()

    var defaultKmPerKwh: Double
        get() = prefs.getFloat("defaultEff", 5.0f).toDouble()
        set(v) = prefs.edit().putFloat("defaultEff", v.toFloat()).apply()

    val batteryCapacityKwh: Double?
        get() = prefs.getFloat("capacity", 77.4f).toDouble()

    var account: String
        get() = prefs.getString("account", "") ?: ""
        set(v) = prefs.edit().putString("account", v).apply()

    var forceFake: Boolean
        get() = prefs.getBoolean("forceFake", false)
        set(v) = prefs.edit().putBoolean("forceFake", v).apply()

    /** 첫 번째가 운전자(차주). 처음엔 비어 있고, 첫 실행 때 운전자 이름만 받는다. */
    var members: List<Member>
        get() {
            val raw = prefs.getString("members", null) ?: return emptyList()
            return raw.split("\n").filter { it.isNotBlank() }.mapIndexed { i, name -> Member("m$i", name) }
        }
        set(v) = prefs.edit().putString("members", v.joinToString("\n") { it.name }).apply()

    val needsOnboarding: Boolean get() = members.isEmpty()

    /** 지난 여행의 좌석 배치(좌석 → 이름). "지난번처럼 출발"에 쓴다. */
    var lastSeating: Map<com.ridelog.core.model.SeatId, String>
        get() = (prefs.getString("lastSeating", null) ?: "").split(",").mapNotNull { pair ->
            val (k, v) = pair.split("=").takeIf { it.size == 2 } ?: return@mapNotNull null
            runCatching { com.ridelog.core.model.SeatId.valueOf(k) to v }.getOrNull()
        }.toMap()
        set(v) = prefs.edit().putString("lastSeating", v.entries.joinToString(",") { "${it.key.name}=${it.value}" }).apply()

    /** 좌석 배정 중 새로 태운 사람을 다음부터 바로 고를 수 있게 저장한다. */
    fun addMember(name: String): Member {
        val trimmed = name.trim().take(10)
        members.firstOrNull { it.name == trimmed }?.let { return it }
        val updated = members + Member("m${members.size}", trimmed)
        members = updated
        return updated.last()
    }
}
