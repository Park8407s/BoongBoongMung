package com.kumhoht.boongboongmung.reward

import com.kumhoht.boongboongmung.drive.SafeDrivingEvaluator
import com.kumhoht.boongboongmung.drive.TripSummary
import kotlin.math.floor
import kotlin.math.min

/** 포인트 적립 기준 (기획서 '기능 ①' 표). 값은 예시이며 테스트 후 조정한다. */
object PointRules {
    const val BASE_PER_KM = 1
    const val SMOOTH_PER_KM = 1
    const val SPEED_LIMIT_BONUS = 10
    const val SPEED_LIMIT_RATIO = 0.90
    const val ARRIVAL_BONUS = 10
    /** 안전벨트를 매고 운행 (운전석, 움직이는 동안 계속 착용) */
    const val SEATBELT_BONUS = 10
    const val REST_BONUS = 20
    const val REST_AFTER_MS = 2 * 60 * 60 * 1000L     // 2시간 연속 운행 후
    const val REST_MIN_MS = 15 * 60 * 1000L           // 15분 이상 휴식
    const val STREAK_PER_DAY = 5
    const val STREAK_MAX = 30
    const val GIFT_BOX_EVERY_SAFE_KM = 30
    /** 이 비율 이상이면 '안전운전한 날'로 연속 일수에 포함 */
    const val SAFE_DAY_SMOOTH_RATIO = 0.70
    /** 이보다 짧은 운행은 결과를 만들지 않는다 (기어만 D→P 바꾼 경우 등) */
    const val MIN_TRIP_KM = 0.05
}

/** 레벨: 누적 안전운전 거리(부드러운 구간 km) 기준. */
object Levels {
    private val thresholds = intArrayOf(0, 5, 15, 30, 50, 80, 120, 170, 230, 300)

    fun levelFor(safeKm: Int): Int {
        if (safeKm >= thresholds.last()) return thresholds.size + (safeKm - thresholds.last()) / 100
        return thresholds.indexOfLast { safeKm >= it } + 1
    }

    /** 다음 레벨에 필요한 누적 안전운전 거리 */
    fun nextThreshold(level: Int): Int =
        if (level < thresholds.size) thresholds[level] else thresholds.last() + (level - thresholds.size + 1) * 100

    fun currentThreshold(level: Int): Int =
        if (level <= thresholds.size) thresholds[level - 1] else thresholds.last() + (level - thresholds.size) * 100

    /** 레벨 칭호 */
    fun titleFor(level: Int): String = when {
        level >= 10 -> "전설의 드라이버"
        level >= 8 -> "붕붕이 베프"
        level >= 5 -> "든든한 보호자"
        level >= 3 -> "산책 친구"
        else -> "새내기 드라이버"
    }
}

/** 정산 결과 한 줄 (결과 화면 '오늘의 드라이브' 목록) */
data class RewardLine(val label: String, val points: Int)

/** 운행 사이에 이어지는 진행 상태 (DB kv 에 저장) */
data class ProgressState(
    val totalSafeKm: Int = 0,
    val streakDays: Int = 0,
    /** 마지막으로 '안전운전한 날'의 날짜 키 (yyyymmdd as Int, 로컬 날짜). 0 = 없음 */
    val lastSafeDay: Int = 0,
    /** 마지막으로 연속 보너스를 준 날짜 키 */
    val lastStreakBonusDay: Int = 0,
    /** 휴식 없이 이어진 운행 시간 */
    val continuousDriveMs: Long = 0,
    val lastTripEndMs: Long = 0,
)

data class Settlement(
    val lines: List<RewardLine>,
    val totalPoints: Int,
    val giftBoxes: Int,
    val levelBefore: Int,
    val levelAfter: Int,
    val newState: ProgressState,
    val restBonus: Boolean,
) {
    val leveledUp: Boolean get() = levelAfter > levelBefore
}

/**
 * 운행 결과 → 포인트·선물상자·레벨 정산. 순수 로직.
 * 모든 보상은 '잘한 운전'에만 주고 감점은 없다.
 */
object RewardCalculator {

    /** 운행 시작 시점에 휴식 보너스 대상인지 (2시간 이상 연속 운행 후 15분 이상 쉬었는지) */
    fun restQualifies(state: ProgressState, tripStartMs: Long): Boolean {
        if (state.lastTripEndMs == 0L) return false
        val gap = tripStartMs - state.lastTripEndMs
        return gap >= PointRules.REST_MIN_MS && state.continuousDriveMs >= PointRules.REST_AFTER_MS
    }

    /**
     * @param dayKey     운행 종료 시각의 로컬 날짜 키 (yyyymmdd)
     * @param prevDayKey dayKey 의 하루 전 날짜 키
     */
    fun settle(
        trip: TripSummary,
        state: ProgressState,
        dayKey: Int,
        prevDayKey: Int,
    ): Settlement {
        val lines = mutableListOf<RewardLine>()
        val km = SafeDrivingEvaluator.floorKm(trip.distanceKm)

        // 기본 주행
        if (km > 0) lines += RewardLine("주행 ${km}km", km * PointRules.BASE_PER_KM)
        // 부드러운 운전
        if (trip.smoothKm > 0) lines += RewardLine("부드러운 운전 ${trip.smoothKm}km", trip.smoothKm * PointRules.SMOOTH_PER_KM)
        // 규정 속도 준수
        trip.speedLimitRatio?.let { r ->
            if (r >= PointRules.SPEED_LIMIT_RATIO) {
                lines += RewardLine("규정 속도 준수 ${(r * 100).toInt()}%", PointRules.SPEED_LIMIT_BONUS)
            }
        }
        // 목적지 도착
        if (trip.arrived) lines += RewardLine("목적지 도착", PointRules.ARRIVAL_BONUS)
        // 안전벨트 착용 (신호가 없던 운행은 표시하지 않음)
        if (trip.beltBuckled == true) lines += RewardLine("안전벨트 착용", PointRules.SEATBELT_BONUS)

        // 휴식 보너스 (이번 운행 시작 전에 충분히 쉬었는지)
        val rest = restQualifies(state, trip.startMs)
        if (rest) lines += RewardLine("휴식 보너스", PointRules.REST_BONUS)

        // 연속 안전운전 일수
        val safeDay = (trip.smoothRatio ?: 0.0) >= PointRules.SAFE_DAY_SMOOTH_RATIO && trip.judgedKm >= 1
        var streak = state.streakDays
        var lastSafeDay = state.lastSafeDay
        var lastBonusDay = state.lastStreakBonusDay
        if (safeDay && lastSafeDay != dayKey) {
            streak = if (lastSafeDay == prevDayKey) streak + 1 else 1
            lastSafeDay = dayKey
        }
        if (safeDay && lastBonusDay != dayKey) {
            val bonus = min(streak * PointRules.STREAK_PER_DAY, PointRules.STREAK_MAX)
            lines += RewardLine("연속 안전운전 ${streak}일째", bonus)
            lastBonusDay = dayKey
        }

        // 선물상자: 누적 안전운전 거리 30km 마다 1개
        val safeBefore = state.totalSafeKm
        val safeAfter = safeBefore + trip.smoothKm
        val boxes = safeAfter / PointRules.GIFT_BOX_EVERY_SAFE_KM - safeBefore / PointRules.GIFT_BOX_EVERY_SAFE_KM

        // 연속 운행 시간 (휴식했으면 리셋)
        val gap = if (state.lastTripEndMs == 0L) Long.MAX_VALUE else trip.startMs - state.lastTripEndMs
        val continuous = (if (gap >= PointRules.REST_MIN_MS) 0L else state.continuousDriveMs) + trip.drivingMs

        val newState = state.copy(
            totalSafeKm = safeAfter,
            streakDays = streak,
            lastSafeDay = lastSafeDay,
            lastStreakBonusDay = lastBonusDay,
            continuousDriveMs = continuous,
            lastTripEndMs = trip.endMs,
        )
        return Settlement(
            lines = lines,
            totalPoints = lines.sumOf { it.points },
            giftBoxes = boxes,
            levelBefore = Levels.levelFor(safeBefore),
            levelAfter = Levels.levelFor(safeAfter),
            newState = newState,
            restBonus = rest,
        )
    }

    /** 다음 선물상자까지 남은 안전운전 거리 */
    fun kmToNextBox(totalSafeKm: Int): Int =
        PointRules.GIFT_BOX_EVERY_SAFE_KM - totalSafeKm % PointRules.GIFT_BOX_EVERY_SAFE_KM

    /** 다음 레벨까지 남은 안전운전 거리 */
    fun kmToNextLevel(totalSafeKm: Int): Int =
        Levels.nextThreshold(Levels.levelFor(totalSafeKm)) - totalSafeKm

    /** 결과 화면 피드백 문구 (감점 없이, 아쉬운 점은 다음 목표로) */
    fun feedback(trip: TripSummary): String {
        val ratio = trip.smoothRatio
        return when {
            trip.arrived -> "목적지까지 안전하게 도착했어요!"
            ratio == null -> "오늘도 안전하게 도착했어요!"
            ratio >= 0.9 -> "정말 부드러운 운전이었어요. 붕붕이가 푹 잤어요!"
            ratio >= 0.7 -> "오늘도 안전하게 도착했어요!"
            else -> "오늘은 부드러운 운전 구간이 ${(ratio * 100).toInt()}%였어요. " +
                "다음엔 붕붕이가 편하게 탈 수 있게 천천히 출발해 볼까요?"
        }
    }

    internal fun floorInt(d: Double) = floor(d).toInt()
}
