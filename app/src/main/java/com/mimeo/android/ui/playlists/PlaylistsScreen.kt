package com.mimeo.android.ui.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mimeo.android.AppViewModel
import com.mimeo.android.model.PlaylistSummary

data class PlaylistPickerChoice(
    val playlistId: Int,
    val playlistName: String,
    val isMember: Boolean,
)

@Composable
fun PlaylistsScreen(vm: AppViewModel) {
    val playlists by vm.playlists.collectAsState()
    val settings by vm.settings.collectAsState()
    var newName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<PlaylistSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<PlaylistSummary?>(null) }
    var renameText by remember { mutableStateOf("") }
    var menuForPlaylistId by remember { mutableIntStateOf(-1) }

    LaunchedEffect(Unit) {
        vm.refreshPlaylists()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New playlist") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            TextButton(
                onClick = {
                    val trimmed = newName.trim()
                    if (trimmed.isNotEmpty()) {
                        vm.createPlaylist(trimmed)
                        newName = ""
                    }
                },
            ) {
                Text("Add")
            }
            TextButton(onClick = { vm.refreshPlaylists() }) { Text("Refresh") }
        }

        TextButton(
            onClick = { vm.selectPlaylist(null) },
            enabled = settings.selectedPlaylistId != null,
        ) {
            Text("Use Smart queue")
        }

        if (playlists.isEmpty()) {
            Text("No playlists yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    val isSelected = settings.selectedPlaylistId == playlist.id
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                modifier = Modifier.weight(1f),
                                text = if (isSelected) "${playlist.name} (selected)" else playlist.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Box {
                                TextButton(onClick = { menuForPlaylistId = playlist.id }) {
                                    Text("Menu")
                                }
                                DropdownMenu(
                                    expanded = menuForPlaylistId == playlist.id,
                                    onDismissRequest = { menuForPlaylistId = -1 },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Use") },
                                        onClick = {
                                            menuForPlaylistId = -1
                                            vm.selectPlaylist(playlist.id)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        onClick = {
                                            menuForPlaylistId = -1
                                            renameTarget = playlist
                                            renameText = playlist.name
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = {
                                            menuForPlaylistId = -1
                                            deleteTarget = playlist
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename playlist") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = renameText.trim()
                        if (trimmed.isNotEmpty()) {
                            vm.renamePlaylist(target.id, trimmed)
                        }
                        renameTarget = null
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete playlist?") },
            text = { Text("Delete '${target.name}' and remove its item links.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deletePlaylist(target.id)
                        deleteTarget = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
fun PlaylistPickerDialog(
    itemTitle: String,
    playlistChoices: List<PlaylistPickerChoice>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onTogglePlaylist: (PlaylistPickerChoice) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playlists...") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = itemTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isLoading) {
                    Text("Loading playlists...")
                } else if (playlistChoices.isEmpty()) {
                    Text("No named playlists yet. Create one in Playlists first.")
                } else {
                    playlistChoices.forEach { playlist ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onTogglePlaylist(playlist) },
                        ) {
                            val action = if (playlist.isMember) "Remove from" else "Add to"
                            Text(
                                text = "$action ${playlist.playlistName}",
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
