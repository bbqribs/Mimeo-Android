package com.mimeo.android.ui.reader

import android.app.SearchManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mimeo.android.ui.common.shareSelectedText
import com.mimeo.android.ui.common.webSearchText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Device-level assurance for the Reader context-action intent and clipboard contracts. */
@RunWith(AndroidJUnit4::class)
class ReaderContextActionsInstrumentedTest {

    private val appContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun selectedTextShareAndWebSearchPreserveExactPayloads() {
        val context = RecordingContext(appContext)
        val selectedText = "  exact reader selection\n"

        shareSelectedText(context, selectedText)
        webSearchText(context, selectedText)

        val chooser = context.started[0]
        @Suppress("DEPRECATION")
        val share = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        val search = context.started[1]
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals(Intent.ACTION_SEND, share?.action)
        assertEquals("text/plain", share?.type)
        assertEquals(selectedText, share?.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(Intent.ACTION_WEB_SEARCH, search.action)
        assertEquals(selectedText, search.getStringExtra(SearchManager.QUERY))
    }

    @Test
    fun blankSelectionLaunchesNothing() {
        val context = RecordingContext(appContext)

        dispatchReaderSelectedTextAction(" \t\n") { shareSelectedText(context, it) }
        dispatchReaderSelectedTextAction(" \t\n") { webSearchText(context, it) }

        assertTrue(context.started.isEmpty())
    }

    @Test
    fun linkShareUsesExactUrlAndNormalTapStillViewsIt() {
        val context = RecordingContext(appContext)
        val url = "https://example.com/reader-link?x=1"

        dispatchReaderLinkAddress(url) { shareSelectedText(context, it) }
        val opened = openReaderLink(context, url)

        @Suppress("DEPRECATION")
        val share = context.started[0].getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        val view = context.started[1]
        assertEquals(url, share?.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue(opened)
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals(url, view.dataString)
        assertFalse(view.hasExtra(Intent.EXTRA_TEXT))
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        val started = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            started += Intent(intent)
        }

        override fun startActivity(intent: Intent, options: Bundle?) {
            started += Intent(intent)
        }
    }
}
