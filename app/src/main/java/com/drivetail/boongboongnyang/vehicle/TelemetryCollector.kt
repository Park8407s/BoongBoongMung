package com.drivetail.boongboongnyang.vehicle

import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import com.drivetail.boongboongnyang.drive.GearState
import com.drivetail.boongboongnyang.drive.VehicleSample
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 마지막으로 받은 차량 값들 */
data class LiveVehicle(
    val gear: GearState = GearState.UNKNOWN,
    val speedKmh: Float? = null,
    val brakePressed: Boolean? = null,
    val odometerKm: Float? = null,
    val speedLimitKmh: Int? = null,
    val restricted: Boolean = false,
    val routeActive: Boolean = false,
    /**
     * 운전석 안전벨트를 안 맸는지. null = 모름(신호 없음).
     * 지금은 안전벨트 경고등(Telltale SEAT_BELT) 켜짐/깜빡임을 '안 맴'으로 본다.
     */
    val beltUnbuckled: Boolean? = null,
)

/** 내비 이벤트 */
enum class NaviSignal { ROUTE_STARTED, ARRIVED, ROUTE_ENDED }

/** 신호별 연결 상태 (시연 패널에 표시) */
enum class SignalStatus { WAITING, OK, UNSUPPORTED }

/**
 * Telemetry Collector: 차량 데이터 리스너를 모아 [LiveVehicle] 하나로 만든다.
 *
 * | 신호 | 1순위 | 2순위 |
 * |---|---|---|
 * | 차속 | AAOS Car API PERF_VEHICLE_SPEED | - |
 * | 기어 | AAOS GEAR_SELECTION (Pleos 권장) | Pleos Vehicle SDK Gear (deprecated) |
 * | 브레이크 페달 | Pleos Vehicle SDK Brake | - |
 * | 누적 주행거리 | Pleos Vehicle SDK Odometer (실차에서 값이 있을 때) | 차속 적분 |
 * | 제한속도·도착 | Pleos NaviHelper | 주행 로그 재생 모드 |
 * | 주행 규제 | CarUxRestrictionsManager | - |
 *
 * 모든 리스너는 값이 '바뀔 때만' 온다. 주기 샘플은 [sample] 로 만든다.
 * 이벤트 기반으로 최소 자원만 쓴다 (폴링 없음).
 */
class TelemetryCollector(private val context: Context) {

    private val _live = MutableStateFlow(LiveVehicle())
    val live: StateFlow<LiveVehicle> = _live.asStateFlow()

    private val _navi = MutableSharedFlow<NaviSignal>(extraBufferCapacity = 8)
    val naviEvents: SharedFlow<NaviSignal> = _navi.asSharedFlow()

    private val _status = MutableStateFlow(
        mapOf(
            SIG_SPEED to SignalStatus.WAITING,
            SIG_GEAR to SignalStatus.WAITING,
            SIG_BRAKE to SignalStatus.WAITING,
            SIG_ODOMETER to SignalStatus.WAITING,
            SIG_NAVI to SignalStatus.WAITING,
            SIG_UX to SignalStatus.WAITING,
            SIG_BELT to SignalStatus.WAITING,
        )
    )
    val status: StateFlow<Map<String, SignalStatus>> = _status.asStateFlow()

    private var vehicle: Vehicle? = null
    private var seatBelt: SeatBeltProbe? = null
    private var naviHelper: NaviHelper? = null
    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null
    private var uxManager: CarUxRestrictionsManager? = null
    /** AAOS GEAR_SELECTION 이 값을 주면 Pleos Gear(deprecated)는 무시한다 */
    private var aaosGearWorks = false
    private var started = false

    /** 디버그(adb) 덮어쓰기 값. null 이면 실제 값 사용 */
    @Volatile var override: LiveVehicle? = null

    /** 디버그(adb): 안전벨트 상태를 화면에도 보이게 바로 바꾼다. 다음 실제 신호가 오면 덮어쓴다 */
    fun debugSetBelt(unbuckled: Boolean) = _live.update { it.copy(beltUnbuckled = unbuckled) }

    fun start() {
        if (started) return
        started = true
        startCarApi()
        startPleosVehicle()
        startNavi()
    }

    fun stop() {
        if (!started) return
        started = false
        safely("release car") {
            propertyManager?.unregisterCallback(propertyCallback)
            uxManager?.unregisterListener()
            car?.disconnect()
        }
        safely("release seat belt") { seatBelt?.stop(); seatBelt = null }
        safely("release vehicle") {
            vehicle?.let { v ->
                @Suppress("DEPRECATION")
                v.getGear().unregisterGearSelection(gearListener)
                v.getBrake().unregisterBrakePedalStatus(brakeListener)
                v.getOdometer().unregisterDistanceDriven(odometerListener)
                v.getSafety().unregisterTelltales(beltLampListener)
                v.release()
            }
        }
        safely("release navi") {
            naviHelper?.removeListener(naviListener)
            naviHelper?.release()
        }
        vehicle = null; naviHelper = null; car = null; propertyManager = null; uxManager = null
    }

    /**
     * 런타임 권한(CAR_SPEED 등)을 사용자가 허용한 뒤 다시 등록한다.
     * 권한 없이 등록하면 SecurityException 이 나서 차속이 오지 않는다.
     */
    fun onPermissionsGranted() {
        val pm = propertyManager ?: return
        safely("speed re-register") {
            val ok = pm.registerCallback(
                propertyCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL
            )
            if (ok) mark(SIG_SPEED, SignalStatus.WAITING)
        }
        safely("aaos gear") {
            pm.registerCallback(propertyCallback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        }
    }

    /** 현재 값으로 샘플 한 장 */
    fun sample(nowMs: Long): VehicleSample {
        val v = override ?: _live.value
        return VehicleSample(
            timeMs = nowMs,
            gear = v.gear,
            speedKmh = v.speedKmh,
            brakePressed = v.brakePressed,
            odometerKm = v.odometerKm,
            speedLimitKmh = v.speedLimitKmh?.takeIf { it > 0 },
            restricted = v.restricted,
            // 안전벨트는 adb 치트도 _live 로 바로 바꾸므로 항상 _live 값을 쓴다
            beltUnbuckled = _live.value.beltUnbuckled,
        )
    }

    /** 디버그(adb)용: 내비 이벤트 흉내 */
    fun emitNavi(signal: NaviSignal) { onNavi(signal) }

    // ---------------------------------------------------------------- AAOS Car API

    private val propertyCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            when (value.propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> {
                    val ms = (value.value as? Float) ?: return
                    // m/s → km/h. 후진 시 음수가 올 수 있어 절댓값.
                    _live.update { it.copy(speedKmh = kotlin.math.abs(ms) * 3.6f) }
                    mark(SIG_SPEED, SignalStatus.OK)
                }
                VehiclePropertyIds.GEAR_SELECTION -> {
                    val g = (value.value as? Int) ?: return
                    aaosGearWorks = true
                    _live.update { it.copy(gear = aaosGear(g)) }
                    mark(SIG_GEAR, SignalStatus.OK)
                }
            }
        }

        override fun onErrorEvent(propertyId: Int, zone: Int) {
            Log.w(TAG, "Car property error id=$propertyId zone=$zone")
        }
    }

    private fun startCarApi() = safely("car api") {
        car = Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { c, ready ->
            if (!ready) {
                Log.w(TAG, "Car service not ready")
                return@createCar
            }
            safely("property manager") {
                val pm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                propertyManager = pm
                safely("speed") {
                    val speedOk = pm.registerCallback(
                        propertyCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL
                    )
                    if (!speedOk) mark(SIG_SPEED, SignalStatus.UNSUPPORTED)
                }
                safely("aaos gear") {
                    pm.registerCallback(propertyCallback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
                }
            }
            safely("ux restrictions") {
                val ux = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                uxManager = ux
                applyUx(ux.currentCarUxRestrictions)
                ux.registerListener { r -> applyUx(r) }
                mark(SIG_UX, SignalStatus.OK)
            }
        }
    }

    private fun applyUx(r: CarUxRestrictions?) {
        val restricted = r?.isRequiresDistractionOptimization ?: false
        _live.update { it.copy(restricted = restricted) }
    }

    private fun aaosGear(g: Int): GearState = when (g) {
        android.car.VehicleGear.GEAR_PARK -> GearState.PARK
        android.car.VehicleGear.GEAR_REVERSE -> GearState.REVERSE
        android.car.VehicleGear.GEAR_NEUTRAL -> GearState.NEUTRAL
        android.car.VehicleGear.GEAR_UNKNOWN -> GearState.UNKNOWN
        else -> GearState.DRIVE // DRIVE, 1~9단
    }

    // ---------------------------------------------------------------- Pleos Vehicle SDK

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            if (gear == null || aaosGearWorks) return
            _live.update { it.copy(gear = pleosGear(gear)) }
            mark(SIG_GEAR, SignalStatus.OK)
        }
        override fun onFailed(e: Exception) { Log.w(TAG, "gear failed: ${e.message}") }
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) {
            _live.update { it.copy(brakePressed = status) }
            mark(SIG_BRAKE, SignalStatus.OK)
        }
        override fun onFailed(e: Exception) { Log.w(TAG, "brake failed: ${e.message}") }
    }

    private val odometerListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) {
            if (value == null) return
            _live.update { it.copy(odometerKm = value) }
            mark(SIG_ODOMETER, SignalStatus.OK)
        }
        override fun onFailed(e: Exception) {
            Log.w(TAG, "odometer failed: ${e.message}")
            mark(SIG_ODOMETER, SignalStatus.UNSUPPORTED)
        }
    }

    @Suppress("DEPRECATION")
    private fun startPleosVehicle() = safely("pleos vehicle") {
        val v = Vehicle(context)
        vehicle = v
        v.initialize()
        // 권한이 없거나 서비스가 없으면 register 가 즉시 예외를 던진다 → 항목별로 감싼다.
        safely("gear") {
            v.getGear().getCurrentGearSelectionState({ g ->
                if (g == null || aaosGearWorks) return@getCurrentGearSelectionState
                _live.update { it.copy(gear = pleosGear(g)) }
                mark(SIG_GEAR, SignalStatus.OK)
            }, { e -> Log.w(TAG, "gear get failed: ${e.message}") })
            v.getGear().registerGearSelection(gearListener)
        }
        safely("brake") {
            v.getBrake().getCurrentBrakePedalStatus({ p ->
                _live.update { it.copy(brakePressed = p) }
                mark(SIG_BRAKE, SignalStatus.OK)
            }, { e ->
                Log.w(TAG, "brake get failed: ${e.message}")
            })
            v.getBrake().registerBrakePedalStatus(brakeListener)
        }
        safely("odometer") {
            // 차종별로 지원 여부가 다르다. 지원하면 실차에서 Odometer 값을 우선 사용.
            v.getOdometer().getCurrentDistanceDriven({ km ->
                if (km == null) return@getCurrentDistanceDriven
                _live.update { it.copy(odometerKm = km) }
                mark(SIG_ODOMETER, SignalStatus.OK)
            }, { e ->
                Log.w(TAG, "odometer get failed: ${e.message}")
                mark(SIG_ODOMETER, SignalStatus.UNSUPPORTED)
            })
            v.getOdometer().registerDistanceDriven(odometerListener)
        }
        // 안전벨트 (운전석) — 지금은 경고등으로 판단.
        // 에뮬레이터는 좌석별 체결 상태를 바꿀 방법이 없고(항상 false), 경고등은 가상 콘솔
        // Safety > Telltales > Seat Belt 로 켜고 끌 수 있어서 시연용으로 경고등을 쓴다.
        // 권한: pleos.car.permission.PRIVATE_CAR_SAFETY
        safely("seat belt lamp") {
            v.getSafety().getTelltalesInfo({ t -> onBeltLamp(t) }, { e -> Log.w(TAG, "belt lamp get failed: ${e.message}") })
            v.getSafety().registerTelltales(beltLampListener)
        }
        // [실차 전환 시] 경고등 대신 운전석 '체결 상태'를 직접 쓴다 (권한: pleos.car.permission.CAR_SEATS).
        // 경고등은 체결 상태의 결과일 뿐이라(시간이 지나면 꺼지기도 함) 실차에서는 이쪽이 정확하다.
        // 위 경고등 블록을 지우고 아래 주석을 풀면 된다.
        // safely("seat belt buckled") {
        //     val driver = SeatArea(SeatValue.ROW_1_LEFT)
        //     v.getSeat().getCurrentSeatBeltBuckledState(driver, { buckled ->
        //         if (buckled != null) _live.update { it.copy(beltUnbuckled = !buckled) }
        //         mark(SIG_BELT, SignalStatus.OK)
        //     }, { e -> Log.w(TAG, "belt get failed: ${e.message}") })
        //     v.getSeat().registerSeatBeltBuckledState(driver, object : SeatBeltBuckledStateListener {
        //         override fun onSeatBeltBuckledStateUpdated(value: Boolean?) {
        //             if (value != null) _live.update { it.copy(beltUnbuckled = !value) }
        //         }
        //         override fun onFailed(e: Exception) { Log.w(TAG, "belt failed: ${e.message}") }
        //     })
        // }

        // 안전벨트 신호 확인 (개발자 확인용, 로그만: adb logcat -s SeatBelt)
        safely("seat belt") { seatBelt = SeatBeltProbe(v).also { it.start() } }
    }

    private val beltLampListener = object : TelltaleInfoListener {
        override fun onTelltaleInfoUpdated(value: Telltales?) { if (value != null) onBeltLamp(value) }
        override fun onFailed(e: Exception) { Log.w(TAG, "belt lamp failed: ${e.message}") }
    }

    /** 안전벨트 경고등이 켜짐/깜빡임이면 '안 맴'. 목록에 없으면 꺼짐(= 맴) */
    private fun onBeltLamp(t: Telltales) {
        val state = t.telltales.firstOrNull { it.type == TelltaleType.SEAT_BELT }?.state ?: TelltaleState.OFF
        val unbuckled = state != TelltaleState.OFF
        if (unbuckled != _live.value.beltUnbuckled) Log.i(TAG, "seat belt lamp=$state → unbuckled=$unbuckled")
        _live.update { it.copy(beltUnbuckled = unbuckled) }
        mark(SIG_BELT, SignalStatus.OK)
    }

    private fun pleosGear(g: VehicleGear): GearState = when (g) {
        VehicleGear.GEAR_PARK -> GearState.PARK
        VehicleGear.GEAR_REVERSE -> GearState.REVERSE
        VehicleGear.GEAR_NEUTRAL -> GearState.NEUTRAL
        VehicleGear.GEAR_UNKNOWN -> GearState.UNKNOWN
        else -> GearState.DRIVE // DRIVE, 1~9단
    }

    // ---------------------------------------------------------------- Pleos NaviHelper

    private val naviListener = object : NaviHelperEventListener {
        override fun onCurrentRoadSpeedLimit(speedLimit: Int) {
            // 0 = 도로를 못 찾음(위치가 도로 밖·지도 밖). 값이 바뀔 때만 로그
            if (speedLimit != _live.value.speedLimitKmh) Log.i(TAG, "navi speedLimit=$speedLimit")
            _live.update { it.copy(speedLimitKmh = speedLimit) }
        }
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            Log.i(TAG, "navi routeStarted")
            _live.update { it.copy(routeActive = true) }
            onNavi(NaviSignal.ROUTE_STARTED)
        }
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            Log.i(TAG, "navi destinationArrived")
            onNavi(NaviSignal.ARRIVED)
        }
        override fun onRouteEnded() { Log.i(TAG, "navi routeEnded"); onNavi(NaviSignal.ROUTE_ENDED) }
        override fun onRouteCancelled() { Log.i(TAG, "navi routeCancelled"); onNavi(NaviSignal.ROUTE_ENDED) }
    }

    private fun startNavi() = safely("navi") {
        val n = NaviHelper(context)
        naviHelper = n
        n.initialize({
            safely("navi listener") {
                n.addListener(naviListener)
                mark(SIG_NAVI, SignalStatus.OK)
            }
        }, { t ->
            Log.w(TAG, "NaviHelper init failed: ${t.message}")
            mark(SIG_NAVI, SignalStatus.UNSUPPORTED)
        })
    }

    private fun onNavi(signal: NaviSignal) {
        when (signal) {
            NaviSignal.ROUTE_STARTED -> _live.update { it.copy(routeActive = true) }
            NaviSignal.ARRIVED, NaviSignal.ROUTE_ENDED ->
                _live.update { it.copy(routeActive = false, speedLimitKmh = null) }
        }
        _navi.tryEmit(signal)
    }

    // ----------------------------------------------------------------

    private fun mark(key: String, s: SignalStatus) = _status.update { it + (key to s) }

    private inline fun safely(what: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.w(TAG, "$what unavailable: ${t.message}")
            when (what) {
                "brake" -> mark(SIG_BRAKE, SignalStatus.UNSUPPORTED)
                "odometer" -> mark(SIG_ODOMETER, SignalStatus.UNSUPPORTED)
                "navi" -> mark(SIG_NAVI, SignalStatus.UNSUPPORTED)
                "ux restrictions" -> mark(SIG_UX, SignalStatus.UNSUPPORTED)
                "seat belt lamp" -> mark(SIG_BELT, SignalStatus.UNSUPPORTED)
            }
        }
    }

    companion object {
        private const val TAG = "Telemetry"
        const val SIG_SPEED = "차속 (Car API)"
        const val SIG_GEAR = "기어 (Vehicle SDK)"
        const val SIG_BRAKE = "브레이크 (Vehicle SDK)"
        const val SIG_ODOMETER = "누적 주행거리 (Vehicle SDK)"
        const val SIG_NAVI = "제한속도·도착 (NaviHelper)"
        const val SIG_UX = "주행 규제 (UxRestrictions)"
        const val SIG_BELT = "안전벨트 경고등 (Vehicle SDK)"
    }
}
