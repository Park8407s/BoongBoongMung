package com.kumhoht.boongboongmung

import com.kumhoht.boongboongmung.drive.DrivePhase
import com.kumhoht.boongboongmung.drive.DriveStateMachine
import com.kumhoht.boongboongmung.drive.DriveTransition
import com.kumhoht.boongboongmung.drive.GearState
import com.kumhoht.boongboongmung.drive.HarshType
import com.kumhoht.boongboongmung.drive.SafeDrivingCriteria
import com.kumhoht.boongboongmung.drive.SafeDrivingEvaluator
import com.kumhoht.boongboongmung.drive.VehicleSample
import com.kumhoht.boongboongmung.replay.ReplayEvent
import com.kumhoht.boongboongmung.replay.ReplayScenarios
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveLogicTest {

    private fun s(t: Long, gear: GearState, v: Float, limit: Int? = null) =
        VehicleSample(timeMs = t, gear = gear, speedKmh = v, speedLimitKmh = limit)

    @Test
    fun stateMachine_startsOnDrive_endsOnParkAndStop() {
        val sm = DriveStateMachine()
        assertNull(sm.onSample(s(0, GearState.PARK, 0f)))
        assertTrue(sm.onSample(s(1000, GearState.DRIVE, 0f)) is DriveTransition.Started)
        assertEquals(DrivePhase.DRIVING, sm.phase)
        assertNull(sm.onSample(s(2000, GearState.DRIVE, 30f)))
        // 기어 P 라도 차가 움직이고 있으면 종료 아님
        assertNull(sm.onSample(s(3000, GearState.PARK, 5f)))
        assertTrue(sm.onSample(s(4000, GearState.PARK, 0f)) is DriveTransition.Ended)
        assertEquals(DrivePhase.PARKED, sm.phase)
    }

    @Test
    fun stateMachine_unknownGear_usesRestriction() {
        val sm = DriveStateMachine()
        assertTrue(sm.onSample(VehicleSample(0, GearState.UNKNOWN, 20f, restricted = true)) is DriveTransition.Started)
        assertNull(sm.onSample(VehicleSample(1000, GearState.UNKNOWN, 0f, restricted = false)))
        assertTrue(sm.onSample(VehicleSample(12_000, GearState.UNKNOWN, 0f, restricted = false)) is DriveTransition.Ended)
    }

    @Test
    fun evaluator_integratesDistance() {
        val ev = SafeDrivingEvaluator(0)
        // 60km/h 로 1시간 = 60km (0.5초 간격)
        var t = 0L
        ev.onSample(s(t, GearState.DRIVE, 60f))
        while (t < 3_600_000L) { t += 500; ev.onSample(s(t, GearState.DRIVE, 60f)) }
        val r = ev.finish(t)
        assertEquals(60.0, r.distanceKm, 0.01)
        assertEquals(60, r.smoothKm)
        assertEquals(0, r.harshAccelCount)
    }

    @Test
    fun evaluator_detectsHarshStartAndStop() {
        val ev = SafeDrivingEvaluator(0, SafeDrivingCriteria.REAL_CAR)
        var t = 0L
        ev.onSample(s(t, GearState.DRIVE, 0f))
        // 1초 만에 0 → 15km/h : 급출발
        t += 500; ev.onSample(s(t, GearState.DRIVE, 8f))
        t += 500; ev.onSample(s(t, GearState.DRIVE, 15f))
        repeat(20) { t += 500; ev.onSample(s(t, GearState.DRIVE, 15f)) }
        // 1초 만에 15 → 0 : 급정지
        t += 500; ev.onSample(s(t, GearState.DRIVE, 7f))
        t += 500; ev.onSample(s(t, GearState.DRIVE, 0f))
        val types = ev.harshEvents.map { it.type }
        assertTrue(types.toString(), HarshType.START in types)
        assertTrue(types.toString(), HarshType.STOP in types)
        val r = ev.finish(t)
        assertEquals(1, r.harshAccelCount)
        assertEquals(1, r.harshDecelCount)
    }

    @Test
    fun evaluator_gentleDriving_noEvents() {
        val ev = SafeDrivingEvaluator(0)
        var t = 0L
        var v = 0f
        ev.onSample(s(t, GearState.DRIVE, v))
        // 초당 5km/h 로 천천히 가속 → 80km/h
        while (v < 80f) { t += 500; v += 2.5f; ev.onSample(s(t, GearState.DRIVE, v)) }
        while (v > 0f) { t += 500; v = (v - 3f).coerceAtLeast(0f); ev.onSample(s(t, GearState.DRIVE, v)) }
        assertTrue(ev.harshEvents.toString(), ev.harshEvents.isEmpty())
    }

    @Test
    fun evaluator_speedLimitRatio() {
        val ev = SafeDrivingEvaluator(0)
        var t = 0L
        ev.onSample(s(t, GearState.DRIVE, 50f, 60))
        // 50km/h 로 9분(준수), 70km/h 로 1분(초과)
        while (t < 540_000L) { t += 500; ev.onSample(s(t, GearState.DRIVE, 50f, 60)) }
        // 50 → 70 이 한 샘플에 튀면 급가속이 되므로 천천히 올린다
        var v = 50f
        while (v < 70f) { t += 500; v += 2f; ev.onSample(s(t, GearState.DRIVE, v, 60)) }
        val end = t + 60_000L
        while (t < end) { t += 500; ev.onSample(s(t, GearState.DRIVE, 70f, 60)) }
        val r = ev.finish(t)
        val ratio = r.speedLimitRatio
        assertNotNull(ratio)
        assertTrue("ratio=$ratio", ratio!! in 0.80..0.90)
    }

    // ---- 가상 콘솔 기준: 페달은 누름/뗌만 있고 ACCEL +18, 타력 -4, BRAKE -44 km/h/s (실측)

    /** 0.5초 간격 샘플로 가상 콘솔 페달 조작을 흉내 낸다. pedal: +1 가속, 0 타력, -1 브레이크 */
    private class Console(val ev: SafeDrivingEvaluator, var v: Float) {
        var t = 0L
        init { ev.onSample(VehicleSample(t, GearState.DRIVE, v)) }
        fun hold(pedal: Int, ms: Long) {
            var left = ms
            while (left > 0) {
                val step = minOf(500L, left)
                val rate = when (pedal) { 1 -> 18f; -1 -> -44f; else -> -4f }
                v = (v + rate * step / 1000f).coerceAtLeast(0f)
                t += step; left -= step
                ev.onSample(VehicleSample(t, GearState.DRIVE, v))
            }
        }
    }

    @Test
    fun console_holdingAccel_isHarsh_tappingIsSmooth() {
        // 50km/h 에서 1초씩 끊어 누르고 타력 → 급가속 없음
        val tap = Console(SafeDrivingEvaluator(0, SafeDrivingCriteria.VIRTUAL_CONSOLE), 50f)
        repeat(5) { tap.hold(1, 1000); tap.hold(0, 4000) }
        assertEquals(tap.ev.harshEvents.toString(), 0, tap.ev.finish(tap.t).harshAccelCount)

        // 50km/h 에서 2초 계속 누름 → 급가속
        val hold = Console(SafeDrivingEvaluator(0, SafeDrivingCriteria.VIRTUAL_CONSOLE), 50f)
        hold.hold(0, 3000); hold.hold(1, 2000); hold.hold(0, 3000)
        assertEquals(hold.ev.harshEvents.toString(), 1, hold.ev.finish(hold.t).harshAccelCount)
    }

    @Test
    fun console_holdingBrake_isHarsh_coastingIsSmooth() {
        // 타력으로만 60 → 0 → 급감속 없음
        val coast = Console(SafeDrivingEvaluator(0, SafeDrivingCriteria.VIRTUAL_CONSOLE), 60f)
        coast.hold(0, 16_000)
        assertEquals(coast.ev.harshEvents.toString(), 0, coast.ev.finish(coast.t).harshDecelCount)

        // 60km/h 에서 BRAKE 1.5초 계속 → 급감속(급정지)
        val brake = Console(SafeDrivingEvaluator(0, SafeDrivingCriteria.VIRTUAL_CONSOLE), 60f)
        brake.hold(0, 3000); brake.hold(-1, 1500); brake.hold(0, 2000)
        assertEquals(brake.ev.harshEvents.toString(), 1, brake.ev.finish(brake.t).harshDecelCount)
    }

    @Test
    fun evaluator_seatBelt() {
        fun run(belt: (Long) -> Boolean?): Boolean? {
            val ev = SafeDrivingEvaluator(0)
            var t = 0L
            // 정차 10초 → 50km/h 로 60초
            while (t < 70_000L) {
                val v = if (t < 10_000L) 0f else 50f
                ev.onSample(VehicleSample(t, GearState.DRIVE, v, beltUnbuckled = belt(t)))
                t += 500
            }
            return ev.finish(t).beltBuckled
        }
        // 계속 맴 → true
        assertEquals(true, run { false })
        // 정차 중에만 안 맴 → true (출발 전에 맸으면 괜찮다)
        assertEquals(true, run { t -> t < 9_000L })
        // 달리는 중에 10초 안 맴 → false
        assertEquals(false, run { t -> t in 20_000L..30_000L })
        // 신호 없음 → null
        assertEquals(null, run { null })
    }

    @Test
    fun replayScenarios_produceExpectedTrips() {
        for (sc in ReplayScenarios.all) {
            val sm = DriveStateMachine()
            var ev: SafeDrivingEvaluator? = null
            var trips = 0
            for (frame in sc.frames()) {
                val sample = frame.sample
                when (val tr = sm.onSample(sample)) {
                    is DriveTransition.Started -> ev = SafeDrivingEvaluator(tr.atMs, SafeDrivingCriteria.REAL_CAR)
                    is DriveTransition.Ended -> {
                        val r = ev!!.finish(tr.atMs)
                        trips++
                        println("${sc.title}: ${"%.1f".format(r.distanceKm)}km smooth=${r.smoothKm}/${r.judgedKm} " +
                            "accel=${r.harshAccelCount} decel=${r.harshDecelCount} limit=${r.speedLimitRatio} belt=${r.beltBuckled}")
                        // 초보 운전만 안전벨트를 늦게 맨다
                        assertEquals(sc.title, sc.id != 3, r.beltBuckled)
                        ev = null
                    }
                    null -> ev?.onSample(sample)
                }
                if (frame.event == ReplayEvent.ARRIVED) ev?.markArrived()
            }
            assertTrue("${sc.title} trips=$trips", trips >= 1)
        }
    }
}
