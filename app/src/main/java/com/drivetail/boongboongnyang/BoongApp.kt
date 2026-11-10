package com.drivetail.boongboongnyang

import android.app.Application
import com.drivetail.boongboongnyang.data.BoongDatabase
import com.drivetail.boongboongnyang.data.BoongRepository
import com.drivetail.boongboongnyang.debug.DebugSimReceiver
import com.drivetail.boongboongnyang.drive.DriveEngine
import com.drivetail.boongboongnyang.vehicle.TelemetryCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 앱 전체에서 하나씩만 쓰는 객체 보관소 (유니티의 DontDestroyOnLoad 매니저).
 */
class BoongApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var repository: BoongRepository
        private set
    lateinit var collector: TelemetryCollector
        private set
    lateinit var engine: DriveEngine
        private set

    override fun onCreate() {
        super.onCreate()
        repository = BoongRepository(BoongDatabase.create(this))
        collector = TelemetryCollector(this)
        engine = DriveEngine(collector, repository, appScope)
        appScope.launch { repository.ensureDefaults(System.currentTimeMillis()) }
        // 집계는 여기서 시작하지 않는다. 사용자가 앱을 열었을 때(MainActivity) DriveService 로 시작하고,
        // 그 뒤에는 앱을 백그라운드로 내려도 계속한다. 부팅(시동) 때 자동으로 켜지지 않는다.
        if (BuildConfig.DEBUG) DebugSimReceiver.register(this)
    }
}
