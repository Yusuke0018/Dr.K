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

