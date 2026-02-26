package com.mimeo.android.ui.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mimeo.android.AppViewModel
import com.mimeo.android.model.PlaylistSummary

@Composable
fun PlaylistsScreen(vm: AppViewModel) {
    val playlists by vm.playlists.collectAsState()
    val settings by vm.settings.collectAsState()
    var newName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<PlaylistSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<PlaylistSummary?>(null) }
    var renameText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        vm.refreshPlaylists()
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Playlists")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New playlist") },
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    val trimmed = newName.trim()
                    if (trimmed.isNotEmpty()) {
                        vm.createPlaylist(trimmed)
                        newName = ""
                    }
                },
            ) {
                Text("Create")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.selectPlaylist(null) },
                enabled = settings.selectedPlaylistId != null,
            ) {
                Text("Use Smart queue")
            }
            Button(onClick = { vm.refreshPlaylists() }) {
                Text("Refresh")
            }
        }

        if (playlists.isEmpty()) {
            Text("No playlists yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    val isSelected = settings.selectedPlaylistId == playlist.id
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = if (isSelected) "${playlist.name} (selected)" else playlist.name,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { vm.selectPlaylist(playlist.id) },
                                enabled = !isSelected,
                            ) {
                                Text("Use")
                            }
                            Button(
                                onClick = {
                                    renameTarget = playlist
                                    renameText = playlist.name
                                },
                            ) {
                                Text("Rename")
                            }
                            Button(
                                onClick = { deleteTarget = playlist },
                            ) {
                                Text("Delete")
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
    title: String,
    itemId: Int,
    playlists: List<PlaylistSummary>,
    pendingPlaylistId: Int?,
    membershipFor: (playlistId: Int, itemId: Int) -> Boolean?,
    onToggle: (PlaylistSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (playlists.isEmpty()) {
                    Text("No playlists yet. Create one in Playlists.")
                } else {
                    playlists.forEach { playlist ->
                        val member = membershipFor(playlist.id, itemId)
                        val labelSuffix = when (member) {
                            true -> " (in playlist)"
                            else -> ""
                        }
                        val isPending = pendingPlaylistId == playlist.id
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onToggle(playlist) },
                            enabled = !isPending,
                        ) {
                            Text("${playlist.name}$labelSuffix")
                            if (isPending) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
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
