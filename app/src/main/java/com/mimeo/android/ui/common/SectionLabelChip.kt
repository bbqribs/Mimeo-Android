package com.mimeo.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mimeo.android.ui.theme.LocalMimeoColorTokens
import com.mimeo.android.ui.theme.LocalMimeoDensityTokens
import com.mimeo.android.ui.theme.LocalMimeoTypographyTokens
import com.mimeo.android.ui.theme.LocalMimeoV1Active

/**
 * Bare compact chip used for list section labels — Library temporal groupings
 * and the Up Next Now Playing / Up Next / History / Earlier sections. A faint
 * neutral wash of the foreground colour with accent text, no border. Callers
 * position it; [SectionLabelHeader] wraps it as a full-width section header.
 */
@Composable
fun SectionLabelChip(
    label: String,
    modifier: Modifier = Modifier,
) {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    val mTypography = LocalMimeoTypographyTokens.current
    val chipShape = RoundedCornerShape(999.dp)
    val containerColor = if (isV1) {
        mColors.fg.copy(alpha = 0.10f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    }
    val contentColor = if (isV1) mColors.accent else MaterialTheme.colorScheme.primary
    Text(
        text = label,
        style = if (isV1) mTypography.button else MaterialTheme.typography.labelLarge,
        color = contentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(containerColor, chipShape)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/**
 * Full-width section header that places a [SectionLabelChip] at the start with
 * section-gap-aware top spacing. Used for Library temporal-group headers and the
 * Up Next History / Earlier in queue sections (incl. their sticky overlays).
 */
@Composable
fun SectionLabelHeader(
    label: String,
    modifier: Modifier = Modifier,
) {
    val isV1 = LocalMimeoV1Active.current
    val densityTokens = LocalMimeoDensityTokens.current
    val headerTopPadding = if (isV1) {
        (densityTokens.sectionGap - 12.dp).coerceAtLeast(2.dp)
    } else {
        0.dp
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = headerTopPadding, bottom = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        SectionLabelChip(label = label)
    }
}
