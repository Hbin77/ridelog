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

    /** 구간 하나만 볼 때의 에너지 (단독 계산·테스트용). */
    fun energyOf(leg: Leg, config: EnergyConfig): LegEnergy = energyOfTrip(listOf(leg), config).single()

    /**
     * 여행 전체에 한 가지 방법만 쓴다 — 구간마다 방법이 섞이면 같은 에너지가 두 번 잡힐 수 있다.
     * 1) 모든 구간이 순간 전비 적분으로 덮이면 적분값
     * 2) 배터리 용량과 SOC 감소가 있으면 여행 전체 SOC 감소 에너지를 거리 비율로 배분 (1% 해상도 보정)
     * 3) 둘 다 없으면 기본 전비 추정
     */
    fun energyOfTrip(legs: List<Leg>, config: EnergyConfig): List<LegEnergy> {
        val driven = legs.filter { it.distanceKm > 0 }
        if (driven.isNotEmpty() && driven.all { it.integratedKm >= it.distanceKm * MIN_INTEGRATED_COVERAGE }) {
            return legs.map {
                if (it.distanceKm <= 0) LegEnergy(0.0, EnergyMethod.INTEGRATED_EFFICIENCY)
                else LegEnergy(it.integratedKwh * (it.distanceKm / it.integratedKm), EnergyMethod.INTEGRATED_EFFICIENCY)
            }
        }
        val capacity = config.batteryCapacityKwh
        val totalSoc = legs.sumOf { it.socConsumedPct }
        val totalKm = legs.sumOf { it.distanceKm }
        if (capacity != null && totalSoc > 0) {
            val totalKwh = totalSoc / 100.0 * capacity
            return legs.map {
                val share = if (totalKm > 0) it.distanceKm / totalKm else it.socConsumedPct / totalSoc
                LegEnergy(totalKwh * share, EnergyMethod.SOC_DROP)
            }
        }
        return legs.map { LegEnergy(it.distanceKm / config.defaultKmPerKwh, EnergyMethod.DEFAULT_EFFICIENCY) }
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
    fun energyItems(legs: List<Leg>, ownerId: String, config: EnergyConfig): List<CostItem> {
        val energies = EnergyCostCalculator.energyOfTrip(legs, config)
        return legs.mapIndexedNotNull { i, leg ->
            val energy = energies[i]
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
