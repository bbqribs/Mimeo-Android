package com.mimeo.android.ui.settings

import com.mimeo.android.model.BlueskyAccountConnectionResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BLUESKY-STATUS-1 — diagnostics boundary guards for the ordinary Bluesky settings spoke.
 *
 * The Bluesky spoke is an ordinary (always-visible) hub category, so the raw account
 * diagnostics it used to render inline — the account DID, connection mode, un-humanized
 * validation codes, backend messages, and the whole scheduler/per-source operator block —
 * must not reach ordinary users. They are debug-only; ordinary users get the friendly
 * BlueskyHealthCard instead. These pin the pure visibility helpers that enforce that.
 */
class BlueskyStatusBoundaryTest {

    private val connectedAccount = BlueskyAccountConnectionResponse(
        connected = true,
        handle = "alice.bsky.social",
        did = "did:plc:examplerawidentifier",
        mode = "public_author_feed",
        lastValidationStatus = "auth_error",
        message = "raw backend note",
    )

    @Test
    fun connectedAccountHidesRawDiagnosticsFromOrdinaryUsers() {
        val lines = blueskyAccountDiagnosticLines(connectedAccount, isDebugBuild = false)
        assertTrue("Ordinary surface must show no raw account diagnostics", lines.isEmpty())
    }

    @Test
    fun didNeverAppearsOnOrdinarySurface() {
        val rendered = blueskyAccountDiagnosticLines(connectedAccount, isDebugBuild = false)
            .joinToString("\n") { "${it.label}: ${it.value}" }
            .lowercase()
        assertFalse("DID must not appear on the ordinary Bluesky spoke", rendered.contains("did:"))
        assertFalse(rendered.contains("did"))
    }

    @Test
    fun diagnosticsRemainAvailableInDebugBuilds() {
        // Diagnostics copy must remain available where intentionally diagnostic (debug build).
        val lines = blueskyAccountDiagnosticLines(connectedAccount, isDebugBuild = true)
        val labels = lines.map { it.label }
        assertTrue("Debug build keeps the DID diagnostic", labels.contains("DID"))
        assertTrue(labels.contains("Handle"))
        assertTrue(labels.contains("Mode"))
        assertEquals(
            "did:plc:examplerawidentifier",
            lines.first { it.label == "DID" }.value,
        )
    }

    @Test
    fun disconnectedAccountHasNoDiagnosticLinesEvenInDebug() {
        val disconnected = BlueskyAccountConnectionResponse(connected = false)
        assertTrue(blueskyAccountDiagnosticLines(disconnected, isDebugBuild = true).isEmpty())
        assertTrue(blueskyAccountDiagnosticLines(null, isDebugBuild = true).isEmpty())
    }

    @Test
    fun schedulerDiagnosticsAreDebugOnly() {
        // The scheduler/per-source block renders raw last-run codes, raw backend error
        // messages, and actor identifiers, so it is gated to debug builds only.
        assertFalse(blueskySchedulerDiagnosticsVisible(isDebugBuild = false))
        assertTrue(blueskySchedulerDiagnosticsVisible(isDebugBuild = true))
    }
}
