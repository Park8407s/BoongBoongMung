package com.kumhoht.boongboongnyang.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.kumhoht.boongboongnyang.BoongApp
import com.kumhoht.boongboongnyang.drive.GearState
import com.kumhoht.boongboongnyang.replay.ReplayScenarios
import com.kumhoht.boongboongnyang.vehicle.NaviSignal

/**
 * adb 로 차량 값을 흉내 내는 치트 콘솔 (디버그 빌드만).
 *
 * ```
 * adb shell am broadcast -a com.kumhoht.boongboongnyang.SIM --es gear D --ef speed 40
 * adb shell am broadcast -a com.kumhoht.boongboongnyang.SIM --ei limit 50
 * adb shell am broadcast -a com.kumhoht.boongboongnyang.SIM --ez arrive true
 * adb shell am broadcast -a com.kumhoht.boongboongnyang.SIM --ez belt false    # 안전벨트 안 맴 (true = 맴)
 * adb shell am broadcast -a com.kumhoht.boongboongnyang.SIM --es gear P --ef speed 0
 * adb shell am broadcast -a com.kumhoht.boongboongnyang.SIM --ei replay 1     # 주행 로그 재생 (1~4)
 * adb shell am broadcast -a com.kumhoht.boongboongnyang.SIM --ez clear true   # 실제 차량 값으로 복귀
 * ```
 */
class DebugSimReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as BoongApp
        val c = app.collector
        if (intent.getBooleanExtra("clear", false)) {
            c.override = null
            Log.i(TAG, "override cleared")
            return
        }
        val replay = intent.getIntExtra("replay", 0)
        if (replay > 0) {
            val sc = ReplayScenarios.byId(replay)
            val ok = sc != null && app.engine.startReplay(sc)
            Log.i(TAG, "replay $replay started=$ok")
            return
        }
        var v = c.override ?: c.live.value
        intent.getStringExtra("gear")?.let { g ->
            val gear = when (g.uppercase()) {
                "P" -> GearState.PARK
                "D" -> GearState.DRIVE
                "R" -> GearState.REVERSE
                "N" -> GearState.NEUTRAL
                else -> GearState.UNKNOWN
            }
            v = v.copy(gear = gear, restricted = gear != GearState.PARK)
        }
        if (intent.hasExtra("speed")) v = v.copy(speedKmh = intent.getFloatExtra("speed", 0f))
        if (intent.hasExtra("limit")) v = v.copy(speedLimitKmh = intent.getIntExtra("limit", 0))
        // 안전벨트: --ez belt true (맴) / false (안 맴)
        if (intent.hasExtra("belt")) {
            val unbuckled = !intent.getBooleanExtra("belt", true)
            c.debugSetBelt(unbuckled)
            Log.i(TAG, "belt unbuckled=$unbuckled")
            // 벨트만 바꾸는 경우 주행 값 덮어쓰기는 하지 않는다
            if (intent.extras?.keySet()?.all { it == "belt" } == true) return
            v = v.copy(beltUnbuckled = unbuckled)
        }
        c.override = v
        if (intent.getBooleanExtra("arrive", false)) c.emitNavi(NaviSignal.ARRIVED)
        Log.i(TAG, "override = $v")
    }

    companion object {
        private const val TAG = "DebugSim"
        const val ACTION = "com.kumhoht.boongboongnyang.SIM"

        fun register(app: BoongApp) {
            ContextCompat.registerReceiver(
                app, DebugSimReceiver(), IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED
            )
        }
    }
}
