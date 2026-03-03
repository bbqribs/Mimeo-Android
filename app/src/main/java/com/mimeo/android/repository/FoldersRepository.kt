package com.mimeo.android.repository

import com.mimeo.android.data.AppDatabase
import com.mimeo.android.data.entities.FolderEntity
import com.mimeo.android.data.entities.FolderPlaylistEntity
import com.mimeo.android.model.FolderSummary

class FoldersRepository(
    private val database: AppDatabase,
) {
    suspend fun listFolders(): List<FolderSummary> {
        return database.folderDao().listFolders().map { it.toSummary() }
    }

    suspend fun listPlaylistAssignments(): Map<Int, Int> {
        return database.folderDao()
            .listAssignments()
            .associate { it.playlistId to it.folderId }
    }

    suspend fun createFolder(name: String): FolderSummary {
        val createdAt = System.currentTimeMillis()
        val folderId = database.folderDao().insertFolder(
            FolderEntity(
                name = name,
                createdAt = createdAt,
            ),
        ).toInt()
        return FolderSummary(
            id = folderId,
            name = name,
            createdAt = createdAt,
        )
    }

    suspend fun renameFolder(folderId: Int, name: String) {
        database.folderDao().renameFolder(folderId, name)
    }

    suspend fun deleteFolder(folderId: Int) {
        database.folderDao().clearAssignmentsForFolder(folderId)
        database.folderDao().deleteFolder(folderId)
    }

    suspend fun assignPlaylistToFolder(playlistId: Int, folderId: Int?) {
        if (folderId == null) {
            database.folderDao().clearAssignment(playlistId)
            return
        }
        database.folderDao().upsertAssignment(
            FolderPlaylistEntity(
                playlistId = playlistId,
                folderId = folderId,
            ),
        )
    }

    private fun FolderEntity.toSummary(): FolderSummary {
        return FolderSummary(
            id = id,
            name = name,
            createdAt = createdAt,
        )
    }
}
