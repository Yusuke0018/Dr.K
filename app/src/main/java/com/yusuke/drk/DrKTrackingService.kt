package com.yusuke.drk

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.android.gms.location.LocationServices

class DrKTrackingService : Service() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureTrackingChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForegroundTracking()
            ACTION_STOP -> stopForegroundTracking()
            else -> Log.d(TAG, "Unknown action: ${intent?.action}")
        }
        return START_STICKY
    }

    private fun startForegroundTracking() {
        val notification = NotificationHelper.buildTrackingNotification(this)
        startForeground(NOTIFICATION_ID, notification)

        // 位置取得の雛形（後続実装）
        val fused = LocationServices.getFusedLocationProviderClient(this)
        // TODO: リクエストとコールバックの実装
    }

    private fun stopForegroundTracking() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "DrKTrackingService"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.yusuke.drk.action.START"
        const val ACTION_STOP = "com.yusuke.drk.action.STOP"
    }
}

