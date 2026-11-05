package com.kumhoht.boongboongmung.drive

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.kumhoht.boongboongmung.BoongApp
import com.kumhoht.boongboongmung.R

/**
 * 주행 중 백그라운드 집계를 유지하는 서비스.
 * 주행 중에는 앱 화면이 플랫폼 주행 규제로 가려지므로, 프로세스가 살아 있도록 포그라운드 서비스로 둔다.
 * 알림은 중요도 최소(소리·진동·헤드업 없음)의 상태 표시 하나뿐이다.
 */
class DriveService : Service() {

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_paw)
            .setContentTitle(getString(R.string.notif_drive_title))
            .setContentText(getString(R.string.notif_drive_text))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } catch (t: Throwable) {
            // 백그라운드에서 시작이 막힌 경우 등. 앱이 열려 있는 동안은 Application 이 집계를 계속한다.
            Log.w(TAG, "startForeground failed: ${t.message}")
        }
        (application as BoongApp).engine.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "DriveService"
        private const val CHANNEL_ID = "drive"
        private const val NOTIFICATION_ID = 7

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, DriveService::class.java))
            } catch (t: Throwable) {
                Log.w(TAG, "start failed: ${t.message}")
            }
        }

        /**
         * 붕붕멍 완전히 끄기: 서비스 정지 → 차량 신호 해제 → 화면 닫기 → 프로세스 종료.
         * 서비스가 START_STICKY 라서 먼저 stopService 로 멈춰야 시스템이 다시 살리지 않는다.
         * 다음 시동(부팅)이나 앱을 다시 열면 다시 켜진다.
         */
        fun shutdown(activity: Activity) {
            activity.stopService(Intent(activity, DriveService::class.java))
            (activity.application as BoongApp).collector.stop()
            activity.finishAndRemoveTask()
            Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 400)
        }

        private fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID, context.getString(R.string.channel_drive), NotificationManager.IMPORTANCE_MIN
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }
}

/** 차량 시동(부팅) 시 집계 시작 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) DriveService.start(context)
    }
}
