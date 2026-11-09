package com.kumhoht.boongboongnyang.replay

import com.kumhoht.boongboongnyang.drive.GearState
import com.kumhoht.boongboongnyang.drive.VehicleSample

/** 재생 중 내비 이벤트 (에뮬레이터에서는 NaviHelper 이벤트가 오지 않아 재생으로 시연한다) */
enum class ReplayEvent { ROUTE_STARTED, ARRIVED }

data class ReplayFrame(val sample: VehicleSample, val event: ReplayEvent? = null)

/**
 * '주행 로그 재생 모드'용 시나리오.
 * 에뮬레이터는 차가 실제로 움직이지 않아 내비 제한속도·목적지 도착 이벤트가 오지 않으므로,
 * 저장된 주행 데이터(여기서는 코드로 생성한 로그)를 같은 파이프라인에 그대로 흘려보낸다.
 * 시각은 0 부터 시작하는 상대 시각(ms). 재생할 때 실제 시각으로 옮긴다.
 */
class ReplayScenario(
    val id: Int,
    val title: String,
    val description: String,
    private val build: LogBuilder.() -> Unit,
) {
    fun frames(): List<ReplayFrame> = LogBuilder().apply(build).frames

    /** 테스트 편의: 샘플만 */
    fun samples(): List<VehicleSample> = frames().map { it.sample }

    val durationMs: Long get() = frames().lastOrNull()?.sample?.timeMs ?: 0L
}

/** 0.5초 간격 주행 로그 생성기 */
class LogBuilder(private val tickMs: Long = 500L) {
    val frames = mutableListOf<ReplayFrame>()
    private var t = 0L
    private var v = 0f
    private var gear = GearState.PARK
    private var limit: Int? = null
    private var pending: ReplayEvent? = null
    /** 운전석 안전벨트 안 맴 여부 (기본: 맴) */
    private var unbuckled = false

    private fun tick() {
        frames += ReplayFrame(
            VehicleSample(timeMs = t, gear = gear, speedKmh = v, speedLimitKmh = limit,
                restricted = gear != GearState.PARK, beltUnbuckled = unbuckled),
            pending,
        )
        pending = null
        t += tickMs
    }

    fun park(seconds: Int) { gear = GearState.PARK; v = 0f; repeat(seconds * 2) { tick() } }
    fun drive() { gear = GearState.DRIVE; tick() }
    fun limit(kmh: Int?) { limit = kmh }
    fun route() { pending = ReplayEvent.ROUTE_STARTED }
    fun arrive() { pending = ReplayEvent.ARRIVED; tick() }
    /** 안전벨트 매기/풀기 */
    fun belt(buckled: Boolean) { unbuckled = !buckled }

    /** 초당 [rate] km/h 로 [to] 까지 속도 변경 (부드럽게: 2~5, 급하게: 12 이상) */
    fun speedTo(to: Float, rate: Float = 3f) {
        val step = rate * tickMs / 1000f
        while (v != to) {
            v = if (v < to) (v + step).coerceAtMost(to) else (v - step).coerceAtLeast(to)
            tick()
        }
    }

    /** 현재 속도로 [km] 만큼 달리기 */
    fun cruiseKm(km: Double) {
        var d = 0.0
        if (v <= 0f) return
        while (d < km) { d += v * tickMs / 3_600_000.0; tick() }
    }

    fun cruiseSec(seconds: Int) = repeat(seconds * 2) { tick() }

    /** 신호 대기: 부드럽게 멈췄다가 다시 출발 */
    fun trafficLight(waitSec: Int = 20, resume: Float = 50f) {
        speedTo(0f, 4f); cruiseSec(waitSec); speedTo(resume, 3f)
    }
}

object ReplayScenarios {

    /** 시나리오 1. 출근길 기본 순환: 18km, 부드러운 운전 15km, 규정 속도 94%, 목적지 도착 */
    val commute = ReplayScenario(1, "출근길 18km", "직장인 출근길 · 내비 경로 안내 · 급가속 3번") {
        park(5); route(); drive()
        limit(50); speedTo(45f)
        repeat(3) { cruiseKm(1.0); trafficLight(resume = 45f) }          // 시내 3km
        speedTo(60f, 15f); speedTo(45f, 3f); cruiseKm(1.2)                 // 급가속 1
        limit(80); speedTo(75f, 3f); cruiseKm(4.0)
        speedTo(85f, 2f); cruiseKm(0.75); speedTo(75f, 2f)                  // 제한속도 초과 잠깐
        cruiseKm(2.0)
        speedTo(50f, 3f); limit(50); cruiseKm(1.0)
        speedTo(0f, 3f); cruiseSec(5); speedTo(30f, 14f); speedTo(45f, 3f) // 급출발(급가속 2)
        cruiseKm(2.0)
        speedTo(65f, 12f); speedTo(45f, 3f); cruiseKm(1.0)                 // 급가속 3
        cruiseKm(1.5)
        speedTo(0f, 4f); arrive(); park(10)
    }

    /** 시나리오 2. 주말 가족 나들이: 60km, 제한속도 준수, 목적지 도착 → 선물상자 2개 */
    val family = ReplayScenario(2, "가족 나들이 60km", "주말 나들이 · 내비 경로 안내 · 제한속도 준수") {
        park(5); route(); drive()
        limit(60); speedTo(55f, 3f); cruiseKm(4.0); trafficLight(resume = 55f); cruiseKm(4.0)
        limit(100); speedTo(95f, 3f); cruiseKm(45.0)
        speedTo(60f, 3f); limit(60); cruiseKm(6.5)
        speedTo(0f, 3f); arrive(); park(10)
    }

    /** 시나리오 3. 초보 운전자: 급정지가 잦은 날 + 안전벨트를 늦게 맴 → 안전벨트 보너스 없음 (감점 없이 다음 목표로 안내) */
    val beginner = ReplayScenario(3, "초보 운전", "급출발·급정지가 잦은 날 · 안전벨트 늦게 맴") {
        belt(false)                             // 벨트 안 맨 채 출발
        park(5); drive()
        limit(50)
        repeat(5) { i ->
            speedTo(40f, 12f); cruiseKm(0.9)    // 급출발
            speedTo(0f, 16f); cruiseSec(10)     // 급정지
            if (i == 0) belt(true)              // 첫 정차 때 벨트를 맴
        }
        speedTo(40f, 3f); cruiseKm(4.0); speedTo(0f, 3f); park(10)
    }

    /** 시나리오 4. 장거리 운전과 휴식: 2시간 이상 연속 운행 → 휴게소 15분 휴식 → 휴식 보너스 */
    val longDrive = ReplayScenario(4, "장거리 + 휴게소 휴식", "고속도로 2시간 10분 → 16분 휴식 → 다시 출발") {
        park(5); route(); drive()
        limit(110); speedTo(100f, 3f)
        cruiseSec(130 * 60)                     // 2시간 10분
        speedTo(30f, 3f); limit(30); cruiseKm(0.3); speedTo(0f, 3f)
        park(16 * 60)                           // 휴게소 16분
        drive(); speedTo(90f, 3f); limit(110); cruiseKm(10.0)
        speedTo(0f, 3f); arrive(); park(10)
    }

    val all = listOf(commute, family, beginner, longDrive)
    fun byId(id: Int) = all.firstOrNull { it.id == id }
}
