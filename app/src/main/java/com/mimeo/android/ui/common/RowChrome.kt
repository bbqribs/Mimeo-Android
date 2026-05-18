package com.mimeo.android.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mimeo.android.ui.theme.LocalMimeoColorTokens
import com.mimeo.android.ui.theme.LocalMimeoV1Active

@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    HorizontalDivider(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .then(modifier),
        color = if (isV1) {
            mColors.line
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        },
    )
}

@Composable
fun rowDragContainerColor(): Color {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    return if (isV1) mColors.surfaceHi else MaterialTheme.colorScheme.surfaceContainerHigh
}

@Composable
fun dragContainerColorFor(isDragging: Boolean): Color? =
    if (isDragging) rowDragContainerColor() else null

fun buildItemMetadata(
    source: String?,
    showArchived: Boolean = false,
): String? = listOfNotNull(
    source?.takeIf { it.isNotBlank() },
    "Archived".takeIf { showArchived },
).joinToString(" · ").takeIf { it.isNotBlank() }
