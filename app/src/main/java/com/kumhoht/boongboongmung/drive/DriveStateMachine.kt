package com.kumhoht.boongboongmung.drive

enum class DrivePhase { PARKED, DRIVING }

sealed interface DriveTransition {
    data class Started(val atMs: Long) : DriveTransition
    data class Ended(val atMs: Long) : DriveTransition
}

/**
 * 출발 전(주차) ↔ 주행 중 상태 머신. 순수 로직 (안드로이드 의존 없음).
 *
 * - 운행 시작: 기어 D/R 진입. 기어를 못 읽는 차량이면 '주행 규제 활성 + 차속 > 0' 으로 대신 판단.
 * - 운행 종료: 기어 P + 차속 0. 기어를 못 읽으면 '주행 규제 해제 + 차속 0' 이 [unknownGearStopMs] 동안 유지.
 */
class DriveStateMachine(
    initial: DrivePhase = DrivePhase.PARKED,
    private val stopSpeedKmh: Float = 1f,
    private val unknownGearStopMs: Long = 10_000L,
) {
    var phase: DrivePhase = initial
        private set

    private var stillSinceMs: Long? = null

    fun onSample(s: VehicleSample): DriveTransition? {
        val speed = s.speedKmh ?: 0f
        return when (phase) {
            DrivePhase.PARKED -> {
                val start = when (s.gear) {
                    GearState.DRIVE, GearState.REVERSE -> true
                    GearState.UNKNOWN -> s.restricted && speed > stopSpeedKmh
                    else -> false
                }
                if (start) {
                    phase = DrivePhase.DRIVING
                    stillSinceMs = null
                    DriveTransition.Started(s.timeMs)
                } else null
            }

            DrivePhase.DRIVING -> {
                val end = when (s.gear) {
                    GearState.PARK -> speed < stopSpeedKmh
                    GearState.UNKNOWN -> {
                        if (!s.restricted && speed < stopSpeedKmh) {
                            val since = stillSinceMs ?: s.timeMs.also { stillSinceMs = it }
                            s.timeMs - since >= unknownGearStopMs
                        } else {
                            stillSinceMs = null
                            false
                        }
                    }
                    else -> false
                }
                if (end) {
                    phase = DrivePhase.PARKED
                    stillSinceMs = null
                    DriveTransition.Ended(s.timeMs)
                } else null
            }
        }
    }
}
