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
                .build().also { INSTANCE = it }
        }
    }
}
