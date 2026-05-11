package com.mimeo.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemColors
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mimeo.android.model.PlaylistSummary
import com.mimeo.android.model.SmartPlaylistSummary
import com.mimeo.android.ui.common.passiveVerticalScrollIndicator
import com.mimeo.android.ui.theme.LocalMimeoColorTokens
import com.mimeo.android.ui.theme.LocalMimeoShapeTokens
import com.mimeo.android.ui.theme.LocalMimeoTypographyTokens
import com.mimeo.android.ui.theme.LocalMimeoV1Active

@Composable
internal fun MimeoDrawerContent(
    drawerItems: List<DrawerDestination>,
    playlists: List<PlaylistSummary>,
    smartPlaylists: List<SmartPlaylistSummary>,
    selectedDrawerRoute: String,
    onNavItemClick: (route: String) -> Unit,
    onPlaylistClick: (playlistId: Int) -> Unit,
    onSmartPlaylistClick: (playlistId: Int) -> Unit,
    onNewPlaylistClick: () -> Unit,
    onNewSmartPlaylistClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    val mTypography = LocalMimeoTypographyTokens.current
    val mShapes = LocalMimeoShapeTokens.current
    val drawerBackground = if (isV1) mColors.surface else MaterialTheme.colorScheme.surface
    val drawerItemColorsV1 = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = if (isV1) mColors.accentDim else MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
        unselectedContainerColor = Color.Transparent,
        selectedTextColor = if (isV1) mColors.accent else MaterialTheme.colorScheme.primary,
        unselectedTextColor = if (isV1) mColors.fg2 else MaterialTheme.colorScheme.onSurface,
        selectedIconColor = if (isV1) mColors.accent else MaterialTheme.colorScheme.primary,
        unselectedIconColor = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
        selectedBadgeColor = if (isV1) mColors.accent else MaterialTheme.colorScheme.primary,
        unselectedBadgeColor = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val drawerSelectedColorsLegacy = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
        selectedTextColor = MaterialTheme.colorScheme.primary,
    )
    val drawerActionColorsV1 = NavigationDrawerItemDefaults.colors(
        unselectedContainerColor = Color.Transparent,
        unselectedTextColor = if (isV1) mColors.accent else MaterialTheme.colorScheme.onSurface,
        unselectedIconColor = if (isV1) mColors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val rowTextStyle = if (isV1) mTypography.row else TextStyle.Default
    val buttonTextStyle = if (isV1) mTypography.button else TextStyle.Default
    BoxWithConstraints {
        val drawerWidth = maxWidth * (2f / 3f)
        val drawerScrollState = rememberScrollState()
        MimeoModalDrawerSheet(
            isV1 = isV1,
            modifier = Modifier.width(drawerWidth),
            drawerContainerColor = drawerBackground,
            drawerContentColor = if (isV1) mColors.fg else MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(drawerBackground),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .passiveVerticalScrollIndicator(
                            scrollState = drawerScrollState,
                            color = if (isV1) mColors.fg3.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.26f),
                        )
                        .verticalScroll(drawerScrollState),
                ) {
                    Text(
                        text = "Mimeo",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        style = if (isV1) mTypography.title else MaterialTheme.typography.titleMedium,
                        color = if (isV1) mColors.fg else Color.Unspecified,
                    )
                    drawerItems.forEach { destination ->
                        MimeoNavigationDrawerItem(
                            label = {
                                Text(
                                    text = destination.label,
                                    style = rowTextStyle,
                                )
                            },
                            selected = selectedDrawerRoute == destination.route,
                            onClick = { onNavItemClick(destination.route) },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                            isV1 = isV1,
                            v1Shape = mShapes.item,
                            v1Colors = drawerItemColorsV1,
                            legacyColors = drawerSelectedColorsLegacy,
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = if (isV1) mColors.line else MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(
                        text = "Playlists",
                        style = if (isV1) mTypography.section else MaterialTheme.typography.labelMedium,
                        color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
                    )
                    playlists.forEach { playlist ->
                        val count = playlist.entries.size
                        val selected = selectedDrawerRoute == "playlist/${playlist.id}"
                        MimeoNavigationDrawerItem(
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
                                        style = rowTextStyle,
                                    )
                                    if (count > 0) {
                                        Text(
                                            text = "($count)",
                                            style = if (isV1) mTypography.meta else MaterialTheme.typography.bodySmall,
                                            color = if (isV1 && selected) mColors.accent else if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 4.dp),
                                        )
                                    }
                                }
                            },
                            selected = selected,
                            onClick = { onPlaylistClick(playlist.id) },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                            isV1 = isV1,
                            v1Shape = mShapes.item,
                            v1Colors = drawerItemColorsV1,
                            legacyColors = drawerSelectedColorsLegacy,
                        )
                    }
                    if (smartPlaylists.isNotEmpty()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = if (isV1) mColors.line else MaterialTheme.colorScheme.outlineVariant,
                        )
                        Text(
                            text = "Smart Playlists",
                            style = if (isV1) mTypography.section else MaterialTheme.typography.labelMedium,
                            color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
                        )
                        smartPlaylists.forEach { playlist ->
                            val selected = selectedDrawerRoute == "smartPlaylist/${playlist.id}"
                            MimeoNavigationDrawerItem(
                                label = {
                                    Column {
                                        Text(
                                            text = playlist.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = rowTextStyle,
                                        )
                                        Text(
                                            text = "Live dynamic",
                                            style = if (isV1) mTypography.meta else MaterialTheme.typography.bodySmall,
                                            color = if (isV1 && selected) mColors.accent else if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                selected = selected,
                                onClick = { onSmartPlaylistClick(playlist.id) },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                isV1 = isV1,
                                v1Shape = mShapes.item,
                                v1Colors = drawerItemColorsV1,
                                legacyColors = drawerSelectedColorsLegacy,
                            )
                        }
                    }
                    MimeoNavigationDrawerItem(
                        label = {
                            Text(
                                text = "+ New Playlist",
                                style = buttonTextStyle,
                            )
                        },
                        selected = false,
                        onClick = onNewPlaylistClick,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        isV1 = isV1,
                        v1Shape = mShapes.item,
                        v1Colors = drawerActionColorsV1,
                    )
                    MimeoNavigationDrawerItem(
                        label = {
                            Text(
                                text = "+ New Smart Playlist",
                                style = buttonTextStyle,
                            )
                        },
                        selected = false,
                        onClick = onNewSmartPlaylistClick,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        isV1 = isV1,
                        v1Shape = mShapes.item,
                        v1Colors = drawerActionColorsV1,
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = if (isV1) mColors.line else MaterialTheme.colorScheme.outlineVariant,
                )
                MimeoNavigationDrawerItem(
                    label = {
                        Text(
                            text = "Settings",
                            style = rowTextStyle,
                        )
                    },
                    selected = selectedDrawerRoute == ROUTE_SETTINGS,
                    onClick = onSettingsClick,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    isV1 = isV1,
                    v1Shape = mShapes.item,
                    v1Colors = drawerItemColorsV1,
                    legacyColors = drawerSelectedColorsLegacy,
                )
            }
        }
    }
}

@Composable
private fun MimeoModalDrawerSheet(
    isV1: Boolean,
    modifier: Modifier,
    drawerContainerColor: Color,
    drawerContentColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (isV1) {
        ModalDrawerSheet(
            modifier = modifier,
            drawerContainerColor = drawerContainerColor,
            drawerContentColor = drawerContentColor,
            content = content,
        )
    } else {
        ModalDrawerSheet(
            modifier = modifier,
            content = content,
        )
    }
}

@Composable
private fun MimeoNavigationDrawerItem(
    label: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    isV1: Boolean,
    v1Shape: androidx.compose.ui.graphics.Shape,
    v1Colors: NavigationDrawerItemColors,
    legacyColors: NavigationDrawerItemColors? = null,
) {
    if (isV1) {
        NavigationDrawerItem(
            label = label,
            selected = selected,
            onClick = onClick,
            modifier = modifier,
            shape = v1Shape,
            colors = v1Colors,
        )
    } else if (legacyColors != null) {
        NavigationDrawerItem(
            label = label,
            selected = selected,
            onClick = onClick,
            modifier = modifier,
            colors = legacyColors,
        )
    } else {
        NavigationDrawerItem(
            label = label,
            selected = selected,
            onClick = onClick,
            modifier = modifier,
        )
    }
}
