package com.mimeo.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mimeo.android.data.dao.CachedItemDao
import com.mimeo.android.data.dao.PendingProgressDao
import com.mimeo.android.data.entities.CachedItemEntity
import com.mimeo.android.data.entities.PendingProgressEntity

@Database(
    entities = [CachedItemEntity::class, PendingProgressEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cachedItemDao(): CachedItemDao
    abstract fun pendingProgressDao(): PendingProgressDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mimeo_android.db",
                ).build().also { INSTANCE = it }
            }
        }
    }
}
