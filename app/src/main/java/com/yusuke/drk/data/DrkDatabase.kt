package com.yusuke.drk.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Session::class, TrackPoint::class, DailyStat::class, PlayerState::class, TitleDef::class],
    version = 2,
    exportSchema = false
)
abstract class DrkDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun dailyStatDao(): DailyStatDao
    abstract fun playerStateDao(): PlayerStateDao
    abstract fun titleDefDao(): TitleDefDao

    companion object {
        @Volatile private var INSTANCE: DrkDatabase? = null
        fun get(context: Context): DrkDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                DrkDatabase::class.java,
                "drk.db"
            )
                .fallbackToDestructiveMigration()
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // プリセット称号投入
                        val scope = CoroutineScope(Dispatchers.IO)
                        scope.launch {
                            val instance = get(context)
                            instance.titleDefDao().insertAll(
                                listOf(
                                    TitleDef("FIRST_1KM", "はじめの1km", "SESSION_DISTANCE", 1000),
                                    TitleDef("CUM_100KM", "累積100km", "CUM_DISTANCE", 100_000),
                                    TitleDef("STREAK_7", "7日連続", "STREAK", 7)
                                )
                            )
                            // 初期PlayerState
                            instance.playerStateDao().upsert(
                                PlayerState(totalXp = 0, level = 1, nextLevelXp = 100, titlesCsv = "", streakDays = 0, lastActiveDate = null)
                            )
                        }
                    }
                })
                .build().also { INSTANCE = it }
        }
    }
}
