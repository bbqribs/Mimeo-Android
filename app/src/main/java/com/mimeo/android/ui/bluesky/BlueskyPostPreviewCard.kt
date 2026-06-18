package com.mimeo.android.ui.bluesky

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalUriHandler
import com.mimeo.android.ui.theme.LocalMimeoColorTokens

/**
 * Compact Bluesky post excerpt shown inside a candidate/link card. The styling mirrors how
 * Bluesky renders a post: a hairline-bordered box on the Bluesky canvas colour, left-aligned
 * content, a semibold display name with a muted handle/timestamp header, and post text in the
 * Bluesky body size/line-height. Colours are taken from Bluesky's ALF design tokens (light
 * theme + the "dim" dark theme) and chosen per the active Mimeo theme's background luminance,
 * so the box reads as a genuine Bluesky post in both light and dark mode.
 *
 * All content arrives pre-sanitized via [buildBlueskyPostPreview]; this composable performs
 * no further formatting and never receives raw identifiers. When a safe post link is
 * available, the whole box opens it.
 */
@Composable
internal fun BlueskyPostPreviewCard(
    preview: BlueskyPostPreview,
    modifier: Modifier = Modifier,
) {
    val darkTheme = LocalMimeoColorTokens.current.bg.luminance() < 0.5f
    val palette = if (darkTheme) BlueskyPostPalette.Dark else BlueskyPostPalette.Light
    val uriHandler = LocalUriHandler.current
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.background)
            .border(1.dp, palette.border, shape)
            .then(
                if (!preview.postUrl.isNullOrBlank()) {
                    Modifier.clickable { uriHandler.openUri(preview.postUrl) }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        val header = buildAnnotatedString {
            var hasContent = false
            preview.displayName?.let { name ->
                // Bluesky renders the display name bold in the primary text colour.
                withStyle(SpanStyle(color = palette.text, fontWeight = FontWeight.SemiBold)) {
                    append(name)
                }
                hasContent = true
            }
            preview.handle?.let { handle ->
                if (hasContent) append(" ")
                withStyle(SpanStyle(color = palette.muted)) { append("@$handle") }
                hasContent = true
            }
            preview.timestamp?.let { time ->
                // Handle/timestamp share Bluesky's muted contrast colour; separated by a dot.
                withStyle(SpanStyle(color = palette.muted)) {
                    append(if (hasContent) "  ·  $time" else time)
                }
            }
        }
        if (header.isNotEmpty()) {
            Text(
                text = header,
                style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!preview.snippet.isNullOrBlank()) {
            Text(
                text = preview.snippet,
                // Bluesky body text: 15sp at the "snug" 1.3 line-height (~20sp).
                style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
                color = palette.text,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Bluesky's post colours, lifted from the ALF design tokens. [Light] is the default light
 * theme; [Dark] is Bluesky's true-black "Dark" theme (its other dark theme, "dim", uses a
 * lighter blue-grey canvas — the true-black theme is the closer match for Mimeo's near-black
 * dark surfaces). Values are the resolved tokens: background = canvas (trueBlack in dark),
 * border = a subtle contrast hairline, text = contrast_900, muted = contrast_500.
 */
private data class BlueskyPostPalette(
    val background: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
) {
    companion object {
        val Light = BlueskyPostPalette(
            background = Color(0xFFFFFFFF),
            border = Color(0xFFD4DBE2),
            text = Color(0xFF253342),
            muted = Color(0xFF6F869F),
        )
        val Dark = BlueskyPostPalette(
            background = Color(0xFF000000),
            border = Color(0xFF1C2732),
            text = Color(0xFFD4DBE2),
            muted = Color(0xFF6F869F),
        )

        /** Bluesky brand link blue (primary_500); reserved for inline links/mentions. */
        @Suppress("unused")
        val Link = Color(0xFF1083FE)
    }
}
