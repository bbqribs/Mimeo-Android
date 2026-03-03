package com.mimeo.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mimeo.android.data.dao.CachedItemDao
import com.mimeo.android.data.dao.FolderDao
import com.mimeo.android.data.dao.NowPlayingDao
import com.mimeo.android.data.dao.PendingProgressDao
import com.mimeo.android.data.entities.CachedItemEntity
import com.mimeo.android.data.entities.FolderEntity
import com.mimeo.android.data.entities.FolderPlaylistEntity
import com.mimeo.android.data.entities.NowPlayingEntity
import com.mimeo.android.data.entities.PendingProgressEntity

@Database(
    entities = [
        CachedItemEntity::class,
        PendingProgressEntity::class,
        NowPlayingEntity::class,
        FolderEntity::class,
        FolderPlaylistEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cachedItemDao(): CachedItemDao
    abstract fun pendingProgressDao(): PendingProgressDao
    abstract fun nowPlayingDao(): NowPlayingDao
    abstract fun folderDao(): FolderDao

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

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mimeo_android.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
        }
    }
}
