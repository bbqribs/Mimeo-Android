package com.mimeo.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemColors
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mimeo.android.model.PlaylistSummary
import com.mimeo.android.model.SmartPlaylistSummary
import com.mimeo.android.ui.common.AuthenticatedIdentityPresentation
import com.mimeo.android.ui.common.passiveVerticalScrollIndicator
import com.mimeo.android.ui.theme.LocalMimeoColorTokens
import com.mimeo.android.ui.theme.LocalMimeoShapeTokens
import com.mimeo.android.ui.theme.LocalMimeoTypographyTokens
import com.mimeo.android.ui.theme.LocalMimeoV1Active
import com.mimeo.android.ui.theme.destinationAccentColor

@Composable
internal fun MimeoDrawerContent(
    drawerItems: List<DrawerDestination>,
    playlists: List<PlaylistSummary>,
    smartPlaylists: List<SmartPlaylistSummary>,
    identity: AuthenticatedIdentityPresentation,
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
    // Expansion is local-only and not persisted: it re-derives from the current
    // destination so opening the drawer on a playlist detail reveals that section.
    val onPlaylistDetail = selectedDrawerRoute.startsWith("playlist/")
    val onSmartPlaylistDetail = selectedDrawerRoute.startsWith("smartPlaylist/")
    var playlistsExpanded by remember(selectedDrawerRoute) { mutableStateOf(onPlaylistDetail) }
    var smartPlaylistsExpanded by remember(selectedDrawerRoute) { mutableStateOf(onSmartPlaylistDetail) }
    val drawerBackground = if (isV1) mColors.surface else MaterialTheme.colorScheme.surface
    // Per-destination accent bases for icon/selected-state tinting. Neutral destinations
    // fall back to the standard foreground so the drawer stays calm and readable.
    val darkDrawerSurface = drawerBackground.luminance() < 0.5f
    val neutralAccent = if (isV1) mColors.fg else MaterialTheme.colorScheme.onSurface
    val primaryAccent = if (isV1) mColors.accent else MaterialTheme.colorScheme.primary
    val playlistsAccent = destinationAccentColor("playlist/", neutralAccent, primaryAccent, darkDrawerSurface, drawerBackground)
    val settingsAccent = destinationAccentColor(ROUTE_SETTINGS, neutralAccent, primaryAccent, darkDrawerSurface, drawerBackground)
    val drawerItemColorsV1 = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = Color.Transparent,
        unselectedContainerColor = Color.Transparent,
        selectedTextColor = if (isV1) mColors.fg else MaterialTheme.colorScheme.onSurface,
        unselectedTextColor = if (isV1) mColors.fg2 else MaterialTheme.colorScheme.onSurface,
        selectedIconColor = if (isV1) mColors.accent else MaterialTheme.colorScheme.primary,
        unselectedIconColor = if (isV1) mColors.accent.copy(alpha = 0.82f) else MaterialTheme.colorScheme.primary,
        selectedBadgeColor = if (isV1) mColors.accent else MaterialTheme.colorScheme.primary,
        unselectedBadgeColor = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val drawerSelectedColorsLegacy = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = Color.Transparent,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        selectedIconColor = MaterialTheme.colorScheme.primary,
        unselectedIconColor = MaterialTheme.colorScheme.primary,
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
        val primaryDrawerItems = drawerItems.filterNot { it.route == ROUTE_BIN }
        val binDestination = drawerItems.firstOrNull { it.route == ROUTE_BIN }
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
                    DrawerAccountIdentityBlock(identity = identity, isV1 = isV1)
                    primaryDrawerItems.forEach { destination ->
                        DrawerDestinationItem(
                            destination = destination,
                            selectedDrawerRoute = selectedDrawerRoute,
                            onClick = { onNavItemClick(destination.route) },
                            isV1 = isV1,
                            rowTextStyle = rowTextStyle,
                            itemShape = mShapes.item,
                            itemColors = drawerItemColorsV1,
                            legacyColors = drawerSelectedColorsLegacy,
                            accentColor = destinationAccentColor(
                                destination.route,
                                neutralAccent,
                                primaryAccent,
                                darkDrawerSurface,
                                drawerBackground,
                            ),
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = if (isV1) mColors.line else MaterialTheme.colorScheme.outlineVariant,
                    )
                    DrawerGroupHeader(
                        title = "Playlists",
                        expanded = playlistsExpanded,
                        onClick = { playlistsExpanded = !playlistsExpanded },
                        isV1 = isV1,
                        textStyle = rowTextStyle,
                        titleColor = playlistsAccent,
                        colors = drawerItemColorsV1,
                        shape = mShapes.item,
                    )
                    if (playlistsExpanded) {
                        playlists.forEach { playlist ->
                            val count = playlist.entries.size
                            val selected = selectedDrawerRoute == "playlist/${playlist.id}"
                            MimeoNavigationDrawerItem(
                                icon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                        contentDescription = null,
                                        tint = if (selected) playlistsAccent else playlistsAccent.copy(alpha = 0.82f),
                                    )
                                },
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
                                            color = playlistsAccent,
                                        )
                                        if (count > 0) {
                                            Text(
                                                text = "($count)",
                                                style = if (isV1) mTypography.meta else MaterialTheme.typography.bodySmall,
                                                color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                selectedRailColor = playlistsAccent,
                            )
                        }
                        MimeoNavigationDrawerItem(
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                )
                            },
                            label = {
                                Text(
                                    text = "New Playlist",
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
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = if (isV1) mColors.line else MaterialTheme.colorScheme.outlineVariant,
                    )
                    DrawerGroupHeader(
                        title = "Smart Playlists",
                        expanded = smartPlaylistsExpanded,
                        onClick = { smartPlaylistsExpanded = !smartPlaylistsExpanded },
                        isV1 = isV1,
                        textStyle = rowTextStyle,
                        titleColor = playlistsAccent,
                        colors = drawerItemColorsV1,
                        shape = mShapes.item,
                    )
                    if (smartPlaylistsExpanded) {
                        smartPlaylists.forEach { playlist ->
                            val selected = selectedDrawerRoute == "smartPlaylist/${playlist.id}"
                            MimeoNavigationDrawerItem(
                                icon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                        contentDescription = null,
                                        tint = if (selected) playlistsAccent else playlistsAccent.copy(alpha = 0.82f),
                                    )
                                },
                                label = {
                                    Column {
                                        Text(
                                            text = playlist.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = rowTextStyle,
                                            color = playlistsAccent,
                                        )
                                        Text(
                                            text = "Live dynamic",
                                            style = if (isV1) mTypography.meta else MaterialTheme.typography.bodySmall,
                                            color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                selectedRailColor = playlistsAccent,
                            )
                        }
                        MimeoNavigationDrawerItem(
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                )
                            },
                            label = {
                                Text(
                                    text = "New Smart Playlist",
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
                    binDestination?.let { destination ->
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = if (isV1) mColors.line else MaterialTheme.colorScheme.outlineVariant,
                        )
                        DrawerDestinationItem(
                            destination = destination,
                            selectedDrawerRoute = selectedDrawerRoute,
                            onClick = { onNavItemClick(destination.route) },
                            isV1 = isV1,
                            rowTextStyle = rowTextStyle,
                            itemShape = mShapes.item,
                            itemColors = drawerItemColorsV1,
                            legacyColors = drawerSelectedColorsLegacy,
                            accentColor = destinationAccentColor(
                                destination.route,
                                neutralAccent,
                                primaryAccent,
                                darkDrawerSurface,
                                drawerBackground,
                            ),
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = if (isV1) mColors.line else MaterialTheme.colorScheme.outlineVariant,
                )
                val settingsSelected = selectedDrawerRoute == ROUTE_SETTINGS
                MimeoNavigationDrawerItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = if (settingsSelected) settingsAccent else settingsAccent.copy(alpha = 0.82f),
                        )
                    },
                    label = {
                        Text(
                            text = "Settings",
                            style = rowTextStyle,
                            color = settingsAccent,
                        )
                    },
                    selected = settingsSelected,
                    onClick = onSettingsClick,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    isV1 = isV1,
                    v1Shape = mShapes.item,
                    v1Colors = drawerItemColorsV1,
                    legacyColors = drawerSelectedColorsLegacy,
                    selectedRailColor = settingsAccent,
                )
            }
        }
    }
}

@Composable
private fun DrawerAccountIdentityBlock(
    identity: AuthenticatedIdentityPresentation,
    isV1: Boolean,
) {
    val mColors = LocalMimeoColorTokens.current
    val mTypography = LocalMimeoTypographyTokens.current
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
        Text(
            text = identity.usernameDisplay,
            style = if (isV1) mTypography.row else MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isV1) mColors.fg else Color.Unspecified,
        )
        Text(
            text = if (identity.isSignedIn) identity.endpointDisplay else identity.authenticationState,
            style = if (isV1) mTypography.meta else MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 10.dp),
            color = if (isV1) mColors.line else MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun DrawerDestinationItem(
    destination: DrawerDestination,
    selectedDrawerRoute: String,
    onClick: () -> Unit,
    isV1: Boolean,
    rowTextStyle: TextStyle,
    itemShape: androidx.compose.ui.graphics.Shape,
    itemColors: NavigationDrawerItemColors,
    legacyColors: NavigationDrawerItemColors,
    accentColor: Color,
) {
    val isUpNext = destination.route == ROUTE_UP_NEXT
    val selected = selectedDrawerRoute == destination.route
    val iconTint = if (selected) accentColor else accentColor.copy(alpha = 0.82f)
    val mColors = LocalMimeoColorTokens.current
    val mTypography = LocalMimeoTypographyTokens.current
    MimeoNavigationDrawerItem(
        icon = { DrawerDestinationIcon(destination.route, iconTint) },
        label = {
            if (destination.subtitle != null) {
                Column {
                    Text(
                        text = destination.label,
                        style = rowTextStyle,
                        color = accentColor,
                    )
                    Text(
                        text = destination.subtitle,
                        style = if (isV1) mTypography.meta else MaterialTheme.typography.bodySmall,
                        color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    text = destination.label,
                    style = rowTextStyle,
                    color = accentColor,
                )
            }
        },
        selected = selected,
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .then(
                if (isUpNext) {
                    Modifier.background(
                        color = accentColor.copy(alpha = 0.055f),
                        shape = itemShape,
                    )
                } else {
                    Modifier
                },
            ),
        isV1 = isV1,
        v1Shape = itemShape,
        v1Colors = itemColors,
        legacyColors = legacyColors,
        selectedRailColor = accentColor,
    )
}

@Composable
private fun DrawerDestinationIcon(route: String, tint: Color) {
    when (route) {
        ROUTE_UP_NEXT -> Icon(
            painter = painterResource(id = R.drawable.msr_chevron_right_24),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(28.dp),
        )

        ROUTE_INBOX -> Icon(
            imageVector = Icons.Default.Inbox,
            contentDescription = null,
            tint = tint,
        )

        ROUTE_FAVORITES -> Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = tint,
        )

        ROUTE_ARCHIVE -> Icon(
            imageVector = Icons.Default.Archive,
            contentDescription = null,
            tint = tint,
        )

        ROUTE_BIN -> Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = null,
            tint = tint,
        )

        ROUTE_SMART_QUEUE -> Icon(
            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
            contentDescription = null,
            tint = tint,
        )

        ROUTE_BLUESKY_BROWSE -> Icon(
            // [tint] resolves to the Bluesky brand blue via the central accent mapping.
            painter = painterResource(id = R.drawable.ic_bluesky_butterfly_24),
            contentDescription = null,
            tint = tint,
        )
    }
}

@Composable
private fun DrawerGroupHeader(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit,
    isV1: Boolean,
    textStyle: TextStyle,
    titleColor: Color,
    colors: NavigationDrawerItemColors,
    shape: androidx.compose.ui.graphics.Shape,
) {
    MimeoNavigationDrawerItem(
        label = {
            Text(
                text = title,
                style = textStyle,
                color = titleColor,
            )
        },
        badge = {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
            )
        },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        isV1 = isV1,
        v1Shape = shape,
        v1Colors = colors,
    )
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
    icon: (@Composable () -> Unit)? = null,
    label: @Composable () -> Unit,
    badge: (@Composable () -> Unit)? = null,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    isV1: Boolean,
    v1Shape: androidx.compose.ui.graphics.Shape,
    v1Colors: NavigationDrawerItemColors,
    legacyColors: NavigationDrawerItemColors? = null,
    selectedRailColor: Color = Color.Transparent,
) {
    val itemModifier = modifier.selectedRail(selected, selectedRailColor)
    val alignedIcon: @Composable () -> Unit = {
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            icon?.invoke()
        }
    }
    if (isV1) {
        NavigationDrawerItem(
            icon = alignedIcon,
            label = label,
            badge = badge,
            selected = selected,
            onClick = onClick,
            modifier = itemModifier,
            shape = v1Shape,
            colors = v1Colors,
        )
    } else if (legacyColors != null) {
        NavigationDrawerItem(
            icon = alignedIcon,
            label = label,
            badge = badge,
            selected = selected,
            onClick = onClick,
            modifier = itemModifier,
            colors = legacyColors,
        )
    } else {
        NavigationDrawerItem(
            icon = alignedIcon,
            label = label,
            badge = badge,
            selected = selected,
            onClick = onClick,
            modifier = itemModifier,
        )
    }
}

private fun Modifier.selectedRail(selected: Boolean, color: Color): Modifier {
    if (!selected || color == Color.Transparent) return this
    return drawBehind {
        val railWidth = 3.dp.toPx()
        val railHeight = size.height * 0.58f
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, (size.height - railHeight) / 2f),
            size = Size(railWidth, railHeight),
            cornerRadius = CornerRadius(railWidth, railWidth),
        )
    }
}
