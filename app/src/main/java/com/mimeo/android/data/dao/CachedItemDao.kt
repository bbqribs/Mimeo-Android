package com.mimeo.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mimeo.android.data.entities.CachedItemWriteSignal
import com.mimeo.android.data.entities.CachedItemEntity
import kotlinx.coroutines.flow.Flow

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

    @Query("DELETE FROM cached_items WHERE itemId = :itemId")
    suspend fun deleteByItemId(itemId: Int)

    /**
     * Emits the latest cached-item write signal whenever the [cached_items] table changes.
     * This avoids full-table ID emissions for each cache write.
     */
    @Query("SELECT itemId, cachedAt FROM cached_items ORDER BY cachedAt DESC LIMIT 1")
    fun observeLatestCachedItemWrite(): Flow<CachedItemWriteSignal?>

    /**
     * Emits table size changes so consumers can detect shrink/deletion paths that require
     * bounded reconciliation.
     */
    @Query("SELECT COUNT(*) FROM cached_items")
    fun observeCachedItemCount(): Flow<Int>
}
