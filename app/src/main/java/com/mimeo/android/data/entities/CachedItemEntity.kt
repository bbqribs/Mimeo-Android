package com.mimeo.android.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_items")
data class CachedItemEntity(
    @PrimaryKey val itemId: Int,
    val activeContentVersionId: Int?,
    val title: String?,
    val url: String,
    val host: String?,
    val status: String?,
    val wordCount: Int?,
    val text: String,
    val paragraphsJson: String,
    val cachedAt: Long,
)
