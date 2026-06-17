package com.mimeo.android.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SETTINGS-HUB-1 — guards for the Settings hub-and-spoke index.
 *
 * The hub is data-driven: [SettingsHubIndex] renders exactly [SettingsSection.entries],
 * one clickable row per entry, each calling `onSelect(section)`. So the enum is the
 * contract that determines which category labels the hub shows and which spokes are
 * reachable. These are intentionally thin enum-level guards (no Compose/snapshot
 * brittleness) covering:
 *   - the hub displays the expected category labels,
 *   - every category is reachable (one row per enum entry),
 *   - ordinary hub rows use user-facing copy and don't leak diagnostic/raw vocabulary.
 */
class SettingsHubIndexTest {

    @Test
    fun hubDisplaysExpectedCategoryLabelsInOrder() {
        // The exact, ordered set of category titles a user sees on the hub index.
        val expected = listOf(
            "Account & Connection",
            "General",
            "Reading",
            "Playback",
            "Appearance",
            "Library & Downloads",
            "AI Summaries",
            "Bluesky",
            "Diagnostics",
        )
        assertEquals(expected, SettingsSection.entries.map { it.title })
    }

    @Test
    fun everyCategoryIsReachableWithUserFacingTitleAndSubtitle() {
        // SettingsHubIndex iterates SettingsSection.entries and emits a clickable row
        // for each, so "reachable" == "present in entries". Each must carry non-blank
        // title + description copy for the row to be meaningful.
        SettingsSection.entries.forEach { section ->
            assertTrue(
                "Section ${section.name} must have a non-blank title",
                section.title.isNotBlank(),
            )
            assertTrue(
                "Section ${section.name} must have a non-blank subtitle",
                section.subtitle.isNotBlank(),
            )
        }
        // No duplicate labels would make two hub rows indistinguishable.
        val titles = SettingsSection.entries.map { it.title }
        assertEquals(titles.size, titles.toSet().size)
    }

    @Test
    fun ordinaryHubRowsDoNotLeakDiagnosticOrRawVocabulary() {
        // Diagnostics is the one spoke allowed intentionally technical vocabulary.
        // Ordinary hub rows must not surface raw backend/debug phrasing: raw at:// or
        // did: identifiers, job IDs, tokens/cookies/secrets values, stack traces, raw
        // URLs, or provider payloads. NOTE: the *concept* "device token" is a legitimate
        // user-facing setting (Account & Connection), so the guard targets raw/secret
        // shapes — not the plain word "token".
        val forbidden = listOf(
            "at://",
            "did:",
            "http://",
            "https://",
            "job id",
            "jobid",
            "cookie",
            "secret",
            "sk-",
            "bearer ",
            "payload",
            "traceback",
            "stack trace",
            "exception",
            "{",
            "}",
        )
        SettingsSection.entries
            .filter { it != SettingsSection.DIAGNOSTICS }
            .forEach { section ->
                val copy = "${section.title}\n${section.subtitle}".lowercase()
                forbidden.forEach { needle ->
                    assertFalse(
                        "Ordinary hub row ${section.name} leaked diagnostic/raw token: '$needle'",
                        copy.contains(needle),
                    )
                }
            }
    }

    @Test
    fun diagnosticsRowMayRemainTechnical() {
        // The boundary is intentional: Diagnostics is the operator-facing spoke and is
        // allowed technical phrasing. This pins that Diagnostics still exists as its own
        // reachable category so diagnostic vocabulary has a home off the ordinary rows.
        assertTrue(SettingsSection.entries.contains(SettingsSection.DIAGNOSTICS))
        assertTrue(SettingsSection.DIAGNOSTICS.subtitle.lowercase().contains("diagnostics"))
    }
}
