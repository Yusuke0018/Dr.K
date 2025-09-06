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
    val pointsCount: Int = 0,
    val currentSessionId: Long? = null
)

object TrackingRepository {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _state = MutableStateFlow(TrackingState())
    val state: StateFlow<TrackingState> = _state

    data class ResultEvent(
        val sessionId: Long,
        val earnedXp: Int,
        val levelUp: Boolean,
        val newTitles: List<String>
    )
    private val _results = kotlinx.coroutines.flow.MutableSharedFlow<ResultEvent>(replay = 1)
    val results: kotlinx.coroutines.flow.SharedFlow<ResultEvent> = _results

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
            _state.emit(TrackingState(isTracking = true, startAtMs = now, totalDistanceM = 0.0, currentSessionId = sessionId))
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
            // 日次集計更新・XP/レベル・称号の判定
            finalizeSession(context, sessionId, distance, durationS)
            currentSessionId = null
            _state.emit(TrackingState(isTracking = false, startAtMs = null, totalDistanceM = 0.0, currentSessionId = null))
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

    // セッション終了時の集計・XP・称号処理
    private suspend fun finalizeSession(context: Context, sessionId: Long, distanceM: Double, durationS: Long) {
        val db = DrkDatabase.get(context)
        // DailyStat 更新（当日分に加算）
        val date = java.time.LocalDate.now().toString()
        val newXp = calcXp(distanceM, durationS)
        val existing = db.dailyStatDao().getByDate(date)
        val updated = if (existing == null) {
            DailyStat(date, totalDistanceM = distanceM, totalDurationS = durationS, earnedXp = newXp, earnedTitlesCsv = "")
        } else {
            existing.copy(
                totalDistanceM = existing.totalDistanceM + distanceM,
                totalDurationS = existing.totalDurationS + durationS,
                earnedXp = existing.earnedXp + newXp
            )
        }
        db.dailyStatDao().upsert(updated)

        // PlayerState 更新（XP, レベル）
        val ps = db.playerStateDao().get() ?: PlayerState(totalXp = 0, level = 1, nextLevelXp = 100, titlesCsv = "", streakDays = 0, lastActiveDate = null)
        var totalXp = ps.totalXp + newXp
        var level = ps.level
        var nextXp = ps.nextLevelXp
        var levelUp = false
        while (totalXp >= nextXp) {
            level += 1
            totalXp -= nextXp
            nextXp = level * 100
            levelUp = true
        }

        // 連続日数（streak）
        val lastDate = ps.lastActiveDate
        val localToday = java.time.LocalDate.now()
        val newStreak = when {
            lastDate == null -> 1
            java.time.LocalDate.parse(lastDate).plusDays(1) == localToday -> ps.streakDays + 1
            java.time.LocalDate.parse(lastDate) == localToday -> ps.streakDays
            else -> 1
        }

        // 称号判定
        val titlesOwned = ps.titlesCsv.split(',').filter { it.isNotBlank() }.toMutableSet()
        val defs = db.titleDefDao().all()
        val totalDistAll = db.sessionDao().totalDistanceM() ?: 0.0
        val newTitles = mutableListOf<String>()
        defs.forEach { def ->
            val ok = when (def.conditionType) {
                "SESSION_DISTANCE" -> distanceM >= def.threshold
                "CUM_DISTANCE" -> totalDistAll >= def.threshold
                "STREAK" -> newStreak >= def.threshold
                else -> false
            }
            if (ok && !titlesOwned.contains(def.key)) {
                titlesOwned.add(def.key)
                newTitles.add(def.key)
            }
        }

        db.playerStateDao().upsert(
            PlayerState(
                totalXp = totalXp,
                level = level,
                nextLevelXp = nextXp,
                titlesCsv = titlesOwned.joinToString(","),
                streakDays = newStreak,
                lastActiveDate = localToday.toString()
            )
        )
        _results.emit(ResultEvent(sessionId, earnedXp = newXp, levelUp = levelUp, newTitles = newTitles))
    }

    private fun calcXp(distanceM: Double, durationS: Long): Int {
        val kmXp = (distanceM / 1000.0 * 10.0).toInt()
        val timeXp = (durationS / 600.0 * 5.0).toInt()
        return kmXp + timeXp
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
