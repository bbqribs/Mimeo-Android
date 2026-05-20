package com.mimeo.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mimeo.android.ui.theme.LocalMimeoAccentScheme
import com.mimeo.android.ui.theme.LocalMimeoColorTokens
import com.mimeo.android.ui.theme.LocalMimeoDensityTokens
import com.mimeo.android.ui.theme.LocalMimeoTypographyTokens
import com.mimeo.android.ui.theme.LocalMimeoV1Active
import com.mimeo.android.ui.theme.MimeoThemeChoice
import com.mimeo.android.ui.theme.accentTokensFor

/**
 * Bare compact accent-on-dark chip used for list section labels — Library
 * temporal groupings and the Up Next Now Playing / Up Next / History / Earlier
 * sections. Callers position it; [SectionLabelHeader] wraps it as a full-width
 * section header.
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
    // Theme-aware dark chip: a dark fill in both themes — near-black ink in light
    // mode, an elevated grey that contrasts the panel in dark mode — paired with
    // the bright dark-scheme accent for text, since the light-scheme accent is
    // too dark to read on a dark fill.
    val isLightTheme = mColors.fg.luminance() < 0.5f
    val chipAccent = accentTokensFor(LocalMimeoAccentScheme.current, MimeoThemeChoice.DARK).accent
    val containerColor = if (isV1) {
        if (isLightTheme) mColors.fg else mColors.fg4
    } else {
        MaterialTheme.colorScheme.inverseSurface
    }
    val contentColor = if (isV1) chipAccent else MaterialTheme.colorScheme.inversePrimary
    Text(
        text = label,
        style = if (isV1) mTypography.button else MaterialTheme.typography.labelLarge,
        color = contentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(containerColor, chipShape)
            .border(1.dp, contentColor, chipShape)
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
