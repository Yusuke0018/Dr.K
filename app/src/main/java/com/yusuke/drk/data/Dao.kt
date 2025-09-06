package com.yusuke.drk.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: Session): Long

    @Update
    suspend fun update(session: Session)

    @Query("SELECT * FROM session WHERE id = :id")
    suspend fun getById(id: Long): Session?

    @Query("SELECT * FROM session ORDER BY startAtMs DESC")
    fun observeAll(): Flow<List<Session>>
}

@Dao
interface TrackPointDao {
    @Insert
    suspend fun insertAll(points: List<TrackPoint>)

    @Insert
    suspend fun insert(point: TrackPoint)

    @Query("SELECT * FROM track_point WHERE sessionId = :sessionId ORDER BY tMs ASC")
    fun observeBySession(sessionId: Long): Flow<List<TrackPoint>>
}

