package com.huanyu.position

import android.content.Context
import androidx.room.Database
import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.huanyu.position.Dao.LocationDao
import com.huanyu.position.entity.LocationEntity

@Database(entities = [LocationEntity::class], version = 1)
abstract class LocationDatabase: RoomDatabase() {
    abstract fun locationDao(): LocationDao

    companion object {
        @Volatile private var INSTANCE: LocationDatabase? = null

        fun getDatabase(context: Context): LocationDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LocationDatabase::class.java,
                    "location_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }

    }


}