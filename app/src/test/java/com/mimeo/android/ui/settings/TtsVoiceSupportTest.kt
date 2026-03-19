package com.mimeo.android.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsVoiceSupportTest {
    @Test
    fun `mapTtsVoiceOptions prioritizes offline higher quality voices and deduplicates by name`() {
        val options = mapTtsVoiceOptions(
            listOf(
                TtsVoiceDescriptor(
                    name = "en-US-1",
                    localeTag = "en-US",
                    quality = 300,
                    latency = 300,
                    requiresNetwork = true,
                ),
                TtsVoiceDescriptor(
                    name = "en-US-1",
                    localeTag = "en-US",
                    quality = 500,
                    latency = 100,
                    requiresNetwork = false,
                ),
                TtsVoiceDescriptor(
                    name = "en-GB-1",
                    localeTag = "en-GB",
                    quality = 400,
                    latency = 100,
                    requiresNetwork = false,
                ),
            ),
        )

        assertEquals(2, options.size)
        assertTrue(options.any { it.name == "en-GB-1" })
        assertTrue(options.any { it.name == "en-US-1" })
        val enUs = options.first { it.name == "en-US-1" }
        assertTrue(enUs.label.contains("offline"))
    }

    @Test
    fun `resolveConfiguredTtsVoiceName keeps configured value when available ignoring case`() {
        val resolved = resolveConfiguredTtsVoiceName(
            configuredVoiceName = "En-Us-1",
            availableOptions = listOf(
                TtsVoiceOption(name = "en-us-1", label = "en-US - en-us-1 - offline"),
            ),
        )

        assertEquals("En-Us-1", resolved)
    }

    @Test
    fun `resolveConfiguredTtsVoiceName falls back to default when configured voice missing`() {
        val resolved = resolveConfiguredTtsVoiceName(
            configuredVoiceName = "en-US-missing",
            availableOptions = listOf(
                TtsVoiceOption(name = "en-US-available", label = "en-US - en-US-available - offline"),
            ),
        )

        assertEquals("", resolved)
    }
}
