package com.mimeo.android.ui.reader

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.mimeo.android.ui.common.selectedTextShareIntent
import com.mimeo.android.ui.common.selectedTextWebSearchIntent
import com.mimeo.android.ui.common.webSearchText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReaderContextActionsTest {

    private val appContext: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun selectedTextShareIntent_hasPlainTextMimeTypeAndExactPayload() {
        val selectedText = "  Exact selected text\n"

        val intent = selectedTextShareIntent(selectedText)

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals(selectedText, intent.getStringExtra(Intent.EXTRA_TEXT))
        assertNull(intent.getStringExtra(Intent.EXTRA_SUBJECT))
    }

    @Test
    fun selectedTextWebSearchIntent_hasExactQuery() {
        val selectedText = "  Exact selected text\n"

        val intent = selectedTextWebSearchIntent(selectedText)

        assertEquals(Intent.ACTION_WEB_SEARCH, intent?.action)
        assertEquals(selectedText, intent?.getStringExtra(SearchManager.QUERY))
    }

    @Test
    fun blankSelectionsDoNotDispatchShareOrWebSearch() {
        val launches = mutableListOf<String>()

        dispatchReaderSelectedTextAction(" \t\n") { text -> launches += "share:$text" }
        dispatchReaderSelectedTextAction(" \t\n") { text -> launches += "search:$text" }

        assertTrue(launches.isEmpty())
        assertNull(selectedTextWebSearchIntent(" \t\n"))
    }

    @Test
    fun webSearchTextHandlesMissingIntentHandler() {
        val noHandlerContext = object : ContextWrapper(appContext) {
            override fun startActivity(intent: Intent) {
                throw ActivityNotFoundException()
            }

            override fun startActivity(intent: Intent, options: Bundle?) {
                throw ActivityNotFoundException()
            }
        }

        val result = runCatching { webSearchText(noHandlerContext, "query") }

        assertTrue(result.isSuccess)
    }

    @Test
    fun shareLinkAddressUsesOnlyExactUrl() {
        val shared = mutableListOf<String>()
        val url = "https://example.com/path?item=1"

        dispatchReaderLinkAddress(url, shared::add)

        assertEquals(listOf(url), shared)
    }

    @Test
    fun copyLinkAddressWritesExactUrl() {
        val url = "https://example.com/path?item=1"

        copyReaderLinkAddress(appContext, url)

        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(url, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        assertEquals("link address", clipboard.primaryClipDescription?.label)
    }

    @Test
    fun unsafeOrNonLinkSelectionDoesNotResolveToLinkAddress() {
        val unsafe = ReaderLinkRange(start = 0, endExclusive = 4, url = "javascript:alert(1)")

        assertNull(resolveSelectionLinkUrl(0, 1, listOf(unsafe)))
        assertNull(resolveSelectionLinkUrl(0, 1, emptyList()))
    }

    @Test
    fun normalLinkTapRetainsImplicitViewIntent() {
        val started = mutableListOf<Intent>()
        val context = object : ContextWrapper(appContext) {
            override fun startActivity(intent: Intent) {
                started += Intent(intent)
            }

            override fun startActivity(intent: Intent, options: Bundle?) {
                started += Intent(intent)
            }
        }
        val url = "https://example.com/path"

        val opened = openReaderLink(context, url)

        assertTrue(opened)
        assertEquals(1, started.size)
        assertEquals(Intent.ACTION_VIEW, started.single().action)
        assertEquals(url, started.single().dataString)
        assertFalse(started.single().hasExtra(Intent.EXTRA_TEXT))
    }
}
