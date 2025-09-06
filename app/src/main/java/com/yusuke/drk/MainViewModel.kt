package com.yusuke.drk

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yusuke.drk.data.TrackingRepository
import com.yusuke.drk.data.SettingsRepository
import com.yusuke.drk.data.DrkDatabase
import com.yusuke.drk.data.TrackPoint
import com.yusuke.drk.data.DailyStat
import com.yusuke.drk.data.PlayerState
import com.yusuke.drk.data.TitleDef
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class UiState(
    val isTracking: Boolean = false,
    val distanceKmText: String = "0.00",
    val elapsedText: String = "00:00:00",
    val paceText: String = "-:--/km"
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = app.applicationContext

    val ui: StateFlow<UiState> = combine(
        TrackingRepository.state,
        SettingsRepository.unitMiles(appContext)
    ) { s, miles ->
        val distKm = s.totalDistanceM / 1000.0
        val dist = if (miles) distKm * 0.621371 else distKm
        val unit = if (miles) "mi" else "km"
        val start = s.startAtMs
        val now = System.currentTimeMillis()
        val elapsedS = if (s.isTracking && start != null) ((now - start) / 1000L) else 0L
        val pace = if (distKm > 0.0 && elapsedS > 0) (elapsedS / distKm).toInt() else null
        UiState(
            isTracking = s.isTracking,
            distanceKmText = String.format("%.2f %s", dist, unit),
            elapsedText = formatHms(elapsedS),
            paceText = pace?.let { formatPace(it) } ?: "-:--/km"
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    fun start() = TrackingRepository.start(appContext)
    fun stop() = TrackingRepository.stop(appContext)

    fun observeCurrentTrackPoints(): kotlinx.coroutines.flow.Flow<List<TrackPoint>> =
        kotlinx.coroutines.flow.flatMapLatest(TrackingRepository.state) { s ->
            val id = s.currentSessionId
            if (id == null) kotlinx.coroutines.flow.flowOf(emptyList())
            else DrkDatabase.get(appContext).trackPointDao().observeBySession(id)
        }

    fun observeTrackPoints(sessionId: Long): kotlinx.coroutines.flow.Flow<List<TrackPoint>> =
        DrkDatabase.get(appContext).trackPointDao().observeBySession(sessionId)

    fun observeDailyRange(from: String, to: String): kotlinx.coroutines.flow.Flow<List<DailyStat>> =
        DrkDatabase.get(appContext).dailyStatDao().observeRange(from, to)

    suspend fun getSession(id: Long) = DrkDatabase.get(appContext).sessionDao().getById(id)
    suspend fun getPlayerState(): PlayerState? = DrkDatabase.get(appContext).playerStateDao().get()
    suspend fun getTitleDefs(): List<TitleDef> = DrkDatabase.get(appContext).titleDefDao().all()

    private fun formatHms(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }

    private fun formatPace(secPerKm: Int): String {
        val m = secPerKm / 60
        val s = secPerKm % 60
        return String.format("%d:%02d/km", m, s)
    }
}
