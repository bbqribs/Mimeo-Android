package com.mimeo.android.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimeo.android.ui.theme.LocalMimeoColorTokens
import com.mimeo.android.ui.theme.LocalMimeoDensityTokens
import com.mimeo.android.ui.theme.LocalMimeoTypographyTokens
import com.mimeo.android.ui.theme.LocalMimeoV1Active

@Composable
fun ListSurfaceScaffold(
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    selectionBar: (@Composable () -> Unit)? = null,
    controls: (@Composable () -> Unit)? = null,
    loading: Boolean,
    empty: Boolean,
    loadingContent: (@Composable () -> Unit)? = { DefaultListSurfaceMessage("Loading...") },
    emptyContent: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        header?.invoke()
        if (selectionBar != null) {
            selectionBar()
        } else {
            controls?.invoke()
        }

        when {
            loading && empty && loadingContent != null -> loadingContent()
            empty -> emptyContent()
            else -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
            ) {
                body()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryItemRow(
    title: String,
    metadata: String?,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    containerColor: Color? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    titleMaxLines: Int? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    progressStateLine: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    val mTypography = LocalMimeoTypographyTokens.current
    val densityTokens = LocalMimeoDensityTokens.current
    val readerClickLabel = "Opens $title in reader"
    val baseStartPadding = if (isV1) 12.dp else 8.dp
    val startPadding = if (leadingContent != null) {
        (baseStartPadding - 4.dp).coerceAtLeast(4.dp)
    } else {
        baseStartPadding
    }
    val resolvedTitleMaxLines = titleMaxLines ?: densityTokens.itemRowTitleMaxLines
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                when {
                    isSelected -> Modifier.background(
                        if (isV1) mColors.accentDim else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    )
                    containerColor != null -> Modifier.background(containerColor)
                    else -> Modifier
                },
            )
            .combinedClickable(
                onClick = onClick,
                onClickLabel = readerClickLabel,
                onLongClick = onLongClick,
            )
            .padding(
                start = startPadding,
                end = 2.dp,
                top = if (isV1) densityTokens.rowPadV else 8.dp,
                bottom = if (isV1) densityTokens.rowPadV else 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        leadingContent?.invoke(this)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(if (isV1) densityTokens.rowGap else 2.dp),
        ) {
            Text(
                text = title,
                style = if (isV1) mTypography.row else MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = if (isV1) mColors.fg else titleColor,
                maxLines = resolvedTitleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (!metadata.isNullOrBlank()) {
                Text(
                    text = metadata,
                    style = if (isV1) mTypography.meta else MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            progressStateLine?.invoke()
        }
        trailingContent?.invoke(this)
    }
}

@Composable
fun SelectionAffordance(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    selectedContentDescription: String = "Selected",
    unselectedContentDescription: String = "Not selected",
) {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    androidx.compose.material3.Icon(
        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
        contentDescription = if (isSelected) selectedContentDescription else unselectedContentDescription,
        tint = if (isSelected) {
            if (isV1) mColors.accent else MaterialTheme.colorScheme.primary
        } else {
            if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier,
    )
}

@Composable
fun ListStatusPill(status: String) {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    val (label, containerColor, contentColor) = when (status) {
        "extracting", "saved" -> Triple(
            "Extracting",
            if (isV1) mColors.surface else MaterialTheme.colorScheme.surfaceVariant,
            if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        "failed" -> Triple(
            "Failed",
            if (isV1) mColors.danger.copy(alpha = 0.18f) else MaterialTheme.colorScheme.errorContainer,
            if (isV1) mColors.danger else MaterialTheme.colorScheme.onErrorContainer,
        )
        "blocked" -> Triple(
            "Blocked",
            if (isV1) mColors.warn.copy(alpha = 0.18f) else MaterialTheme.colorScheme.tertiaryContainer,
            if (isV1) mColors.warn else MaterialTheme.colorScheme.onTertiaryContainer,
        )
        else -> return
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(containerColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
    }
}

@Composable
fun DefaultListSurfaceMessage(text: String) {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    val mTypography = LocalMimeoTypographyTokens.current
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        style = if (isV1) mTypography.body else MaterialTheme.typography.bodyMedium,
        color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
