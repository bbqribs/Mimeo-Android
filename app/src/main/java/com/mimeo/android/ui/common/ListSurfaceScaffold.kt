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
            else -> body()
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
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    progressStateLine: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val readerClickLabel = "Opens $title in reader"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                when {
                    isSelected -> Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    containerColor != null -> Modifier.background(containerColor)
                    else -> Modifier
                },
            )
            .combinedClickable(
                onClick = onClick,
                onClickLabel = readerClickLabel,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leadingContent?.invoke(this)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!metadata.isNullOrBlank()) {
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    androidx.compose.material3.Icon(
        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
        contentDescription = if (isSelected) selectedContentDescription else unselectedContentDescription,
        tint = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier,
    )
}

@Composable
fun ListStatusPill(status: String) {
    val (label, containerColor, contentColor) = when (status) {
        "extracting", "saved" -> Triple(
            "Extracting",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        "failed" -> Triple(
            "Failed",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        "blocked" -> Triple(
            "Blocked",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
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
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
