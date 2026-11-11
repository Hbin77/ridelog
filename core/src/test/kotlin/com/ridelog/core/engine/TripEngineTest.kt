package com.ridelog.core.engine

import com.ridelog.core.model.Member
import com.ridelog.core.model.SeatId
import com.ridelog.core.model.VehicleEvent.Charging
import com.ridelog.core.model.VehicleEvent.DrivingState
import com.ridelog.core.model.VehicleEvent.Efficiency
import com.ridelog.core.model.VehicleEvent.Odometer
import com.ridelog.core.model.VehicleEvent.SeatBelt
import com.ridelog.core.model.VehicleEvent.SeatOccupancy
import com.ridelog.core.model.VehicleEvent.Soc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripEngineTest {
    private val me = Member("me", "지훈")
    private val minsu = Member("m", "민수")
    private val seoyeon = Member("s", "서연")

    private fun fourSeatTrip() = TripEngine.start(
        atMillis = 0,
        assignments = mapOf(SeatId.DRIVER to me, SeatId.FRONT_PASSENGER to minsu, SeatId.REAR_LEFT to seoyeon),
        odometerKm = 1000.0, socPct = 80.0, beltSupported = true,
    )

    private fun TripState.apply(vararg events: com.ridelog.core.model.VehicleEvent): Pair<TripState, List<EngineOutput>> {
        var s = this
        val out = mutableListOf<EngineOutput>()
        events.forEach { val step = TripEngine.reduce(s, it); s = step.state; out += step.outputs }
        return s to out
    }

    @Test fun `single leg when nobody gets on or off`() {
        val (s, _) = fourSeatTrip().apply(DrivingState(1, true), Odometer(2, 1030.0), DrivingState(3, false))
        val done = TripEngine.finish(s, 4).state
        assertEquals(1, done.legs.size)
        assertEquals(30.0, done.legs[0].distanceKm, 1e-9)
        assertEquals(setOf("me", "m", "s"), done.legs[0].memberIds)
    }

    @Test fun `mid-trip alighting splits legs`() {
        val (s, out) = fourSeatTrip().apply(
            DrivingState(1, true), Odometer(2, 1020.0), DrivingState(3, false),
            SeatOccupancy(4, SeatId.REAR_LEFT, false),
            DrivingState(5, true), Odometer(6, 1050.0), DrivingState(7, false),
        )
        val done = TripEngine.finish(s, 8).state
        assertEquals(2, done.legs.size)
        assertEquals(setOf("me", "m", "s"), done.legs[0].memberIds)
        assertEquals(setOf("me", "m"), done.legs[1].memberIds)
        assertEquals(30.0, done.legs[1].distanceKm, 1e-9)
        assertTrue(out.any { it is EngineOutput.Alighted && it.member == seoyeon })
    }

    @Test fun `occupancy flicker while driving is ignored`() {
        val (s, out) = fourSeatTrip().apply(
            DrivingState(1, true),
            SeatOccupancy(2, SeatId.FRONT_PASSENGER, false),
            SeatOccupancy(3, SeatId.FRONT_PASSENGER, true),
            Odometer(4, 1010.0), DrivingState(5, false),
        )
        assertTrue(out.none { it is EngineOutput.Alighted })
        assertEquals(1, TripEngine.finish(s, 6).state.legs.size)
    }

    @Test fun `alighting while driving is applied when the car stops`() {
        val (s, out) = fourSeatTrip().apply(
            DrivingState(1, true), Odometer(2, 1010.0),
            SeatOccupancy(3, SeatId.REAR_LEFT, false),
            DrivingState(4, false),
        )
        assertTrue(s.onboard.none { it == SeatId.REAR_LEFT })
        assertTrue(out.any { it is EngineOutput.Alighted })
    }

    @Test fun `joining member needs belt when belt is supported`() {
        val start = TripEngine.start(0, mapOf(SeatId.DRIVER to me, SeatId.REAR_RIGHT to minsu), 0.0, null, beltSupported = true)
        val solo = start.copy(onboard = setOf(SeatId.DRIVER), occupied = setOf(SeatId.DRIVER), currentLeg = OpenLeg(0, 0.0, setOf(SeatId.DRIVER)))
        val (s1, _) = solo.apply(DrivingState(1, true), Odometer(2, 10.0), DrivingState(3, false), SeatOccupancy(4, SeatId.REAR_RIGHT, true))
        assertTrue(SeatId.REAR_RIGHT !in s1.onboard)
        val (s2, out) = s1.apply(SeatBelt(5, SeatId.REAR_RIGHT, true))
        assertTrue(SeatId.REAR_RIGHT in s2.onboard)
        assertTrue(out.any { it is EngineOutput.Boarded && it.member == minsu })
        assertEquals(setOf("me"), s2.legs.single().memberIds)
    }

    @Test fun `occupancy alone boards when belt is unsupported`() {
        val start = TripEngine.start(0, mapOf(SeatId.DRIVER to me, SeatId.REAR_RIGHT to minsu), 0.0, null, beltSupported = false)
        val solo = start.copy(onboard = setOf(SeatId.DRIVER), occupied = setOf(SeatId.DRIVER))
        val (s, _) = solo.apply(SeatOccupancy(1, SeatId.REAR_RIGHT, true))
        assertTrue(SeatId.REAR_RIGHT in s.onboard)
    }

    @Test fun `unbuckled occupied seat is boarded once driving starts`() {
        val start = TripEngine.start(0, mapOf(SeatId.DRIVER to me, SeatId.REAR_RIGHT to minsu), 0.0, null, beltSupported = true)
        val solo = start.copy(onboard = setOf(SeatId.DRIVER), occupied = setOf(SeatId.DRIVER))
        val (s, _) = solo.apply(SeatOccupancy(1, SeatId.REAR_RIGHT, true), DrivingState(2, true))
        assertTrue(SeatId.REAR_RIGHT in s.onboard)
    }

    @Test fun `unassigned occupied seat is flagged on the leg`() {
        val (s, _) = fourSeatTrip().apply(SeatOccupancy(1, SeatId.REAR_RIGHT, true), SeatBelt(2, SeatId.REAR_RIGHT, true), Odometer(3, 1005.0))
        val leg = TripEngine.finish(s, 4).state.legs.last()
        assertEquals(setOf(SeatId.REAR_RIGHT), leg.unassignedSeats)
        assertEquals(setOf("me", "m", "s"), leg.memberIds)
    }

    @Test fun `soc increase while charging is excluded from consumption`() {
        val (s, out) = fourSeatTrip().apply(
            DrivingState(1, true), Soc(2, 70.0), DrivingState(3, false),
            Charging(4, true), Soc(5, 90.0), Charging(6, false),
            DrivingState(7, true), Soc(8, 85.0),
        )
        val done = TripEngine.finish(s, 9).state
        assertEquals(15.0, done.legs.single().socConsumedPct, 1e-9)
        assertEquals(1, done.chargingStops.size)
        assertEquals(70.0, done.chargingStops[0].startSocPct!!, 1e-9)
        assertTrue(out.any { it is EngineOutput.ChargingRecorded })
    }

    @Test fun `soc rise while parked is detected as charging without explicit signal`() {
        val (s, _) = fourSeatTrip().apply(Soc(1, 85.0))
        assertTrue(s.charging)
        val done = TripEngine.finish(s, 2).state
        assertEquals(1, done.chargingStops.size)
    }

    @Test fun `odometer going backwards or duplicated is ignored`() {
        val (s, _) = fourSeatTrip().apply(Odometer(1, 1010.0), Odometer(2, 1005.0), Odometer(3, 1010.0), Odometer(4, 1012.0))
        assertEquals(1012.0, s.odometerKm, 1e-9)
    }

    @Test fun `efficiency is integrated over distance`() {
        val (s, _) = fourSeatTrip().apply(Efficiency(1, 5.0), Odometer(2, 1010.0), Efficiency(3, 4.0), Odometer(4, 1018.0))
        val leg = TripEngine.finish(s, 5).state.legs.single()
        assertEquals(10.0 / 5.0 + 8.0 / 4.0, leg.integratedKwh, 1e-9)
        assertEquals(18.0, leg.integratedKm, 1e-9)
    }

    @Test fun `soc flapping while driving nets out`() {
        val (s, _) = fourSeatTrip().apply(DrivingState(1, true), Soc(2, 79.0), Soc(3, 80.0), Soc(4, 79.0), Soc(5, 78.0))
        assertEquals(2.0, TripEngine.finish(s, 6).state.legs.single().socConsumedPct, 1e-9)
    }

    @Test fun `known seat state at start delays pickup and flags strangers`() {
        val start = TripEngine.start(
            0, mapOf(SeatId.DRIVER to me, SeatId.FRONT_PASSENGER to minsu, SeatId.REAR_LEFT to seoyeon),
            1000.0, 80.0, beltSupported = true, occupiedSeats = setOf(SeatId.FRONT_PASSENGER, SeatId.REAR_RIGHT),
        )
        assertEquals(setOf(SeatId.DRIVER, SeatId.FRONT_PASSENGER, SeatId.REAR_RIGHT), start.onboard)
        val (s, _) = start.apply(
            DrivingState(1, true), Odometer(2, 1010.0), DrivingState(3, false),
            SeatOccupancy(4, SeatId.REAR_LEFT, true), SeatBelt(5, SeatId.REAR_LEFT, true),
            DrivingState(6, true), Odometer(7, 1020.0),
        )
        val legs = TripEngine.finish(s, 8).state.legs
        assertEquals(setOf("me", "m"), legs[0].memberIds)
        assertEquals(setOf(SeatId.REAR_RIGHT), legs[0].unassignedSeats)
        assertEquals(setOf("me", "m", "s"), legs[1].memberIds)
    }

    @Test fun `non finite values are ignored`() {
        val (s, _) = fourSeatTrip().apply(Odometer(1, Double.NaN), Soc(2, Double.NaN))
        assertEquals(1000.0, s.odometerKm, 1e-9)
        assertEquals(80.0, s.socPct!!, 1e-9)
    }

    @Test fun `events after finish are ignored`() {
        val done = TripEngine.finish(fourSeatTrip(), 1).state
        val after = TripEngine.reduce(done, Odometer(2, 2000.0)).state
        assertEquals(done, after)
    }
}
