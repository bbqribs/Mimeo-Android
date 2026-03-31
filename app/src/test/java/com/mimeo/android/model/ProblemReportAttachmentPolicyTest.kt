package com.mimeo.android.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProblemReportAttachmentPolicyTest {

    @Test
    fun titleAttachmentIsTrimmedAndBounded() {
        val raw = "  " + "T".repeat(PROBLEM_REPORT_TITLE_ATTACHMENT_MAX_CHARS + 30) + "  "

        val attached = toProblemReportAttachmentTitle(raw)

        assertEquals(PROBLEM_REPORT_TITLE_ATTACHMENT_MAX_CHARS, attached?.length)
        assertTrue(attached?.startsWith("T") == true)
    }

    @Test
    fun titleAttachmentIsNullWhenBlank() {
        assertNull(toProblemReportAttachmentTitle("   "))
        assertNull(toProblemReportAttachmentTitle(null))
    }

    @Test
    fun textAttachmentIsBounded() {
        val raw = "A".repeat(PROBLEM_REPORT_TEXT_ATTACHMENT_MAX_CHARS + 20)

        val attached = toProblemReportAttachmentText(raw)

        assertEquals(PROBLEM_REPORT_TEXT_ATTACHMENT_MAX_CHARS, attached?.length)
    }

    @Test
    fun textAttachmentIsNullWhenBlank() {
        assertNull(toProblemReportAttachmentText(""))
        assertNull(toProblemReportAttachmentText(null))
    }
}
