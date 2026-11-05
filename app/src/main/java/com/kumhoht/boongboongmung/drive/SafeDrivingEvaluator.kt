package com.kumhoht.boongboongmung.drive

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * 위험운전행동 판별 기준.
 *
 * ## 지금 기본값 = Pleos Connect 가상 콘솔(Virtual Console) 기준
 * 가상 콘솔 페달은 '누름/뗌'만 있어 가감속이 항상 같다 (2026-11 실측):
 * - ACCEL 누르는 동안: 약 +18 km/h/s (0 → 87km/h 5초)
 * - 페달 뗌(타력 주행): 약 -4 km/h/s
 * - BRAKE 누르는 동안: 약 -44 km/h/s
 * 그래서 초당 변화량 기준만으로는 부드러움/급함이 구분되지 않는다.
 * 판단 창을 2초로 넓혀 '2초 평균'으로 보고, 페달을 짧게 끊어 누르면 부드러운 운전,
 * 오래 누르면 급가속·급감속이 되도록 맞췄다.
 * - ACCEL 약 1.7초 이상 계속 누름 → 급가속 (1초 이하로 끊어 누르면 부드러움)
 * - BRAKE 약 0.8초 이상 계속 누름 → 급감속·급정지 (0.3초씩 끊어 누르거나 타력으로 줄이면 부드러움)
 *
 * ## 실제 차 기준 = [REAL_CAR] (각 값 옆 주석의 '실차: ...')
 * 국토교통부·한국교통안전공단 '위험운전행동 판별 기준(2022)' 중 승용차에 가장 가까운 택시 기준을 참고했다.
 * 실차 배포 전에는 [DriveEngine] 의 실주행 기준을 [REAL_CAR] 로 바꾼다.
 */
data class SafeDrivingCriteria(
    /** 급출발: 출발 직전 속도 이하에서 (실차: 5) */
    val startSpeedMaxKmh: Float = 5f,
    /** 급출발: 초당 가속 (km/h/s) (실차: 10) */
    val harshStartKmhPerSec: Float = 12f,
    /** 급가속: 속도 구간별 초당 가속 (km/h/s). 저속 <30, 중속 30~60, 고속 >=60 (실차: 12 / 10 / 8) */
    val harshAccelLow: Float = 14f,
    val harshAccelMid: Float = 14f,
    val harshAccelHigh: Float = 14f,
    /** 급감속: 초당 감속 (km/h/s). 저·중속 <60 / 고속 >=60 (실차: 15 / 14) */
    val harshDecelLow: Float = 20f,
    val harshDecelHigh: Float = 20f,
    /** 급정지: 감속 후 속도가 이 값 이하이면 급정지로 분류 (실차: 5) */
    val stopSpeedMaxKmh: Float = 5f,
    /** 급정지: 초당 감속 (km/h/s) (실차: 14) */
    val harshStopKmhPerSec: Float = 20f,
    /** 판단 창 (초당 변화량을 이 시간 간격의 평균으로 계산) (실차: 1초) */
    val windowMs: Long = 2_000L,
    /** 같은 종류 이벤트 중복 방지 */
    val cooldownMs: Long = 3_000L,
    /** 샘플 간격이 이보다 길면 끊긴 것으로 보고 적분 시간을 자른다 */
    val maxGapMs: Long = 5_000L,
    /** 부드러운 운전 구간 길이 */
    val segmentKm: Double = 1.0,
    /** 제한속도 허용 오차 (km/h). 기획: '제한속도 이하' → 0 */
    val speedLimitToleranceKmh: Float = 0f,
) {
    companion object {
        /** Pleos Connect 가상 콘솔용 (현재 실주행 기준) */
        val VIRTUAL_CONSOLE = SafeDrivingCriteria()

        /** 실제 차 기준 (위험운전행동 판별 기준 2022, 택시). 주행 로그 재생 모드도 이 값을 쓴다. */
        val REAL_CAR = SafeDrivingCriteria(
            startSpeedMaxKmh = 5f,
            harshStartKmhPerSec = 10f,
            harshAccelLow = 12f,
            harshAccelMid = 10f,
            harshAccelHigh = 8f,
            harshDecelLow = 15f,
            harshDecelHigh = 14f,
            stopSpeedMaxKmh = 5f,
            harshStopKmhPerSec = 14f,
            windowMs = 1_000L,
        )
    }
}

enum class HarshType { START, ACCEL, DECEL, STOP }

data class HarshEvent(val type: HarshType, val atMs: Long, val speedKmh: Float, val deltaKmhPerSec: Float)

/** 운행 한 번의 결과 요약. 포인트 정산의 입력. */
data class TripSummary(
    val startMs: Long,
    val endMs: Long,
    val drivingMs: Long,
    val distanceKm: Double,
    /** 급가속·급감속 0회로 완주한 1km 구간 수 */
    val smoothKm: Int,
    /** 판정이 끝난 1km 구간 수 */
    val judgedKm: Int,
    val harshAccelCount: Int,
    val harshDecelCount: Int,
    /** 제한속도 정보가 있었던 거리 */
    val limitCoveredKm: Double,
    /** 그 중 제한속도 이하로 달린 거리 */
    val limitCompliantKm: Double,
    val arrived: Boolean,
    val maxSpeedKmh: Float,
    /**
     * 안전벨트를 매고 달렸는지. 달리는 동안(5km/h 초과) 안 맨 시간이 거의 없으면 true.
     * 안전벨트 신호가 한 번도 없었으면 null (결과에 표시하지 않음)
     */
    val beltBuckled: Boolean? = null,
) {
    /** 부드러운 운전 비율 0~1. 판정 구간이 없으면 null */
    val smoothRatio: Double? get() = if (judgedKm > 0) smoothKm.toDouble() / judgedKm else null

    /** 규정 속도 준수 비율 0~1. 제한속도 정보가 1km 미만이면 null (내비 미연동) */
    val speedLimitRatio: Double? get() = if (limitCoveredKm >= 1.0) limitCompliantKm / limitCoveredKm else null
}

/**
 * 운행 1회 동안 샘플을 받아 주행거리·급가감속·부드러운 구간·규정 속도 준수를 집계한다. 순수 로직.
 *
 * 주행거리: 기본은 차속 × 시간 적분. 누적 주행거리(Odometer) 값이 오면 그 증가량을 우선한다.
 */
class SafeDrivingEvaluator(
    val startMs: Long,
    private val c: SafeDrivingCriteria = SafeDrivingCriteria(),
) {
    private var lastMs = startMs
    private var lastSpeed: Float? = null
    private var drivingMs = 0L

    private var integratedKm = 0.0
    private var odoStart: Float? = null
    private var odoLast: Float? = null

    // 1초 창 계산용 최근 샘플 (시각, 속도)
    private val window = ArrayDeque<Pair<Long, Float>>()
    private val lastEventAt = HashMap<HarshType, Long>()

    // 1km 구간
    private var segmentKm = 0.0
    private var segmentEvents = 0
    private var smoothKm = 0
    private var judgedKm = 0

    private var accelCount = 0
    private var decelCount = 0
    private val events = mutableListOf<HarshEvent>()

    private var limitCoveredKm = 0.0
    private var limitCompliantKm = 0.0
    private var arrived = false
    private var maxSpeed = 0f

    // 안전벨트: 신호를 받은 주행 시간 / 그중 안 맨 채 달린 시간
    private var beltKnownMs = 0L
    private var unbuckledMovingMs = 0L

    val harshEvents: List<HarshEvent> get() = events

    /** 지금까지 달린 거리 (화면·로그용) */
    val distanceKm: Double get() = currentDistance()

    fun markArrived() { arrived = true }

    fun onSample(s: VehicleSample) {
        val speed = s.speedKmh
        val dtMs = (s.timeMs - lastMs).coerceAtLeast(0L)
        val usableDt = min(dtMs, c.maxGapMs)

        // 1) 거리 적분 (사다리꼴)
        var dKm = 0.0
        if (speed != null) {
            val prev = lastSpeed ?: speed
            val avg = (prev + speed) / 2.0
            dKm = avg * usableDt / 3_600_000.0
            integratedKm += dKm
            if (speed > maxSpeed) maxSpeed = speed
        }
        if (s.gear != GearState.PARK) drivingMs += usableDt

        s.odometerKm?.let { odo ->
            if (odoStart == null) odoStart = odo
            odoLast = odo
        }

        // 2) 규정 속도 준수 (거리 가중)
        val limit = s.speedLimitKmh
        if (limit != null && limit > 0 && speed != null && dKm > 0) {
            limitCoveredKm += dKm
            if (speed <= limit + c.speedLimitToleranceKmh) limitCompliantKm += dKm
        }

        // 3) 급가속·급감속
        if (speed != null) detectHarsh(s.timeMs, speed)

        // 3-1) 안전벨트: 움직이는 동안(5km/h 초과) 안 맨 시간만 센다 (주차·정차 중 풀었다 매는 건 괜찮음)
        s.beltUnbuckled?.let { unbuckled ->
            if (s.gear != GearState.PARK) beltKnownMs += usableDt
            if (unbuckled && (speed ?: 0f) > c.stopSpeedMaxKmh) unbuckledMovingMs += usableDt
        }

        // 4) 1km 구간 판정
        segmentKm += dKm
        while (segmentKm >= c.segmentKm - 1e-9) {
            segmentKm = (segmentKm - c.segmentKm).coerceAtLeast(0.0)
            judgedKm++
            if (segmentEvents == 0) smoothKm++
            segmentEvents = 0
        }

        lastMs = s.timeMs
        if (speed != null) lastSpeed = speed
    }

    private fun detectHarsh(now: Long, speed: Float) {
        window.addLast(now to speed)
        // 창보다 오래된 샘플 정리 (기준 샘플 1개는 남긴다)
        while (window.size > 2 && now - window[1].first >= c.windowMs) window.removeFirst()
        val (t0, v0) = window.first()
        val dt = now - t0
        // 창이 3/4 이상 찼을 때만 판단 (운행 시작 직후 짧은 구간으로 과대평가하지 않도록)
        if (dt < c.windowMs * 3 / 4 || dt > c.windowMs * 2) return
        val perSec = (speed - v0) * 1000f / dt

        val type: HarshType? = if (perSec > 0) {
            if (v0 <= c.startSpeedMaxKmh) {
                if (perSec >= c.harshStartKmhPerSec) HarshType.START else null
            } else {
                val th = when {
                    v0 < 30f -> c.harshAccelLow
                    v0 < 60f -> c.harshAccelMid
                    else -> c.harshAccelHigh
                }
                if (perSec >= th) HarshType.ACCEL else null
            }
        } else {
            val decel = -perSec
            if (speed <= c.stopSpeedMaxKmh) {
                if (decel >= c.harshStopKmhPerSec) HarshType.STOP else null
            } else {
                val th = if (v0 >= 60f) c.harshDecelHigh else c.harshDecelLow
                if (decel >= th) HarshType.DECEL else null
            }
        }
        if (type == null) return

        // 같은 방향(가속 계열/감속 계열) 쿨다운
        val family = if (type == HarshType.START || type == HarshType.ACCEL) HarshType.ACCEL else HarshType.DECEL
        val last = lastEventAt[family]
        if (last != null && now - last < c.cooldownMs) return
        lastEventAt[family] = now

        events += HarshEvent(type, now, speed, perSec)
        if (family == HarshType.ACCEL) accelCount++ else decelCount++
        segmentEvents++
    }

    private fun currentDistance(): Double {
        val a = odoStart
        val b = odoLast
        val odoKm = if (a != null && b != null) (b - a).toDouble() else 0.0
        // 오도미터가 실제로 움직였으면 그 값을 우선 (실차). 에뮬레이터는 0 이라 적분값 사용.
        return if (odoKm > 0.05) odoKm else integratedKm
    }

    fun finish(endMs: Long): TripSummary {
        val dist = currentDistance()
        // 판정은 완주한 1km 구간만. (자투리 구간은 다음 운행으로 넘기지 않는다)
        val smooth = smoothKm
        val judged = judgedKm
        return TripSummary(
            startMs = startMs,
            endMs = max(endMs, startMs),
            drivingMs = drivingMs,
            distanceKm = dist,
            smoothKm = smooth,
            judgedKm = judged,
            harshAccelCount = accelCount,
            harshDecelCount = decelCount,
            limitCoveredKm = limitCoveredKm,
            limitCompliantKm = limitCompliantKm,
            arrived = arrived,
            maxSpeedKmh = maxSpeed,
            beltBuckled = if (beltKnownMs > 0) unbuckledMovingMs <= BELT_GRACE_MS else null,
        )
    }

    companion object {
        /** 출발 직후 신호가 늦게 오는 경우 등을 봐주는 여유 (안 맨 채 달린 시간이 이 이하면 착용으로 본다) */
        const val BELT_GRACE_MS = 3_000L

        fun floorKm(km: Double): Int = floor(km + 1e-9).toInt()
    }
}
