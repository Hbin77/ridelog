package com.ridelog.core.fare

import com.ridelog.core.model.Expense
import com.ridelog.core.model.Leg
import kotlin.math.floor
import kotlin.math.roundToLong

enum class EnergyMethod(val label: String) {
    /** 순간 전비를 주행거리로 적분 — 실측. */
    INTEGRATED_EFFICIENCY("실측"),
    /** 배터리 충전율 감소량 × 배터리 용량 — 실측. */
    SOC_DROP("실측"),
    /** 기본 전비로 추정. */
    DEFAULT_EFFICIENCY("추정"),
}

data class EnergyConfig(
    val pricePerKwhWon: Double,
    val batteryCapacityKwh: Double?,
    val defaultKmPerKwh: Double,
)

data class LegEnergy(val kwh: Double, val method: EnergyMethod)

object EnergyCostCalculator {
    /** 적분이 구간 거리의 이 비율 이상을 덮으면 적분값을 쓴다. */
    private const val MIN_INTEGRATED_COVERAGE = 0.8

    fun energyOf(leg: Leg, config: EnergyConfig): LegEnergy {
        val distance = leg.distanceKm
        if (distance > 0 && leg.integratedKm >= distance * MIN_INTEGRATED_COVERAGE) {
            val scaled = leg.integratedKwh * (distance / leg.integratedKm)
            return LegEnergy(scaled, EnergyMethod.INTEGRATED_EFFICIENCY)
        }
        val capacity = config.batteryCapacityKwh
        if (capacity != null && leg.socConsumedPct > 0) {
            return LegEnergy(leg.socConsumedPct / 100.0 * capacity, EnergyMethod.SOC_DROP)
        }
        return LegEnergy(distance / config.defaultKmPerKwh, EnergyMethod.DEFAULT_EFFICIENCY)
    }

    fun costWon(energy: LegEnergy, config: EnergyConfig): Long =
        (energy.kwh * config.pricePerKwhWon).roundToLong()
}

enum class CostKind { ENERGY, EXPENSE }

/** 나눌 비용 한 건. estimated 는 에너지 비용이 기본 전비로 추정됐는지 여부. */
data class CostItem(
    val label: String,
    val amountWon: Long,
    val payerId: String,
    val sharedBy: Set<String>,
    val estimated: Boolean = false,
    val kind: CostKind = CostKind.EXPENSE,
)

data class Transfer(val fromId: String, val toId: String, val amountWon: Long)

data class Settlement(
    val totalWon: Long,
    /** 멤버별 부담액(원, 소수 포함 정확값). */
    val owedByMember: Map<String, Double>,
    val paidByMember: Map<String, Long>,
    val transfers: List<Transfer>,
    val items: List<CostItem>,
)

object FareCalculator {

    /** 구간 에너지 비용은 차주(운전자)가 결제한 것으로 보고, 그 구간 탑승 멤버가 균등 부담한다. */
    fun energyItems(legs: List<Leg>, ownerId: String, config: EnergyConfig): List<CostItem> =
        legs.mapIndexedNotNull { i, leg ->
            val energy = EnergyCostCalculator.energyOf(leg, config)
            val cost = EnergyCostCalculator.costWon(energy, config)
            val sharers = leg.memberIds.ifEmpty { setOf(ownerId) }
            if (cost <= 0) null
            else CostItem(
                label = "주행 구간 ${i + 1} (${"%.1f".format(leg.distanceKm)}km)",
                amountWon = cost,
                payerId = ownerId,
                sharedBy = sharers,
                estimated = energy.method == EnergyMethod.DEFAULT_EFFICIENCY,
                kind = CostKind.ENERGY,
            )
        }

    fun expenseItems(expenses: List<Expense>): List<CostItem> =
        expenses.filter { it.amountWon > 0 && it.sharedBy.isNotEmpty() }
            .map { CostItem(it.label, it.amountWon, it.payerId, it.sharedBy) }

    fun settle(memberIds: Collection<String>, items: List<CostItem>): Settlement {
        val owed = memberIds.associateWith { 0.0 }.toMutableMap()
        val paid = memberIds.associateWith { 0L }.toMutableMap()
        for (item in items) {
            val share = item.amountWon.toDouble() / item.sharedBy.size
            item.sharedBy.forEach { owed[it] = (owed[it] ?: 0.0) + share }
            paid[item.payerId] = (paid[item.payerId] ?: 0L) + item.amountWon
        }
        val net = (owed.keys + paid.keys).associateWith { (paid[it] ?: 0L) - (owed[it] ?: 0.0) }
        return Settlement(
            totalWon = items.sumOf { it.amountWon },
            owedByMember = owed,
            paidByMember = paid,
            transfers = SettlementSolver.minimalTransfers(net),
            items = items,
        )
    }
}

object SettlementSolver {
    const val UNIT_WON = 10L

    /**
     * net > 0 은 받을 돈, net < 0 은 낼 돈. 큰 금액끼리 먼저 맞추는 greedy 로
     * 송금 건수를 (인원 - 1) 이하로 만든다. 송금액은 10원 단위 절사.
     */
    fun minimalTransfers(net: Map<String, Double>): List<Transfer> {
        val debtors = net.filterValues { it < 0 }.mapValues { -it.value }.toMutableMap()
        val creditors = net.filterValues { it > 0 }.toMutableMap()
        val result = mutableListOf<Transfer>()
        while (true) {
            val d = debtors.filterValues { it >= UNIT_WON }.maxByOrNull { it.value } ?: break
            val c = creditors.filterValues { it >= UNIT_WON }.maxByOrNull { it.value } ?: break
            val raw = minOf(d.value, c.value)
            val amount = floorToUnit(raw)
            if (amount > 0) result += Transfer(d.key, c.key, amount)
            debtors[d.key] = d.value - raw
            creditors[c.key] = c.value - raw
        }
        return result
    }

    fun floorToUnit(won: Double): Long = (floor(won / UNIT_WON) * UNIT_WON).toLong()
}
