package com.yusuke.drk.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Session::class, TrackPoint::class],
    version = 1,
    exportSchema = false
)
abstract class DrkDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun trackPointDao(): TrackPointDao

    companion object {
        @Volatile private var INSTANCE: DrkDatabase? = null
        fun get(context: Context): DrkDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                DrkDatabase::class.java,
                "drk.db"
            ).build().also { INSTANCE = it }
        }
    }
}

