package com.ridelog.app.trip

import android.content.Context
import com.google.gson.reflect.TypeToken
import com.ridelog.core.engine.TripState
import com.ridelog.core.fare.SettlementSolver
import com.ridelog.core.fare.Transfer
import com.ridelog.core.model.Member
import java.io.File
import java.util.Calendar

/** 끝난 여행 한 건의 요약 (월 정산용). */
data class HistoryEntry(
    val endedAtMillis: Long,
    val totalKm: Double,
    val totalWon: Long,
    val members: List<Member>,
    val transfers: List<Transfer>,
)

/** 진행 중인 여행. 앱이 종료돼도 이어서 기록할 수 있게 저장한다. */
data class ActiveTrip(val phase: Phase, val trip: TripState, val batteryCapacityKwh: Double?, val source: Source = Source.PLEOS)

/** 기기 안 파일(JSON)에만 저장한다. 서버 전송 없음. */
class TripStore(context: Context) {
    // NaN 등이 섞여도 저장이 예외로 죽지 않게 한다 (엔진에서도 걸러낸다)
    private val gson = com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create()
    private val activeFile = File(context.filesDir, "active_trip.json")
    private val historyFile = File(context.filesDir, "history.json")

    fun saveActive(active: ActiveTrip?) {
        if (active == null) { activeFile.delete(); return }
        writeAtomic(activeFile, gson.toJson(active))
    }

    fun loadActive(): ActiveTrip? = runCatching {
        if (!activeFile.exists()) null else gson.fromJson(activeFile.readText(), ActiveTrip::class.java)
    }.getOrNull()

    fun history(): List<HistoryEntry> = runCatching {
        if (!historyFile.exists()) emptyList()
        else gson.fromJson<List<HistoryEntry>>(historyFile.readText(), object : TypeToken<List<HistoryEntry>>() {}.type)
    }.getOrNull() ?: emptyList()

    fun append(entry: HistoryEntry) {
        writeAtomic(historyFile, gson.toJson((listOf(entry) + history()).take(200)))
    }

    private fun writeAtomic(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        tmp.renameTo(target)
    }
}

data class MonthlySummary(val year: Int, val month: Int, val trips: Int, val totalKm: Double, val transfers: List<Transfer>, val names: Map<String, String>)

/** 같은 달 여행들의 송금을 이름 기준으로 상계해 한 번에 보낼 목록을 만든다. */
fun monthlySummary(history: List<HistoryEntry>, nowMillis: Long = System.currentTimeMillis()): MonthlySummary {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val y = cal.get(Calendar.YEAR); val m = cal.get(Calendar.MONTH)
    val inMonth = history.filter {
        val c = Calendar.getInstance().apply { timeInMillis = it.endedAtMillis }
        c.get(Calendar.YEAR) == y && c.get(Calendar.MONTH) == m
    }
    // 여행마다 멤버 id가 달라도 같은 이름이면 같은 사람으로 합친다
    val net = mutableMapOf<String, Double>()
    inMonth.forEach { e ->
        val name = e.members.associate { it.id to it.name }
        e.transfers.forEach { t ->
            val from = name[t.fromId] ?: t.fromId
            val to = name[t.toId] ?: t.toId
            net[from] = (net[from] ?: 0.0) - t.amountWon
            net[to] = (net[to] ?: 0.0) + t.amountWon
        }
    }
    return MonthlySummary(y, m + 1, inMonth.size, inMonth.sumOf { it.totalKm }, SettlementSolver.minimalTransfers(net), net.keys.associateWith { it })
}
