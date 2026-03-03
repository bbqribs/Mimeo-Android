package com.mimeo.android.ui.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
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
fun CollectionsScreen(
    vm: AppViewModel,
    onOpenPlaylistsManager: () -> Unit,
) {
    val playlists by vm.playlists.collectAsState()
    val settings by vm.settings.collectAsState()

    LaunchedEffect(Unit) {
        vm.refreshPlaylists()
    }

    Column(
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
                    playlists.forEach { playlist ->
                        val selected = settings.selectedPlaylistId == playlist.id
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
                                    text = "${playlist.entries.size} items",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (selected) {
                                AssistChip(
                                    onClick = { vm.selectPlaylist(playlist.id) },
                                    label = { Text("Selected") },
                                )
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
                Text("Folders", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Folders (coming soon)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Phase 6.2 will add nested folders and moving items between folders.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
}
