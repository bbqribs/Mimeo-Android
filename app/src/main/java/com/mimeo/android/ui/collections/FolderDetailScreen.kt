package com.mimeo.android.ui.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mimeo.android.AppViewModel

@Composable
fun FolderDetailScreen(
    vm: AppViewModel,
    folderId: Int,
    onBack: () -> Unit,
    onOpenPlaylist: () -> Unit,
) {
    val folders by vm.folders.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val playlistFolderAssignments by vm.playlistFolderAssignments.collectAsState()
    val folder = folders.firstOrNull { it.id == folderId }
    val assignedPlaylists = playlists.filter { playlistFolderAssignments[it.id] == folderId }

    LaunchedEffect(folderId) {
        vm.refreshPlaylists()
        vm.refreshFolders()
    }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = folder?.name ?: "Folder",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "${assignedPlaylists.size} playlists",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onBack) {
                Text("Collections")
            }
        }

        if (folder == null) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "This folder no longer exists.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Column
        }

        if (assignedPlaylists.isEmpty()) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("No playlists in this folder yet.", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Use Collections to assign playlists to ${folder.name}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            assignedPlaylists.forEach { playlist ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
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
                                text = "${playlist.entries.size} items",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = {
                                    vm.selectPlaylist(playlist.id)
                                    onOpenPlaylist()
                                },
                            ) {
                                Text("Open")
                            }
                            TextButton(
                                onClick = {
                                    vm.assignPlaylistToFolder(playlist.id, null)
                                    vm.showSnackbar("Removed from ${folder.name}")
                                },
                            ) {
                                Text("Remove from folder")
                            }
                        }
                    }
                }
            }
        }
    }
}
