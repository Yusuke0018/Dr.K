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

    @Query("SELECT SUM(distanceM) FROM session")
    suspend fun totalDistanceM(): Double?
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

@Dao
interface DailyStatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stat: DailyStat)

    @Query("SELECT * FROM daily_stat WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun observeRange(from: String, to: String): Flow<List<DailyStat>>

    @Query("SELECT * FROM daily_stat WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): DailyStat?
}

@Dao
interface PlayerStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: PlayerState)

    @Query("SELECT * FROM player_state WHERE id = 0")
    suspend fun get(): PlayerState?
}

@Dao
interface TitleDefDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<TitleDef>)

    @Query("SELECT * FROM title_def")
    suspend fun all(): List<TitleDef>
}
