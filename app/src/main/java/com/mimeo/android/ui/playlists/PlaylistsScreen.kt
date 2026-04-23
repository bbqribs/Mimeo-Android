package com.mimeo.android.ui.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mimeo.android.model.PlaylistSummary

data class PlaylistPickerChoice(
    val playlistId: Int,
    val playlistName: String,
    val isMember: Boolean,
)

@Composable
fun BatchPlaylistPickerDialog(
    itemCount: Int,
    playlists: List<PlaylistSummary>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (PlaylistSummary) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add $itemCount item${if (itemCount == 1) "" else "s"} to…") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (playlists.isEmpty()) {
                    Text("No named playlists yet. Create one in Playlists first.")
                } else {
                    playlists.forEach { playlist ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onSelectPlaylist(playlist) },
                        ) {
                            Text(
                                text = playlist.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
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
