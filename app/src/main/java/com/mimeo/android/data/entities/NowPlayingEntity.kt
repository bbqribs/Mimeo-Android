package com.mimeo.android.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "now_playing")
data class NowPlayingEntity(
    @PrimaryKey val id: Int = 1,
    val queueJson: String,
    val currentIndex: Int,
    val updatedAt: Long,
)
