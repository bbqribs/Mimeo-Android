package com.mimeo.android.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mimeo.android.model.VisualDensityPreference

data class MimeoDensityTokens(
    val rowPadV: Dp,
    val rowGap: Dp,
    val sectionGap: Dp,
)

val MimeoDensityDefault = MimeoDensityTokens(
    rowPadV = 14.dp,
    rowGap = 4.dp,
    sectionGap = 18.dp,
)

val MimeoDensityCompact = MimeoDensityTokens(
    rowPadV = 10.dp,
    rowGap = 2.dp,
    sectionGap = 14.dp,
)

fun densityTokensFor(preference: VisualDensityPreference): MimeoDensityTokens = when (preference) {
    VisualDensityPreference.DEFAULT -> MimeoDensityDefault
    VisualDensityPreference.COMPACT -> MimeoDensityCompact
}
