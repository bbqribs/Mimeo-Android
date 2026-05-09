package com.mimeo.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

data class MimeoShapeTokens(
    val none: Shape,
    val input: Shape,
    val item: Shape,
    val card: Shape,
    val sheet: Shape,
    val pill: Shape,
)

val MimeoShapes = MimeoShapeTokens(
    none = RoundedCornerShape(0.dp),
    input = RoundedCornerShape(8.dp),
    item = RoundedCornerShape(10.dp),
    card = RoundedCornerShape(12.dp),
    sheet = RoundedCornerShape(16.dp),
    pill = RoundedCornerShape(percent = 50),
)
