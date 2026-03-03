package com.mimeo.android.data.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "folder_playlists",
    primaryKeys = ["playlistId"],
    indices = [Index("folderId")],
)
data class FolderPlaylistEntity(
    val playlistId: Int,
    val folderId: Int,
)
