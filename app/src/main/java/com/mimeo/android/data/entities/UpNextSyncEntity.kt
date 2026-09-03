package com.mimeo.android.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "up_next_sync_metadata")
data class UpNextSyncEntity(
    @PrimaryKey val id: Int = 1,
    val ownerKey: String,
    val serverIdentity: String,
    val capability: String,
    val serverVersion: Long? = null,
    val serverStructureVersion: Long? = null,
    val serverSessionId: Long? = null,
    val serverPointerVersion: Long? = null,
    @ColumnInfo(defaultValue = "'[]'") val pendingPointerTransitionsJson: String = "[]",
    @ColumnInfo(defaultValue = "'UNKNOWN'") val semanticMoveCapability: String = "UNKNOWN",
    @ColumnInfo(defaultValue = "''") val pendingSemanticMoveJson: String = "",
    @ColumnInfo(defaultValue = "''") val lastSemanticMoveDiagnosticJson: String = "",
    val dirty: Boolean = false,
)
