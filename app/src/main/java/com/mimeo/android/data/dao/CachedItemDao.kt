package com.mimeo.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mimeo.android.data.entities.CachedItemEntity

@Dao
interface CachedItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CachedItemEntity)

    @Query("SELECT * FROM cached_items WHERE itemId = :itemId LIMIT 1")
    suspend fun findByItemId(itemId: Int): CachedItemEntity?
}
