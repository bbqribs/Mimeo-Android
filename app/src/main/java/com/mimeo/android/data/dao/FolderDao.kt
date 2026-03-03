package com.mimeo.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mimeo.android.data.entities.FolderEntity
import com.mimeo.android.data.entities.FolderPlaylistEntity

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY createdAt ASC, id ASC")
    suspend fun listFolders(): List<FolderEntity>

    @Insert
    suspend fun insertFolder(entity: FolderEntity): Long

    @Query("UPDATE folders SET name = :name WHERE id = :folderId")
    suspend fun renameFolder(folderId: Int, name: String)

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolder(folderId: Int)

    @Query("SELECT * FROM folder_playlists")
    suspend fun listAssignments(): List<FolderPlaylistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssignment(entity: FolderPlaylistEntity)

    @Query("DELETE FROM folder_playlists WHERE playlistId = :playlistId")
    suspend fun clearAssignment(playlistId: Int)

    @Query("DELETE FROM folder_playlists WHERE folderId = :folderId")
    suspend fun clearAssignmentsForFolder(folderId: Int)
}
