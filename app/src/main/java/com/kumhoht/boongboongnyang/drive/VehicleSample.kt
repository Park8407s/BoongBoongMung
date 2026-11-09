package com.kumhoht.boongboongnyang.drive

/** 기어 상태. SDK 마다 표현이 달라서 앱 안에서는 이 enum 하나로 통일한다. */
enum class GearState { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN;
    val isMoving: Boolean get() = this == DRIVE || this == REVERSE
}

/**
 * 차량 신호 한 장면. Telemetry Collector 가 0.5초마다 마지막 값으로 하나씩 만든다.
 * (CarPropertyManager 는 값이 '바뀔 때만' 알려주므로, 일정 속도로 달리는 동안에도
 *  주행거리를 적분하려면 주기적으로 샘플을 만들어야 한다.)
 *
 * @param timeMs        샘플 시각 (재생 모드에서는 가상 시각)
 * @param speedKmh      차속 km/h. 모르면 null
 * @param brakePressed  브레이크 페달. 모르면 null
 * @param odometerKm    누적 주행거리 km (실차에서만 오는 경우가 많다). 모르면 null
 * @param speedLimitKmh 현재 도로 제한속도 (내비 연동 시). 모르면 null 또는 0
 * @param restricted    플랫폼 주행 규제(CarUxRestrictions) 활성 여부
 * @param beltUnbuckled 운전석 안전벨트를 안 맸는지. 모르면 null
 */
data class VehicleSample(
    val timeMs: Long,
    val gear: GearState = GearState.UNKNOWN,
    val speedKmh: Float? = null,
    val brakePressed: Boolean? = null,
    val odometerKm: Float? = null,
    val speedLimitKmh: Int? = null,
    val restricted: Boolean = false,
    val beltUnbuckled: Boolean? = null,
)
