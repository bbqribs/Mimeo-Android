package com.mimeo.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mimeo.android.data.entities.NowPlayingEntity

@Dao
interface NowPlayingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: NowPlayingEntity)

    @Query("SELECT * FROM now_playing WHERE id = 1 LIMIT 1")
    suspend fun getSession(): NowPlayingEntity?

    @Query("UPDATE now_playing SET currentIndex = :currentIndex, updatedAt = :updatedAt WHERE id = 1")
    suspend fun updateCurrentIndex(currentIndex: Int, updatedAt: Long)

    @Query("DELETE FROM now_playing WHERE id = 1")
    suspend fun clear()
}
