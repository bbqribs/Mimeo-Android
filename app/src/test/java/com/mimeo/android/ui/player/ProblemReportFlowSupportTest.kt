package com.mimeo.android.ui.player

import com.mimeo.android.data.ApiException
import com.mimeo.android.model.ProblemReportCategory
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class ProblemReportFlowSupportTest {

    @Test
    fun defaultCategoryPrefersItemLinkedContext() {
        val category = resolveDefaultProblemReportCategory(
            itemId = 42,
            url = "https://example.com/article",
        )

        assertEquals(ProblemReportCategory.CONTENT_PROBLEM, category)
    }

    @Test
    fun defaultCategoryFallsBackToSaveFailureForUrlOnlyContext() {
        val category = resolveDefaultProblemReportCategory(
            itemId = null,
            url = "https://example.com/article",
        )

        assertEquals(ProblemReportCategory.SAVE_FAILURE, category)
    }

    @Test
    fun defaultCategoryFallsBackToAppProblemWithoutItemOrUrl() {
        val category = resolveDefaultProblemReportCategory(
            itemId = null,
            url = "",
        )

        assertEquals(ProblemReportCategory.APP_PROBLEM, category)
    }

    @Test
    fun failureMessageUsesAuthGuidanceForUnauthorized() {
        val message = resolveProblemReportFailureMessage(ApiException(401, "HTTP 401"))

        assertEquals("Sign in to submit problem reports.", message)
    }

    @Test
    fun failureMessageUsesRateLimitMessageFor429() {
        val message = resolveProblemReportFailureMessage(ApiException(429, "HTTP 429"))

        assertEquals("Too many reports sent recently. Please try again later.", message)
    }

    @Test
    fun failureMessageUsesNetworkMessageForConnectivityErrors() {
        val message = resolveProblemReportFailureMessage(IOException("timeout"))

        assertEquals("Couldn't reach server. Check connection and try again.", message)
    }
}
