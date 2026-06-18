package com.mimeo.android.ui.bluesky

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import com.mimeo.android.ui.theme.LocalMimeoColorTokens
import com.mimeo.android.ui.theme.LocalMimeoShapeTokens
import com.mimeo.android.ui.theme.LocalMimeoTypographyTokens
import com.mimeo.android.ui.theme.LocalMimeoV1Active

/**
 * Compact "Bluesky post" excerpt shown inside a candidate/link card. It is rendered as its
 * own bordered, tinted box so it reads as a quoted post separate from the card's title and
 * save/open action area. When a safe post link is available the whole box opens it.
 *
 * All content arrives pre-sanitized via [buildBlueskyPostPreview]; this composable performs
 * no further formatting and never receives raw identifiers.
 */
@Composable
internal fun BlueskyPostPreviewCard(
    preview: BlueskyPostPreview,
    modifier: Modifier = Modifier,
) {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    val mTypography = LocalMimeoTypographyTokens.current
    val mShapes = LocalMimeoShapeTokens.current
    val uriHandler = LocalUriHandler.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (!preview.postUrl.isNullOrBlank()) {
                    Modifier.clickable { uriHandler.openUri(preview.postUrl) }
                } else {
                    Modifier
                },
            ),
        shape = if (isV1) mShapes.card else MaterialTheme.shapes.small,
        color = if (isV1) mColors.surfaceHi else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (isV1) mColors.line else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            // Frames the box as a post excerpt rather than a raw data block.
            Text(
                text = "Bluesky post",
                style = if (isV1) mTypography.meta else MaterialTheme.typography.labelSmall,
                color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val attributionLine = listOfNotNull(preview.attribution, preview.timestamp)
            if (attributionLine.isNotEmpty()) {
                Text(
                    text = attributionLine.joinToString(" · "),
                    style = if (isV1) mTypography.meta else MaterialTheme.typography.bodySmall,
                    color = if (isV1) mColors.fg2 else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!preview.snippet.isNullOrBlank()) {
                Text(
                    text = preview.snippet,
                    style = if (isV1) mTypography.body else MaterialTheme.typography.bodyMedium,
                    color = if (isV1) mColors.fg else MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
