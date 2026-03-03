package com.mimeo.android.ui.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mimeo.android.AppViewModel
import com.mimeo.android.model.FolderSummary
import com.mimeo.android.model.PlaylistSummary

@Composable
fun CollectionsScreen(
    vm: AppViewModel,
    onOpenPlaylistsManager: () -> Unit,
) {
    val playlists by vm.playlists.collectAsState()
    val folders by vm.folders.collectAsState()
    val playlistFolderAssignments by vm.playlistFolderAssignments.collectAsState()
    val settings by vm.settings.collectAsState()
    var newFolderName by remember { mutableStateOf("") }
    var renameFolderTarget by remember { mutableStateOf<FolderSummary?>(null) }
    var renameFolderText by remember { mutableStateOf("") }
    var deleteFolderTarget by remember { mutableStateOf<FolderSummary?>(null) }
    var folderMenuForId by remember { mutableIntStateOf(-1) }
    var pickerPlaylist by remember { mutableStateOf<PlaylistSummary?>(null) }

    LaunchedEffect(Unit) {
        vm.refreshPlaylists()
        vm.refreshFolders()
    }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Collections", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Library home for playlists, folders, and archive.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Playlists", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onOpenPlaylistsManager) {
                        Text("Manage")
                    }
                }

                TextButton(
                    onClick = { vm.selectPlaylist(null) },
                    enabled = settings.selectedPlaylistId != null,
                ) {
                    Text("Use Smart queue")
                }

                if (playlists.isEmpty()) {
                    Text(
                        text = "No playlists yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        playlists.forEach { playlist ->
                            val selected = settings.selectedPlaylistId == playlist.id
                            val assignedFolderName = playlistFolderAssignments[playlist.id]
                                ?.let { folderId -> folders.firstOrNull { it.id == folderId }?.name }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.selectPlaylist(playlist.id) }
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = buildString {
                                            append("${playlist.entries.size} items")
                                            if (!assignedFolderName.isNullOrBlank()) {
                                                append(" • ")
                                                append(assignedFolderName)
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (selected) {
                                        AssistChip(
                                            onClick = { vm.selectPlaylist(playlist.id) },
                                            label = { Text("Selected") },
                                        )
                                    }
                                    TextButton(onClick = { pickerPlaylist = playlist }) {
                                        Text("Add to folder...")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Folders", style = MaterialTheme.typography.titleMedium)
                    TextButton(
                        onClick = {
                            val trimmed = newFolderName.trim()
                            if (trimmed.isNotEmpty()) {
                                vm.createFolder(trimmed)
                                newFolderName = ""
                            }
                        },
                    ) {
                        Text("New folder")
                    }
                }
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (folders.isEmpty()) {
                    Text(
                        text = "No folders yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    folders.forEach { folder ->
                        val assignedCount = playlistFolderAssignments.values.count { it == folder.id }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "$assignedCount playlists",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Box {
                                IconButton(onClick = { folderMenuForId = folder.id }) {
                                    Icon(
                                        imageVector = Icons.Outlined.MoreVert,
                                        contentDescription = "Folder actions",
                                    )
                                }
                                DropdownMenu(
                                    expanded = folderMenuForId == folder.id,
                                    onDismissRequest = { folderMenuForId = -1 },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        onClick = {
                                            folderMenuForId = -1
                                            renameFolderTarget = folder
                                            renameFolderText = folder.name
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = {
                                            folderMenuForId = -1
                                            deleteFolderTarget = folder
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Archive", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Archive browser (coming soon)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Archive navigation is not wired yet in Android.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    renameFolderTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameFolderTarget = null },
            title = { Text("Rename folder") },
            text = {
                OutlinedTextField(
                    value = renameFolderText,
                    onValueChange = { renameFolderText = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = renameFolderText.trim()
                        if (trimmed.isNotEmpty()) {
                            vm.renameFolder(target.id, trimmed)
                        }
                        renameFolderTarget = null
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameFolderTarget = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    deleteFolderTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteFolderTarget = null },
            title = { Text("Delete folder?") },
            text = { Text("Delete '${target.name}'? Playlists will remain available and become unfiled.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteFolder(target.id)
                        deleteFolderTarget = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteFolderTarget = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    pickerPlaylist?.let { playlist ->
        FolderPickerDialog(
            playlist = playlist,
            folders = folders,
            assignedFolderId = playlistFolderAssignments[playlist.id],
            onDismiss = { pickerPlaylist = null },
            onSelectFolder = { folderId ->
                vm.assignPlaylistToFolder(playlist.id, folderId)
                pickerPlaylist = null
            },
        )
    }
}

@Composable
private fun FolderPickerDialog(
    playlist: PlaylistSummary,
    folders: List<FolderSummary>,
    assignedFolderId: Int?,
    onDismiss: () -> Unit,
    onSelectFolder: (Int?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to folder...") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = playlist.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (folders.isEmpty()) {
                    Text(
                        text = "No folders yet. Create one first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = assignedFolderId != null,
                        onClick = { onSelectFolder(null) },
                    ) {
                        Text("Remove from folder")
                    }
                    folders.forEach { folder ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onSelectFolder(folder.id) },
                        ) {
                            Text(
                                text = if (folder.id == assignedFolderId) {
                                    "${folder.name} (Current)"
                                } else {
                                    folder.name
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}
