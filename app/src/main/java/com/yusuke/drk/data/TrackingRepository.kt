package com.yusuke.drk.data

import android.content.Context
import android.location.Location
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class TrackingState(
    val isTracking: Boolean = false,
    val startAtMs: Long? = null,
    val totalDistanceM: Double = 0.0,
    val lastLat: Double? = null,
    val lastLon: Double? = null,
    val pointsCount: Int = 0
)

object TrackingRepository {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _state = MutableStateFlow(TrackingState())
    val state: StateFlow<TrackingState> = _state

    private var currentSessionId: Long? = null
    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private var totalDistanceM: Double = 0.0
    private var pointsCount: Int = 0

    fun start(context: Context) {
        if (currentSessionId != null) return
        val now = System.currentTimeMillis()
        scope.launch {
            val db = DrkDatabase.get(context)
            val sessionId = db.sessionDao().insert(Session(startAtMs = now))
            currentSessionId = sessionId
            lastLat = null
            lastLon = null
            totalDistanceM = 0.0
            pointsCount = 0
            _state.emit(TrackingState(isTracking = true, startAtMs = now, totalDistanceM = 0.0))
        }
    }

    fun stop(context: Context) {
        val sessionId = currentSessionId ?: return
        val startAt = _state.value.startAtMs ?: return
        val endAt = System.currentTimeMillis()
        val durationS = ((endAt - startAt) / 1000L)
        val distance = totalDistanceM
        val avgPaceSecPerKm = if (distance > 0) (durationS / (distance / 1000.0)).toInt() else null
        scope.launch {
            val db = DrkDatabase.get(context)
            val old = db.sessionDao().getById(sessionId) ?: return@launch
            db.sessionDao().update(
                old.copy(
                    endAtMs = endAt,
                    distanceM = distance,
                    durationS = durationS,
                    avgPaceSecPerKm = avgPaceSecPerKm,
                    pointsCount = pointsCount
                )
            )
            currentSessionId = null
            _state.emit(TrackingState(isTracking = false, startAtMs = null, totalDistanceM = 0.0))
        }
    }

    fun onLocation(context: Context, loc: Location) {
        val sessionId = currentSessionId ?: return
        val acc = loc.accuracy
        if (!acc.isNaN() && acc > 40f) return

        val prevLat = lastLat
        val prevLon = lastLon
        val lat = loc.latitude
        val lon = loc.longitude

        var d = 0.0
        if (prevLat != null && prevLon != null) {
            d = haversineM(prevLat, prevLon, lat, lon)
        }
        if (d < 3.0) d = 0.0

        lastLat = lat
        lastLon = lon
        if (d > 0.0) totalDistanceM += d
        pointsCount += 1

        scope.launch {
            val db = DrkDatabase.get(context)
            db.trackPointDao().insert(
                TrackPoint(
                    sessionId = sessionId,
                    tMs = System.currentTimeMillis(),
                    lat = lat,
                    lon = lon,
                    accM = if (acc.isNaN()) null else acc,
                    speedMps = if (loc.hasSpeed()) loc.speed else null,
                    cumDistanceM = totalDistanceM
                )
            )
            _state.emit(
                _state.value.copy(
                    totalDistanceM = totalDistanceM,
                    lastLat = lat,
                    lastLon = lon,
                    pointsCount = pointsCount
                )
            )
        }
    }

    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
}

