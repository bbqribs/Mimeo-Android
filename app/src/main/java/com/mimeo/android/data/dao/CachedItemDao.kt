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

    @Query("SELECT itemId FROM cached_items WHERE itemId IN (:itemIds)")
    suspend fun findCachedIds(itemIds: List<Int>): List<Int>

    @Query(
        "SELECT activeContentVersionId FROM cached_items " +
            "WHERE activeContentVersionId IS NOT NULL AND activeContentVersionId IN (:versionIds)"
    )
    suspend fun findCachedActiveContentVersionIds(versionIds: List<Int>): List<Int>
}
