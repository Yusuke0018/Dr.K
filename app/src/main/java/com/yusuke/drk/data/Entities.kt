package com.yusuke.drk.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "session")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startAtMs: Long,
    val endAtMs: Long? = null,
    val distanceM: Double = 0.0,
    val durationS: Long = 0,
    val avgPaceSecPerKm: Int? = null,
    val pointsCount: Int = 0
)

@Entity(tableName = "track_point")
data class TrackPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val tMs: Long,
    val lat: Double,
    val lon: Double,
    val accM: Float?,
    val speedMps: Float?,
    val cumDistanceM: Double
)

@Entity(tableName = "daily_stat")
data class DailyStat(
    @PrimaryKey val date: String, // ISO yyyy-MM-dd（ローカル）
    val totalDistanceM: Double,
    val totalDurationS: Long,
    val earnedXp: Int,
    val earnedTitlesCsv: String
)

@Entity(tableName = "player_state")
data class PlayerState(
    @PrimaryKey val id: Int = 0,
    val totalXp: Int,
    val level: Int,
    val nextLevelXp: Int,
    val titlesCsv: String,
    val streakDays: Int,
    val lastActiveDate: String?
)

@Entity(tableName = "title_def")
data class TitleDef(
    @PrimaryKey val key: String,
    val name: String,
    val conditionType: String,
    val threshold: Long
)
