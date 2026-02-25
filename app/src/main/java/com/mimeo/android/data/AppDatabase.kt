package com.mimeo.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mimeo.android.data.dao.CachedItemDao
import com.mimeo.android.data.dao.NowPlayingDao
import com.mimeo.android.data.dao.PendingProgressDao
import com.mimeo.android.data.entities.CachedItemEntity
import com.mimeo.android.data.entities.NowPlayingEntity
import com.mimeo.android.data.entities.PendingProgressEntity

@Database(
    entities = [CachedItemEntity::class, PendingProgressEntity::class, NowPlayingEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cachedItemDao(): CachedItemDao
    abstract fun pendingProgressDao(): PendingProgressDao
    abstract fun nowPlayingDao(): NowPlayingDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS now_playing (
                        id INTEGER NOT NULL,
                        queueJson TEXT NOT NULL,
                        currentIndex INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mimeo_android.db",
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
        }
    }
}
