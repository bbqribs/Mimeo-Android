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
        assertTrue(enUs.voiceLabel.contains("offline"))
        assertTrue(enUs.localeLabel.contains("English"))
    }

    @Test
    fun `resolveConfiguredTtsVoiceName keeps configured value when available ignoring case`() {
        val resolved = resolveConfiguredTtsVoiceName(
            configuredVoiceName = "En-Us-1",
            availableOptions = listOf(
                TtsVoiceOption(
                    name = "en-us-1",
                    localeTag = "en-US",
                    localeLabel = "English (United States)",
                    voiceLabel = "en-us-1 - offline",
                ),
            ),
        )

        assertEquals("En-Us-1", resolved)
    }

    @Test
    fun `resolveConfiguredTtsVoiceName falls back to default when configured voice missing`() {
        val resolved = resolveConfiguredTtsVoiceName(
            configuredVoiceName = "en-US-missing",
            availableOptions = listOf(
                TtsVoiceOption(
                    name = "en-US-available",
                    localeTag = "en-US",
                    localeLabel = "English (United States)",
                    voiceLabel = "en-US-available - offline",
                ),
            ),
        )

        assertEquals("", resolved)
    }

    @Test
    fun `mapTtsLocaleOptions groups voices by locale tag`() {
        val locales = mapTtsLocaleOptions(
            listOf(
                TtsVoiceOption("en-1", "en-US", "English (United States)", "v1"),
                TtsVoiceOption("en-2", "en-US", "English (United States)", "v2"),
                TtsVoiceOption("de-1", "de-DE", "German (Germany)", "v3"),
                TtsVoiceOption("uk-1", "en-GB", "English (United Kingdom)", "v4"),
            ),
        )

        assertEquals(3, locales.size)
        assertTrue(locales.any { it.tag == "en-US" })
        assertTrue(locales.any { it.tag == "de-DE" })
        assertEquals("en-GB", locales.first().tag)
    }

    @Test
    fun `resolveConfiguredTtsVoiceSelection returns fallback message when unavailable`() {
        val resolution = resolveConfiguredTtsVoiceSelection(
            configuredVoiceName = "missing",
            availableOptions = listOf(
                TtsVoiceOption("present", "en-US", "English (United States)", "present - offline"),
            ),
        )

        assertEquals("", resolution.resolvedVoiceName)
        assertTrue(resolution.message.orEmpty().contains("Saved voice unavailable"))
    }
}
