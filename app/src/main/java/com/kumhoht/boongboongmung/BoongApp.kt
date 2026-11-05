package com.kumhoht.boongboongmung

import android.app.Application
import com.kumhoht.boongboongmung.data.BoongDatabase
import com.kumhoht.boongboongmung.data.BoongRepository
import com.kumhoht.boongboongmung.debug.DebugSimReceiver
import com.kumhoht.boongboongmung.drive.DriveEngine
import com.kumhoht.boongboongmung.vehicle.TelemetryCollector
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
        engine.start()
        if (BuildConfig.DEBUG) DebugSimReceiver.register(this)
    }
}
