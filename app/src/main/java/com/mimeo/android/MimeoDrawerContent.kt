package com.mimeo.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mimeo.android.model.PlaylistSummary

@Composable
internal fun MimeoDrawerContent(
    drawerItems: List<DrawerDestination>,
    playlists: List<PlaylistSummary>,
    selectedDrawerRoute: String,
    onNavItemClick: (route: String) -> Unit,
    onPlaylistClick: (playlistId: Int) -> Unit,
    onNewPlaylistClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = "Mimeo",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            drawerItems.forEach { destination ->
                NavigationDrawerItem(
                    label = { Text(destination.label) },
                    selected = selectedDrawerRoute == destination.route,
                    onClick = { onNavItemClick(destination.route) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Text(
                text = "Playlists",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
            )
            playlists.forEach { playlist ->
                val count = playlist.entries.size
                NavigationDrawerItem(
                    label = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = playlist.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (count > 0) {
                                Text(
                                    text = "($count)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                    },
                    selected = selectedDrawerRoute == "playlist/${playlist.id}",
                    onClick = { onPlaylistClick(playlist.id) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
            NavigationDrawerItem(
                label = { Text("+ New Playlist") },
                selected = false,
                onClick = onNewPlaylistClick,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            NavigationDrawerItem(
                label = { Text("Settings") },
                selected = selectedDrawerRoute == ROUTE_SETTINGS,
                onClick = onSettingsClick,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}
