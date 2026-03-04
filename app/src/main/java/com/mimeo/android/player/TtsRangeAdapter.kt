package com.mimeo.android.player

fun normalizeActiveChunkRange(
    textLength: Int,
    baseOffset: Int,
    start: Int,
    endExclusive: Int,
): IntRange? {
    if (textLength <= 0) return null

    val safeStart = (baseOffset + start).coerceIn(0, textLength)
    val safeEndExclusive = (baseOffset + endExclusive).coerceIn(0, textLength)
    if (safeEndExclusive <= safeStart) return null
    return safeStart until safeEndExclusive
}
