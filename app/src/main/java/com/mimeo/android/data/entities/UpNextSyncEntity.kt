package com.mimeo.android.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "up_next_sync_metadata")
data class UpNextSyncEntity(
    @PrimaryKey val id: Int = 1,
    val ownerKey: String,
    val serverIdentity: String,
    val capability: String,
    val serverVersion: Long? = null,
    val dirty: Boolean = false,
)
