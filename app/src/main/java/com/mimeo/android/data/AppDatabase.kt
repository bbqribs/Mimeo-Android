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
    entities = [
        CachedItemEntity::class,
        PendingProgressEntity::class,
        NowPlayingEntity::class,
    ],
    version = 6,
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS folders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS folder_playlists (
                        playlistId INTEGER NOT NULL,
                        folderId INTEGER NOT NULL,
                        PRIMARY KEY(playlistId)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_folder_playlists_folderId
                    ON folder_playlists(folderId)
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE now_playing
                    ADD COLUMN sourcePlaylistId INTEGER
                    """.trimIndent(),
                )
            }
        }
        
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE cached_items
                    ADD COLUMN contentBlocksJson TEXT
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS folder_playlists")
                db.execSQL("DROP TABLE IF EXISTS folders")
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { INSTANCE = it }
            }
        }
    }
}
