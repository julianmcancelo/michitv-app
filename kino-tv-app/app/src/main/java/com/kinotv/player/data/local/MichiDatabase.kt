package com.kinotv.player.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [WatchlistItem::class, WatchHistory::class], version = 1, exportSchema = false)
abstract class MichiDatabase : RoomDatabase() {
    abstract fun michiDao(): MichiDao

    companion object {
        @Volatile
        private var INSTANCE: MichiDatabase? = null

        fun getDatabase(context: Context): MichiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MichiDatabase::class.java,
                    "michitv_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
