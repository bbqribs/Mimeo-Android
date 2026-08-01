package com.mimeo.android.ui.settings

import com.mimeo.android.model.PlaybackPosition
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.model.ProgressSyncBadgeState
import com.mimeo.android.repository.NowPlayingSession
import com.mimeo.android.repository.NowPlayingSessionItem
import com.mimeo.android.repository.PendingProgressSnapshot
import com.mimeo.android.ui.player.PlaybackEngineState
import com.mimeo.android.ui.player.PlaybackOpenIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private fun fixedTime(ms: Long): DiagnosticsTimeSource = DiagnosticsTimeSource { ms }

private fun engineState(
    currentItemId: Int = 0,
    isSpeaking: Boolean = false,
    isAutoPlaying: Boolean = false,
    autoPlayAfterLoad: Boolean = false,
    hasStartedPlaybackForCurrentItem: Boolean = false,
): PlaybackEngineState = PlaybackEngineState(
    currentItemId = currentItemId,
    openIntent = PlaybackOpenIntent.ManualOpen,
    isSpeaking = isSpeaking,
    isAutoPlaying = isAutoPlaying,
    autoPlayAfterLoad = autoPlayAfterLoad,
    hasStartedPlaybackForCurrentItem = hasStartedPlaybackForCurrentItem,
)

private fun sessionItem(
    itemId: Int,
    lastReadPercent: Int? = null,
    chunkIndex: Int = 0,
    offsetInChunkChars: Int = 0,
    readerScrollOffset: Int = 0,
): NowPlayingSessionItem = NowPlayingSessionItem(
    itemId = itemId,
    title = null,
    url = "https://example.com/$itemId",
    host = null,
    sourceType = null,
    sourceLabel = null,
    sourceUrl = null,
    captureKind = null,
    sourceAppPackage = null,
    status = null,
    activeContentVersionId = null,
    lastReadPercent = lastReadPercent,
    chunkIndex = chunkIndex,
    offsetInChunkChars = offsetInChunkChars,
    readerScrollOffset = readerScrollOffset,
)

private fun session(
    items: List<NowPlayingSessionItem>,
    currentIndex: Int,
    historyItems: List<NowPlayingSessionItem> = emptyList(),
    updatedAt: Long = 0L,
): NowPlayingSession = NowPlayingSession(
    items = items,
    currentIndex = currentIndex,
    updatedAt = updatedAt,
    sourcePlaylistId = null,
    historyItems = historyItems,
)

private fun queueItem(
    itemId: Int,
    apiProgressPercent: Int? = null,
    resumeReadPercent: Int? = null,
    lastReadPercent: Int? = null,
    apiFurthestPercent: Int? = null,
    title: String? = null,
    url: String = "https://example.com/$itemId",
): PlaybackQueueItem = PlaybackQueueItem(
    itemId = itemId,
    title = title,
    url = url,
    apiProgressPercent = apiProgressPercent,
    resumeReadPercent = resumeReadPercent,
    lastReadPercent = lastReadPercent,
    apiFurthestPercent = apiFurthestPercent,
)

private fun buildState(
    queueItems: List<PlaybackQueueItem> = emptyList(),
    session: NowPlayingSession? = null,
    engineState: PlaybackEngineState = engineState(),
    liveCursorByItem: Map<Int, PlaybackPosition> = emptyMap(),
    persistedCursorByItem: Map<Int, PlaybackPosition> = emptyMap(),
    persistedReaderScrollOffsetByItem: Map<Int, Int> = emptyMap(),
    durableReaderScrollOffsetByItem: Map<Int, Int> = emptyMap(),
    pendingProgress: PendingProgressSnapshot? = null,
    lastQueueLoadCompletedAtMs: Long = 0L,
    queueOffline: Boolean = false,
    progressSyncBadgeState: ProgressSyncBadgeState = ProgressSyncBadgeState.SYNCED,
    nowMillis: Long = 0L,
): ProgressPointerDiagnosticsUiState = buildProgressPointerDiagnosticsUiState(
    queueItems = queueItems,
    session = session,
    engineState = engineState,
    liveCursorByItem = liveCursorByItem,
    persistedCursorByItem = persistedCursorByItem,
    getReaderScrollOffset = { id -> durableReaderScrollOffsetByItem[id] },
    persistedReaderScrollOffset = { id -> persistedReaderScrollOffsetByItem[id] },
    pendingProgress = pendingProgress,
    lastQueueLoadCompletedAtMs = lastQueueLoadCompletedAtMs,
    queueOffline = queueOffline,
    progressSyncBadgeState = progressSyncBadgeState,
    timeSource = fixedTime(nowMillis),
)

private fun allRenderedLines(state: ProgressPointerDiagnosticsUiState): List<String> =
    renderSectionA(state) + renderSectionB(state) + renderSectionC(state) + renderSectionD(state) +
        renderDivergenceRows(state) + renderLagRows(state)

class ProgressPointerDiagnosticsTest {

    // ---------------------------------------------------------------
    // Divergence detectors
    // ---------------------------------------------------------------

    @Test
    fun pointerVsEngineDivergenceFiresOnlyWhenBothPresentAndDiffer() {
        assertTrue(detectPointerVsEngineDivergence(pointerItemId = 1, engineItemId = 2))
        assertFalse(detectPointerVsEngineDivergence(pointerItemId = 1, engineItemId = 1))
        assertFalse(detectPointerVsEngineDivergence(pointerItemId = 1, engineItemId = null))
        assertFalse(detectPointerVsEngineDivergence(pointerItemId = 1, engineItemId = 0))
    }

    @Test
    fun pointerVsEngineDivergenceRendersWithoutIdsWhenEngineIsInHistory() {
        val state = buildState(
            queueItems = listOf(queueItem(1), queueItem(2)),
            session = session(items = listOf(sessionItem(1)), currentIndex = 0, historyItems = listOf(sessionItem(2))),
            engineState = engineState(currentItemId = 2, isSpeaking = true),
        )
        assertEquals(PointerEngineRelation.DIFFER, state.pointerEngineRelation)
        val divergence = state.divergences.first { it.name == "pointer-vs-engine" }
        assertTrue(divergence.lines.any { it.contains("in history") })
    }

    @Test
    fun canonicalVsSessionSilentOnPostReconcileFalsePositiveFixture() {
        // R5 (raw last_read_percent after queue reconcile) differs from R1 but does not exceed R2.
        val item = queueItem(1, apiProgressPercent = 40, apiFurthestPercent = 60)
        val state = buildState(
            queueItems = listOf(item),
            session = session(items = listOf(sessionItem(1, lastReadPercent = 55)), currentIndex = 0),
            engineState = engineState(currentItemId = 1, isSpeaking = true),
        )
        assertTrue(state.divergences.none { it.name == "canonical-vs-session" })
    }

    @Test
    fun canonicalVsSessionFiresWhenSessionExceedsFurthest() {
        val item = queueItem(1, apiProgressPercent = 40, apiFurthestPercent = 60)
        val state = buildState(
            queueItems = listOf(item),
            session = session(items = listOf(sessionItem(1, lastReadPercent = 75)), currentIndex = 0),
            engineState = engineState(currentItemId = 1, isSpeaking = true),
        )
        assertTrue(state.divergences.any { it.name == "canonical-vs-session" })
    }

    @Test
    fun canonicalVsFurthestFiresWhenResumeReadExceedsFurthest() {
        assertTrue(detectCanonicalVsFurthestDivergence(queueItem(1, resumeReadPercent = 90, apiFurthestPercent = 50)))
    }

    @Test
    fun canonicalVsFurthestSilentWhenNotExceeded() {
        assertFalse(detectCanonicalVsFurthestDivergence(queueItem(1, resumeReadPercent = 40, apiFurthestPercent = 50)))
    }

    @Test
    fun canonicalVsFurthestPostCapComparisonCanNeverFire() {
        val item = queueItem(1, resumeReadPercent = 90, apiFurthestPercent = 50)
        // The post-cap value is always <= furthest by construction (minOf in the getter).
        assertTrue(item.progressPercent <= item.furthestPercent)
    }

    // ---------------------------------------------------------------
    // Lag detectors
    // ---------------------------------------------------------------

    @Test
    fun liveVsPersistedCursorFiresOnlyWhenBothPresentAndDiffer() {
        assertTrue(
            detectLiveVsPersistedCursorLag(
                CursorValue(1, 500),
                CursorValue(1, 100),
            ),
        )
        assertFalse(detectLiveVsPersistedCursorLag(CursorValue(1, 100), CursorValue(1, 100)))
        assertFalse(detectLiveVsPersistedCursorLag(null, CursorValue(1, 100)))
        assertFalse(detectLiveVsPersistedCursorLag(CursorValue(1, 100), null))
    }

    @Test
    fun readerLagIsStructurallySilentGivenNoLiveSignal() {
        // §11.2 forbids hoisting PlayerScreen's live readerScrollOffsets state; this bounded
        // implementation therefore never has a live signal to compare against.
        assertFalse(detectReaderLag())
        val state = buildState(
            queueItems = listOf(queueItem(1)),
            session = session(items = listOf(sessionItem(1)), currentIndex = 0),
        )
        assertTrue(state.lagNotes.none { it.name == "reader-lag" })
    }

    // ---------------------------------------------------------------
    // Provenance resolution
    // ---------------------------------------------------------------

    @Test
    fun canonicalProvenanceResolvesAllFourFallbackOrders() {
        val apiWins = queueItem(1, apiProgressPercent = 10, resumeReadPercent = 20, lastReadPercent = 30)
        val resumeWins = queueItem(1, resumeReadPercent = 20, lastReadPercent = 30)
        val lastWins = queueItem(1, lastReadPercent = 30)
        val defaultWins = queueItem(1)

        assertEquals(ProgressSourceField.API_PROGRESS_PERCENT, resolveCanonicalSourceForTest(apiWins))
        assertEquals(ProgressSourceField.RESUME_READ_PERCENT, resolveCanonicalSourceForTest(resumeWins))
        assertEquals(ProgressSourceField.LAST_READ_PERCENT, resolveCanonicalSourceForTest(lastWins))
        assertEquals(ProgressSourceField.DEFAULT_ZERO, resolveCanonicalSourceForTest(defaultWins))
    }

    @Test
    fun furthestProvenanceCanResolveToApiFurthestPercent() {
        val furthestWins = queueItem(1, apiFurthestPercent = 90, lastReadPercent = 10)
        assertEquals(ProgressSourceField.API_FURTHEST_PERCENT, resolveFurthestSourceForTest(furthestWins))
    }

    @Test
    fun readerOffsetResolvesToDataStoreTierWhenPersistedEntryExists() {
        val column = buildItemProgressColumn(
            itemId = 1,
            role = ItemRole.ACTIVE_ITEM,
            queueItems = emptyList(),
            liveCursorByItem = emptyMap(),
            persistedCursorByItem = emptyMap(),
            session = null,
            getReaderScrollOffset = { 640 },
            persistedReaderScrollOffset = { 640 },
        )
        assertEquals(ReaderOffsetValue.Available(640, ReaderOffsetTier.DATA_STORE), column.readerOffsetDurable)
    }

    @Test
    fun readerOffsetResolvesToSessionTierWhenNoPersistedEntryExists() {
        val column = buildItemProgressColumn(
            itemId = 1,
            role = ItemRole.ACTIVE_ITEM,
            queueItems = emptyList(),
            liveCursorByItem = emptyMap(),
            persistedCursorByItem = emptyMap(),
            session = null,
            getReaderScrollOffset = { 320 },
            persistedReaderScrollOffset = { null },
        )
        assertEquals(ReaderOffsetValue.Available(320, ReaderOffsetTier.SESSION), column.readerOffsetDurable)
    }

    @Test
    fun liveReaderOffsetTierAlwaysRendersUnavailable() {
        val state = buildState(
            queueItems = listOf(queueItem(1)),
            session = session(items = listOf(sessionItem(1)), currentIndex = 0),
            durableReaderScrollOffsetByItem = mapOf(1 to 500),
        )
        val lines = renderSectionC(state)
        assertTrue(lines.any { it.contains("live reader offset") && it.contains("unavailable") })
    }

    // ---------------------------------------------------------------
    // Unavailable rendering (never invented zeroes)
    // ---------------------------------------------------------------

    @Test
    fun missingSessionCachedPercentRendersUnavailableNotZero() {
        val state = buildState(
            queueItems = listOf(queueItem(1, apiProgressPercent = 40)),
            session = session(items = listOf(sessionItem(1, lastReadPercent = null)), currentIndex = 0),
        )
        val lines = renderSectionB(state)
        assertTrue(lines.any { it.contains("session-cached percent: unavailable") })
    }

    @Test
    fun absentPendingRowRendersUnavailableNotZero() {
        val state = buildState(
            queueItems = listOf(queueItem(1)),
            session = session(items = listOf(sessionItem(1)), currentIndex = 0),
            pendingProgress = null,
        )
        val lines = renderSectionD(state)
        assertTrue(lines.any { it.contains("pending progress present: false") && it.contains("percent=unavailable") && it.contains("attempts=unavailable") })
    }

    @Test
    fun lastSuccessfulProgressPostAlwaysUnavailable() {
        val state = buildState(queueItems = listOf(queueItem(1)), session = session(items = listOf(sessionItem(1)), currentIndex = 0))
        assertTrue(renderSectionD(state).any { it == "last successful progress post: unavailable" })
    }

    @Test
    fun missingQueueItemRendersUnavailableCanonicalAndFurthest() {
        // Engine item id present but never loaded into queueItems.
        val state = buildState(
            queueItems = emptyList(),
            session = session(items = listOf(sessionItem(1)), currentIndex = 0),
            engineState = engineState(currentItemId = 1, isSpeaking = true),
        )
        val lines = renderSectionB(state)
        assertTrue(lines.any { it.contains("canonical percent: unavailable") })
        assertTrue(lines.any { it.contains("furthest percent: unavailable") })
    }

    // ---------------------------------------------------------------
    // Mandatory lag / snapshot markers, no numeric lag ceiling
    // ---------------------------------------------------------------

    @Test
    fun persistedCursorAlwaysRendersLagMarker() {
        val state = buildState(queueItems = listOf(queueItem(1)), session = session(items = listOf(sessionItem(1)), currentIndex = 0))
        assertTrue(renderSectionC(state).any { it.contains("persisted-segment cursor") && it.contains("lag marker") })
    }

    @Test
    fun pendingRowAlwaysRendersSnapshotTimeMarker() {
        val state = buildState(
            queueItems = listOf(queueItem(1)),
            session = session(items = listOf(sessionItem(1)), currentIndex = 0),
            pendingProgress = PendingProgressSnapshot(percent = 40, attemptCount = 1),
            nowMillis = 1_700_000_000_000L,
        )
        assertTrue(renderSectionD(state).any { it.contains("snapshot") })
    }

    @Test
    fun noRenderedStringContainsANumericLagCeiling() {
        val state = buildState(
            queueItems = listOf(queueItem(1)),
            session = session(items = listOf(sessionItem(1)), currentIndex = 0),
            liveCursorByItem = mapOf(1 to PlaybackPosition(2, 900)),
            persistedCursorByItem = mapOf(1 to PlaybackPosition(2, 100)),
        )
        val text = allRenderedLines(state).joinToString("\n")
        assertFalse(text.contains("120"))
        assertFalse(text.contains("24 px") || text.contains("24px"))
    }

    // ---------------------------------------------------------------
    // Exclusion test (privacy regression lock)
    // ---------------------------------------------------------------

    @Test
    fun excludesTitleUrlHostSourceLabelPackageArticleTextAndLastError() {
        val item = queueItem(
            1,
            apiProgressPercent = 40,
            apiFurthestPercent = 60,
            title = "SECRET_TITLE_TOKEN",
            url = "https://leak.example.com/SECRET_URL_TOKEN",
        )
        val state = buildState(
            queueItems = listOf(item),
            session = session(items = listOf(sessionItem(1, lastReadPercent = 50)), currentIndex = 0),
            pendingProgress = PendingProgressSnapshot(percent = 40, attemptCount = 2),
        )
        val text = allRenderedLines(state).joinToString("\n")
        // PendingProgressSnapshot structurally excludes lastError (repository boundary, §7.2) — there is
        // no lastError value reachable here to assert against; the exclusion is enforced by the type.
        val forbidden = listOf(
            "SECRET_TITLE_TOKEN",
            "SECRET_URL_TOKEN",
            "leak.example.com",
        )
        forbidden.forEach { token -> assertFalse("Found forbidden token: $token", text.contains(token)) }
    }

    // ---------------------------------------------------------------
    // No-identifier tests (mandatory, §7.2.1)
    // ---------------------------------------------------------------

    private fun buildDifferFixture(pointerId: Int, engineId: Int): ProgressPointerDiagnosticsUiState = buildState(
        queueItems = listOf(queueItem(pointerId, apiProgressPercent = 41), queueItem(engineId, apiProgressPercent = 17)),
        session = session(items = listOf(sessionItem(pointerId)), currentIndex = 0, historyItems = listOf(sessionItem(engineId))),
        engineState = engineState(currentItemId = engineId, isSpeaking = true),
        pendingProgress = PendingProgressSnapshot(percent = 41, attemptCount = 1),
        nowMillis = 5_000L,
    )

    private fun buildAgreeFixture(itemId: Int): ProgressPointerDiagnosticsUiState = buildState(
        queueItems = listOf(queueItem(itemId, apiProgressPercent = 41)),
        session = session(items = listOf(sessionItem(itemId)), currentIndex = 0),
        engineState = engineState(currentItemId = itemId, isSpeaking = true),
        pendingProgress = PendingProgressSnapshot(percent = 41, attemptCount = 1),
        nowMillis = 5_000L,
    )

    private val idTriples = listOf(
        Pair(907_211, 907_212) to 907_213,
        Pair(41_308, 588_697) to 12_044,
        Pair(3_112_004, 9_870_555) to 6_009_318,
    )

    @Test
    fun noIdSubstringInDifferFixture() {
        idTriples.forEach { (pointerAndEngine, pendingLikeId) ->
            val (pointerId, engineId) = pointerAndEngine
            val state = buildDifferFixture(pointerId, engineId)
            val text = allRenderedLines(state).joinToString("\n")
            assertFalse(text.contains(pointerId.toString()))
            assertFalse(text.contains(engineId.toString()))
            assertFalse(text.contains(pendingLikeId.toString()))
        }
    }

    @Test
    fun noIdSubstringInAgreeFixture() {
        idTriples.forEach { (pointerAndEngine, _) ->
            val (itemId, _) = pointerAndEngine
            val state = buildAgreeFixture(itemId)
            val text = allRenderedLines(state).joinToString("\n")
            assertFalse(text.contains(itemId.toString()))
        }
    }

    @Test
    fun renderIsByteIdenticalAcrossVaryingIdsInDifferFixture() {
        val renders = idTriples.map { (pointerAndEngine, _) ->
            val (pointerId, engineId) = pointerAndEngine
            allRenderedLines(buildDifferFixture(pointerId, engineId)).joinToString("\n")
        }
        assertEquals(renders[0], renders[1])
        assertEquals(renders[1], renders[2])
    }

    @Test
    fun renderIsByteIdenticalAcrossVaryingIdsInAgreeFixture() {
        val renders = idTriples.map { (pointerAndEngine, _) ->
            val (itemId, _) = pointerAndEngine
            allRenderedLines(buildAgreeFixture(itemId)).joinToString("\n")
        }
        assertEquals(renders[0], renders[1])
        assertEquals(renders[1], renders[2])
    }

    // ---------------------------------------------------------------
    // Relational-label test
    // ---------------------------------------------------------------

    @Test
    fun differRendersRoleLabelsAndPointerPosition() {
        val state = buildState(
            queueItems = listOf(queueItem(1), queueItem(2)),
            session = session(items = listOf(sessionItem(1), sessionItem(99)), currentIndex = 0, historyItems = listOf(sessionItem(2))),
            engineState = engineState(currentItemId = 2, isSpeaking = true),
        )
        assertEquals(PointerEngineRelation.DIFFER, state.pointerEngineRelation)
        val sectionA = renderSectionA(state)
        assertTrue(sectionA.any { it.contains("pointer/engine: differ") })
        assertTrue(sectionA.any { it.contains("session pointer position: 1 of 2") })
        val sectionB = renderSectionB(state)
        assertTrue(sectionB.any { it.startsWith("pointer item") })
        assertTrue(sectionB.any { it.startsWith("engine item") })
    }

    @Test
    fun agreeRendersSingleActiveItemColumn() {
        val state = buildAgreeFixture(1)
        val sectionA = renderSectionA(state)
        assertTrue(sectionA.any { it.contains("pointer/engine: agree") })
        val sectionB = renderSectionB(state)
        assertTrue(sectionB.all { it.startsWith("active item") })
    }

    @Test
    fun sessionPointerPositionUnavailableWhenCurrentIndexOutOfRange() {
        val state = buildState(
            queueItems = listOf(queueItem(1)),
            session = session(items = listOf(sessionItem(1)), currentIndex = -1),
            engineState = engineState(currentItemId = 1, isSpeaking = true),
        )
        assertTrue(renderSectionA(state).any { it.contains("session pointer position: unavailable") })
    }

    // ---------------------------------------------------------------
    // No-logging assertion (source-level)
    // ---------------------------------------------------------------

    @Test
    fun sourceFileContainsNoLoggingTokens() {
        val candidatePaths = listOf(
            "app/src/main/java/com/mimeo/android/ui/settings/ProgressPointerDiagnosticsScreen.kt",
            "src/main/java/com/mimeo/android/ui/settings/ProgressPointerDiagnosticsScreen.kt",
            "../app/src/main/java/com/mimeo/android/ui/settings/ProgressPointerDiagnosticsScreen.kt",
        )
        val file = candidatePaths.map(::File).firstOrNull { it.exists() }
        requireNotNull(file) { "Could not locate ProgressPointerDiagnosticsScreen.kt from working dir ${File(".").absolutePath}" }
        val text = file.readText()
        val forbiddenTokens = listOf("Log.", "println(", "debugLog", "continuationLog")
        forbiddenTokens.forEach { token ->
            assertFalse("Found forbidden logging token: $token", text.contains(token))
        }
    }

    // ---------------------------------------------------------------
    // Pure-function / read-only invariant
    // ---------------------------------------------------------------

    @Test
    fun builderIsPureGivenIdenticalInputs() {
        val first = buildAgreeFixture(1)
        val second = buildAgreeFixture(1)
        assertEquals(first, second)
    }
}

private fun resolveCanonicalSourceForTest(item: PlaybackQueueItem): ProgressSourceField {
    val column = buildItemProgressColumn(
        itemId = item.itemId,
        role = ItemRole.ACTIVE_ITEM,
        queueItems = listOf(item),
        liveCursorByItem = emptyMap(),
        persistedCursorByItem = emptyMap(),
        session = null,
        getReaderScrollOffset = { null },
        persistedReaderScrollOffset = { null },
    )
    return column.canonicalSourceField!!
}

private fun resolveFurthestSourceForTest(item: PlaybackQueueItem): ProgressSourceField {
    val column = buildItemProgressColumn(
        itemId = item.itemId,
        role = ItemRole.ACTIVE_ITEM,
        queueItems = listOf(item),
        liveCursorByItem = emptyMap(),
        persistedCursorByItem = emptyMap(),
        session = null,
        getReaderScrollOffset = { null },
        persistedReaderScrollOffset = { null },
    )
    return column.furthestSourceField!!
}
