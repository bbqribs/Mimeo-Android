package com.mimeo.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mimeo.android.data.entities.UpNextSyncEntity

@Dao
interface UpNextSyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UpNextSyncEntity)

    @Query("SELECT * FROM up_next_sync_metadata WHERE id = 1 LIMIT 1")
    suspend fun get(): UpNextSyncEntity?

    @Query("DELETE FROM up_next_sync_metadata")
    suspend fun clear()
}
