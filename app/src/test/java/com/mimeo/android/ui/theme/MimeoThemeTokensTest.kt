package com.mimeo.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mimeo.android.model.VisualDensityPreference
import com.mimeo.android.model.VisualThemePreference
import org.junit.Assert.assertEquals
import org.junit.Test

class MimeoThemeTokensTest {

    @Test
    fun paperLightColors_matchVisualPlan() {
        val colors = MimeoColors.PaperLight
        assertEquals(Color(0xFFF4EFE7), colors.bg)
        assertEquals(Color(0xFFFAF7F1), colors.surface)
        assertEquals(Color(0xFFFFFCF6), colors.surfaceHi)
        assertEquals(Color(0xFFE2DACB), colors.line)
        assertEquals(Color(0xFFECE5D6), colors.lineSoft)
        assertEquals(Color(0xFF1B1A17), colors.fg)
        assertEquals(Color(0xFF67625A), colors.fg2)
        assertEquals(Color(0xFF9A9388), colors.fg3)
        assertEquals(Color(0xFFC7BEAE), colors.fg4)
        assertEquals(Color(0xFFC25B2E), colors.accent)
        assertEquals(Color(0x1AC25B2E), colors.accentDim)
        assertEquals(Color(0xFFFFFCF6), colors.accentOn)
        assertEquals(Color(0x12C25B2E), colors.nowTint)
        assertEquals(Color(0xFF3F7A52), colors.success)
        assertEquals(Color(0xFFB7892A), colors.warn)
        assertEquals(Color(0xFFB84A3F), colors.danger)
    }

    @Test
    fun emberDarkColors_matchVisualPlan() {
        val colors = MimeoColors.EmberDark
        assertEquals(Color(0xFF0B0B0E), colors.bg)
        assertEquals(Color(0xFF121217), colors.surface)
        assertEquals(Color(0xFF181822), colors.surfaceHi)
        assertEquals(Color(0xFF1F1F2A), colors.line)
        assertEquals(Color(0xFF15151D), colors.lineSoft)
        assertEquals(Color(0xFFECECF1), colors.fg)
        assertEquals(Color(0xFF9A99A6), colors.fg2)
        assertEquals(Color(0xFF5E5D6B), colors.fg3)
        assertEquals(Color(0xFF3A3947), colors.fg4)
        assertEquals(Color(0xFFB6A1FF), colors.accent)
        assertEquals(Color(0x24B6A1FF), colors.accentDim)
        assertEquals(Color(0xFF0B0B0E), colors.accentOn)
        assertEquals(Color(0x0FB6A1FF), colors.nowTint)
        assertEquals(Color(0xFF7FD1A8), colors.success)
        assertEquals(Color(0xFFE8C26A), colors.warn)
        assertEquals(Color(0xFFF26E6E), colors.danger)
    }

    @Test
    fun defaultDensity_mapsToExpectedSpacing() {
        val density = densityTokensFor(VisualDensityPreference.DEFAULT)
        assertEquals(14.dp, density.rowPadV)
        assertEquals(4.dp, density.rowGap)
        assertEquals(18.dp, density.sectionGap)
    }

    @Test
    fun compactDensity_mapsToExpectedSpacing() {
        val density = densityTokensFor(VisualDensityPreference.COMPACT)
        assertEquals(10.dp, density.rowPadV)
        assertEquals(2.dp, density.rowGap)
        assertEquals(14.dp, density.sectionGap)
    }

    @Test
    fun visualThemePreference_resolvesWithoutGlobalApplication() {
        assertEquals(
            MimeoThemeChoice.LIGHT,
            resolveThemeChoice(VisualThemePreference.FOLLOW_SYSTEM, systemIsDarkTheme = false),
        )
        assertEquals(
            MimeoThemeChoice.DARK,
            resolveThemeChoice(VisualThemePreference.FOLLOW_SYSTEM, systemIsDarkTheme = true),
        )
        assertEquals(
            MimeoThemeChoice.LIGHT,
            resolveThemeChoice(VisualThemePreference.LIGHT, systemIsDarkTheme = true),
        )
        assertEquals(
            MimeoThemeChoice.DARK,
            resolveThemeChoice(VisualThemePreference.DARK, systemIsDarkTheme = false),
        )
    }
}
