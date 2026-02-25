package com.mimeo.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mimeo.android.data.entities.PendingProgressEntity

@Dao
interface PendingProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PendingProgressEntity)

    @Query("SELECT * FROM pending_progress ORDER BY createdAt ASC, id ASC")
    suspend fun listPending(): List<PendingProgressEntity>

    @Query("SELECT COUNT(*) FROM pending_progress")
    suspend fun countPending(): Int

    @Query(
        "UPDATE pending_progress SET attemptCount = :attemptCount, lastAttemptAt = :lastAttemptAt, lastError = :lastError WHERE id = :id",
    )
    suspend fun recordAttempt(id: Long, attemptCount: Int, lastAttemptAt: Long, lastError: String?)

    @Query("DELETE FROM pending_progress WHERE id = :id")
    suspend fun deleteById(id: Long)
}
