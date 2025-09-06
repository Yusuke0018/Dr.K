package com.yusuke.drk

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import kotlin.math.*

class DrKTrackingService : Service() {
    private lateinit var fused: FusedLocationProviderClient
    private var callback: LocationCallback? = null
    private var isTracking = false
    private var lastLocation: Location? = null
    private var totalDistanceM: Double = 0.0
    private var startMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureTrackingChannel(this)
        fused = LocationServices.getFusedLocationProviderClient(this)
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
        if (isTracking) return
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted; stopping service")
            stopSelf()
            return
        }

        startMs = System.currentTimeMillis()
        totalDistanceM = 0.0
        lastLocation = null
        isTracking = true

        val notification = NotificationHelper.buildTrackingNotification(this)
        startForeground(NOTIFICATION_ID, notification)

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500L)
            .setMinUpdateIntervalMillis(1000L)
            .setMinUpdateDistanceMeters(5f)
            .build()

        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (loc in result.locations) handleLocation(loc)
                updateNotification()
            }
        }
        fused.requestLocationUpdates(request, callback as LocationCallback, Looper.getMainLooper())
    }

    private fun stopForegroundTracking() {
        if (!isTracking) return
        isTracking = false
        callback?.let { fused.removeLocationUpdates(it) }
        callback = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine && coarse
    }

    private fun handleLocation(loc: Location) {
        // 外れ値除去: 精度が悪いものを捨てる
        val acc = loc.accuracy
        if (!acc.isNaN() && acc > 40f) return

        val prev = lastLocation
        lastLocation = loc
        if (prev != null) {
            val d = haversineM(prev.latitude, prev.longitude, loc.latitude, loc.longitude)
            if (d >= 3.0) {
                totalDistanceM += d
            }
        }
    }

    private fun updateNotification() {
        val km = totalDistanceM / 1000.0
        val text = String.format("累計距離 %.2f km", km)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val n = NotificationHelper.buildTrackingNotification(this).apply {
            // 再生成するだけで簡易更新（軽量化は後続で最適化）
        }
        // NotificationCompat.Builder を使い直して更新テキストを差し込む
        val builder = androidx.core.app.NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID_TRACKING)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.notification_tracking_title))
            .setContentText(text)
            .setOngoing(true)
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "DrKTrackingService"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.yusuke.drk.action.START"
        const val ACTION_STOP = "com.yusuke.drk.action.STOP"

        private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val R = 6371000.0 // Earth radius (m)
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return R * c
        }
    }
}
