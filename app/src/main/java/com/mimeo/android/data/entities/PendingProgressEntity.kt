package com.mimeo.android.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_progress",
    indices = [Index(value = ["itemId"], unique = true)],
)
data class PendingProgressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Int,
    val percent: Int,
    val createdAt: Long,
    val attemptCount: Int,
    val lastAttemptAt: Long?,
    val lastError: String?,
)
