package com.ridelog.core.fare

import com.ridelog.core.model.Leg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class FareTest {
    private val config = EnergyConfig(pricePerKwhWon = 350.0, batteryCapacityKwh = 77.4, defaultKmPerKwh = 5.0)

    private fun leg(km: Double, members: Set<String>, socPct: Double = 0.0, intKwh: Double = 0.0, intKm: Double = 0.0) =
        Leg(0, 1, 0.0, km, members, emptySet(), socPct, intKwh, intKm)

    @Test fun `integrated efficiency is preferred when it covers the leg`() {
        val e = EnergyCostCalculator.energyOf(leg(100.0, setOf("a"), socPct = 50.0, intKwh = 18.0, intKm = 90.0), config)
        assertEquals(EnergyMethod.INTEGRATED_EFFICIENCY, e.method)
        assertEquals(20.0, e.kwh, 1e-9)
    }

    @Test fun `soc drop is used when integration is sparse`() {
        val e = EnergyCostCalculator.energyOf(leg(100.0, setOf("a"), socPct = 20.0, intKwh = 1.0, intKm = 10.0), config)
        assertEquals(EnergyMethod.SOC_DROP, e.method)
        assertEquals(0.2 * 77.4, e.kwh, 1e-9)
    }

    @Test fun `default efficiency is the last fallback and marked estimated`() {
        val items = FareCalculator.energyItems(listOf(leg(50.0, setOf("a", "b"))), "a", config)
        assertEquals(3500L, items.single().amountWon)
        assertTrue(items.single().estimated)
    }

    @Test fun `four people share one full leg equally`() {
        val items = listOf(CostItem("전기", 20000, "a", setOf("a", "b", "c", "d")))
        val s = FareCalculator.settle(listOf("a", "b", "c", "d"), items)
        assertEquals(3, s.transfers.size)
        s.transfers.forEach { assertEquals("a", it.toId); assertEquals(5000L, it.amountWon) }
    }

    @Test fun `partial rider only pays for legs they were on`() {
        val legs = listOf(leg(60.0, setOf("a", "b", "c")), leg(40.0, setOf("a", "b")))
        val s = FareCalculator.settle(listOf("a", "b", "c"), FareCalculator.energyItems(legs, "a", config))
        // 구간1 60km → 4200원 / 3, 구간2 40km → 2800원 / 2
        assertEquals(1400.0, s.owedByMember.getValue("c"), 1e-9)
        assertEquals(1400.0 + 1400.0, s.owedByMember.getValue("b"), 1e-9)
    }

    @Test fun `driver-only leg is borne by the driver`() {
        val legs = listOf(leg(30.0, setOf("a")), leg(30.0, setOf("a", "b")))
        val s = FareCalculator.settle(listOf("a", "b"), FareCalculator.energyItems(legs, "a", config))
        assertEquals(1050.0, s.owedByMember.getValue("b"), 1e-9)
        assertEquals(listOf(Transfer("b", "a", 1050)), s.transfers)
    }

    @Test fun `passenger-paid expense offsets energy cost`() {
        val items = listOf(
            CostItem("전기", 10000, "a", setOf("a", "b")),
            CostItem("간식", 10000, "b", setOf("a", "b")),
        )
        val s = FareCalculator.settle(listOf("a", "b"), items)
        assertTrue(s.transfers.isEmpty())
    }

    @Test fun `transfers floor to 10 won and never exceed n minus 1`() {
        val rnd = Random(42)
        repeat(300) {
            val ids = (0 until rnd.nextInt(2, 6)).map { "m$it" }
            val items = (0 until rnd.nextInt(1, 8)).map {
                val sharers = ids.shuffled(rnd).take(rnd.nextInt(1, ids.size + 1)).toSet()
                CostItem("x", rnd.nextLong(1, 100_000), ids.random(rnd), sharers)
            }
            val s = FareCalculator.settle(ids, items)
            assertTrue(s.transfers.size <= ids.size - 1)
            s.transfers.forEach { assertEquals(0L, it.amountWon % 10) }
            // 각자 순잔액은 송금 후 (10원 × 인원) 미만으로 남는다
            val net = ids.associateWith { (s.paidByMember[it] ?: 0L) - (s.owedByMember[it] ?: 0.0) }.toMutableMap()
            s.transfers.forEach { t -> net[t.fromId] = net.getValue(t.fromId) + t.amountWon; net[t.toId] = net.getValue(t.toId) - t.amountWon }
            net.values.forEach { assertTrue("residual $it", abs(it) < 10.0 * ids.size) }
            assertEquals(items.sumOf { it.amountWon }.toDouble(), s.owedByMember.values.sum(), 1e-6)
        }
    }

    @Test fun `zero cost yields no transfers`() {
        val s = FareCalculator.settle(listOf("a", "b"), emptyList())
        assertEquals(0L, s.totalWon)
        assertTrue(s.transfers.isEmpty())
    }
}
