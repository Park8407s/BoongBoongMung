package com.kumhoht.boongboongmung.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatBeltNotBuckledWarningListener
import android.util.Log

/**
 * 안전벨트 신호 확인용 (개발자 확인 체크리스트). 결과는 로그로만 남긴다: `adb logcat -s SeatBelt`
 *
 * Pleos Vehicle SDK 에는 성격이 다른 두 가지가 있다.
 * - Safety '미착용 경고': 차가 안전벨트 경고(경고등)를 켰는지
 *   checkSeatNotBuckledWarningCapabilities / hasSeatBeltNotBuckledWarning / registerSeatBeltNotBuckledWarning
 * - Seat '좌석별 체결 상태': 실제로 벨트를 맸는지
 *   checkSeatBeltBuckledCapabilities / getCurrentSeatBeltBuckledState / registerSeatBeltBuckledState
 *   (Safety 쪽에도 checkSeatBuckledCapabilities 가 있어 함께 확인한다)
 *
 * 지원 좌석 목록을 먼저 받고, 목록이 비면 운전석(1열 왼쪽)으로 시도한다.
 */
class SeatBeltProbe(private val vehicle: Vehicle) {

    private val driver = SeatArea(SeatValue.ROW_1_LEFT)
    private val registered = mutableListOf<() -> Unit>()

    fun start() {
        val safety = vehicle.getSafety()
        val seat = vehicle.getSeat()

        // ③ 경고등(Telltale) 중 안전벨트: 가상 콘솔 Safety > Telltales > Seat Belt 로 켜고 끌 수 있다
        safely("safety.getTelltalesInfo") {
            safety.getTelltalesInfo({ t ->
                Log.i(TAG, "③ 경고등 조회: 안전벨트=${seatBeltLamp(t)} (전체 ${t.telltales.size}개)")
            }, { e -> fail("③ 경고등 조회", e) })
        }
        safely("safety.registerTelltales") {
            val l = object : TelltaleInfoListener {
                override fun onTelltaleInfoUpdated(value: Telltales?) {
                    if (value != null) Log.i(TAG, "③ 경고등 변경 알림: 안전벨트=${seatBeltLamp(value)}")
                }
                override fun onFailed(e: Exception) = fail("③ 경고등 변경 알림", e)
            }
            safety.registerTelltales(l)
            registered += { safety.unregisterTelltales(l) }
            Log.i(TAG, "③ 경고등 변경 알림 등록")
        }

        // ① 지원 여부 (Capabilities)
        safely("safety.checkSeatNotBuckledWarningCapabilities") {
            safety.checkSeatNotBuckledWarningCapabilities({ cfg, areas ->
                Log.i(TAG, "① Safety 미착용 경고 지원: config=$cfg areas=${areas.names()}")
                areas.ifEmpty { listOf(driver) }.forEach { watchWarning(it) }
            }, { e -> fail("① Safety 미착용 경고 지원 확인", e); watchWarning(driver) })
        }
        safely("safety.checkSeatBuckledCapabilities") {
            safety.checkSeatBuckledCapabilities({ cfg, areas ->
                Log.i(TAG, "① Safety 체결 지원: config=$cfg areas=${areas.names()}")
            }, { e -> fail("① Safety 체결 지원 확인", e) })
        }
        safely("seat.checkSeatBeltBuckledCapabilities") {
            seat.checkSeatBeltBuckledCapabilities({ cfg, areas ->
                Log.i(TAG, "① Seat 체결 지원: config=$cfg areas=${areas.names()}")
                areas.ifEmpty { listOf(driver) }.forEach { watchBuckled(it) }
            }, { e -> fail("① Seat 체결 지원 확인", e); watchBuckled(driver) })
        }
    }

    /** ② Safety 미착용 경고: 조회 + 변경 알림 */
    private fun watchWarning(area: SeatArea) {
        val safety = vehicle.getSafety()
        val name = area.names()
        safely("safety.hasSeatBeltNotBuckledWarning $name") {
            safety.hasSeatBeltNotBuckledWarning(area, { on ->
                Log.i(TAG, "② Safety 미착용 경고 조회 $name = $on")
            }, { e -> fail("② Safety 미착용 경고 조회 $name", e) })
        }
        safely("safety.registerSeatBeltNotBuckledWarning $name") {
            val l = object : SeatBeltNotBuckledWarningListener {
                override fun onSeatBeltNotBuckledWarningUpdated(value: Boolean?) {
                    Log.i(TAG, "② Safety 미착용 경고 변경 알림 $name = $value")
                }
                override fun onFailed(e: Exception) = fail("② Safety 미착용 경고 변경 알림 $name", e)
            }
            safety.registerSeatBeltNotBuckledWarning(area, l)
            registered += { safety.unregisterSeatBeltNotBuckledWarning(area, l) }
            Log.i(TAG, "② Safety 미착용 경고 변경 알림 등록 $name")
        }
    }

    /** ② Seat 좌석별 체결 상태: 조회 + 변경 알림 */
    private fun watchBuckled(area: SeatArea) {
        val seat = vehicle.getSeat()
        val name = area.names()
        safely("seat.getCurrentSeatBeltBuckledState $name") {
            seat.getCurrentSeatBeltBuckledState(area, { buckled ->
                Log.i(TAG, "② Seat 체결 상태 조회 $name = $buckled")
            }, { e -> fail("② Seat 체결 상태 조회 $name", e) })
        }
        safely("seat.registerSeatBeltBuckledState $name") {
            val l = object : SeatBeltBuckledStateListener {
                override fun onSeatBeltBuckledStateUpdated(value: Boolean?) {
                    Log.i(TAG, "② Seat 체결 상태 변경 알림 $name = $value")
                }
                override fun onFailed(e: Exception) = fail("② Seat 체결 상태 변경 알림 $name", e)
            }
            seat.registerSeatBeltBuckledState(area, l)
            registered += { seat.unregisterSeatBeltBuckledState(area, l) }
            Log.i(TAG, "② Seat 체결 상태 변경 알림 등록 $name")
        }
    }

    fun stop() {
        registered.forEach { runCatching(it) }
        registered.clear()
    }

    /** 안전벨트 경고등 상태 (OFF/ON/BLINK). 목록에 없으면 OFF 로 본다 */
    private fun seatBeltLamp(t: Telltales): String =
        t.telltales.firstOrNull { it.type == TelltaleType.SEAT_BELT }?.state?.name ?: "OFF(목록에 없음)"

    private fun List<SeatArea>.names() = joinToString { it.names() }
    private fun SeatArea.names() = values.joinToString("+") { it.name }

    private fun fail(what: String, e: Throwable) {
        Log.w(TAG, "$what 실패: ${e.javaClass.simpleName}: ${e.message}")
    }

    private inline fun safely(what: String, block: () -> Unit) {
        try { block() } catch (t: Throwable) { fail(what, t) }
    }

    companion object {
        private const val TAG = "SeatBelt"
    }
}
