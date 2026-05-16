package com.mimeo.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mimeo.android.model.AccentSchemePreference
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
    fun followSystemThemePreference_resolvesLightWhenSystemIsLight() {
        assertEquals(
            MimeoThemeChoice.LIGHT,
            resolveThemeChoice(VisualThemePreference.FOLLOW_SYSTEM, systemIsDarkTheme = false),
        )
    }

    @Test
    fun followSystemThemePreference_resolvesDarkWhenSystemIsDark() {
        assertEquals(
            MimeoThemeChoice.DARK,
            resolveThemeChoice(VisualThemePreference.FOLLOW_SYSTEM, systemIsDarkTheme = true),
        )
    }

    @Test
    fun lightThemePreference_resolvesLight() {
        assertEquals(
            MimeoThemeChoice.LIGHT,
            resolveThemeChoice(VisualThemePreference.LIGHT, systemIsDarkTheme = true),
        )
    }

    @Test
    fun darkThemePreference_resolvesDark() {
        assertEquals(
            MimeoThemeChoice.DARK,
            resolveThemeChoice(VisualThemePreference.DARK, systemIsDarkTheme = false),
        )
    }

    @Test
    fun visualV1ThemeFlagDisabled_keepsLegacyThemePath() {
        assertEquals(
            MimeoThemeRuntimePath.LEGACY,
            resolveThemeRuntimePath(enableVisualDesignV1 = false),
        )
    }

    @Test
    fun visualV1ThemeFlagEnabled_usesVisualV1Path() {
        assertEquals(
            MimeoThemeRuntimePath.VISUAL_V1,
            resolveThemeRuntimePath(enableVisualDesignV1 = true),
        )
    }

    // Accent-token substrate — Ember scheme (existing)

    @Test
    fun emberLightAccent_matchesColorTokens() {
        val scheme = MimeoAccentSchemes.EmberLight
        val colors = MimeoColors.PaperLight
        assertEquals(scheme.accent, colors.accent)
        assertEquals(scheme.accentDim, colors.accentDim)
        assertEquals(scheme.accentOn, colors.accentOn)
        assertEquals(scheme.nowTint, colors.nowTint)
    }

    @Test
    fun emberDarkAccent_matchesColorTokens() {
        val scheme = MimeoAccentSchemes.EmberDark
        val colors = MimeoColors.EmberDark
        assertEquals(scheme.accent, colors.accent)
        assertEquals(scheme.accentDim, colors.accentDim)
        assertEquals(scheme.accentOn, colors.accentOn)
        assertEquals(scheme.nowTint, colors.nowTint)
    }

    @Test
    fun emberLightAccent_rawValues() {
        val scheme = MimeoAccentSchemes.EmberLight
        assertEquals(Color(0xFFC25B2E), scheme.accent)
        assertEquals(Color(0x1AC25B2E), scheme.accentDim)
        assertEquals(Color(0xFFFFFCF6), scheme.accentOn)
        assertEquals(Color(0x12C25B2E), scheme.nowTint)
    }

    @Test
    fun emberDarkAccent_rawValues() {
        val scheme = MimeoAccentSchemes.EmberDark
        assertEquals(Color(0xFFB6A1FF), scheme.accent)
        assertEquals(Color(0x24B6A1FF), scheme.accentDim)
        assertEquals(Color(0xFF0B0B0E), scheme.accentOn)
        assertEquals(Color(0x0FB6A1FF), scheme.nowTint)
    }

    // Accent scheme variants

    @Test
    fun defaultAccentScheme_isLilac() {
        // colorTokensFor with no explicit scheme must produce Lilac accent values
        val light = colorTokensFor(MimeoThemeChoice.LIGHT)
        val dark  = colorTokensFor(MimeoThemeChoice.DARK)
        assertEquals(MimeoAccentSchemes.LilacLight.accent, light.accent)
        assertEquals(MimeoAccentSchemes.LilacDark.accent,  dark.accent)
    }

    @Test
    fun emberScheme_paperLightAndEmberDarkBindingsUnchanged() {
        // Passing EMBER explicitly must produce identical accent fields to the static objects
        val light = colorTokensFor(MimeoThemeChoice.LIGHT, MimeoAccentScheme.EMBER)
        val dark  = colorTokensFor(MimeoThemeChoice.DARK,  MimeoAccentScheme.EMBER)

        assertEquals(MimeoColors.PaperLight.accent,    light.accent)
        assertEquals(MimeoColors.PaperLight.accentDim, light.accentDim)
        assertEquals(MimeoColors.PaperLight.accentOn,  light.accentOn)
        assertEquals(MimeoColors.PaperLight.nowTint,   light.nowTint)

        assertEquals(MimeoColors.EmberDark.accent,    dark.accent)
        assertEquals(MimeoColors.EmberDark.accentDim, dark.accentDim)
        assertEquals(MimeoColors.EmberDark.accentOn,  dark.accentOn)
        assertEquals(MimeoColors.EmberDark.nowTint,   dark.nowTint)
    }

    @Test
    fun lilacScheme_resolvesDifferentAccentFromEmber() {
        val light = colorTokensFor(MimeoThemeChoice.LIGHT, MimeoAccentScheme.LILAC)
        val dark  = colorTokensFor(MimeoThemeChoice.DARK,  MimeoAccentScheme.LILAC)
        assertEquals(MimeoAccentSchemes.LilacLight.accent, light.accent)
        assertEquals(MimeoAccentSchemes.LilacDark.accent,  dark.accent)
        // Must differ from Ember
        assert(light.accent != colorTokensFor(MimeoThemeChoice.LIGHT, MimeoAccentScheme.EMBER).accent)
        assert(dark.accent  != colorTokensFor(MimeoThemeChoice.DARK,  MimeoAccentScheme.EMBER).accent)
    }

    @Test
    fun forestScheme_resolvesDifferentAccentFromEmber() {
        val light = colorTokensFor(MimeoThemeChoice.LIGHT, MimeoAccentScheme.FOREST)
        val dark  = colorTokensFor(MimeoThemeChoice.DARK,  MimeoAccentScheme.FOREST)
        assertEquals(MimeoAccentSchemes.ForestLight.accent, light.accent)
        assertEquals(MimeoAccentSchemes.ForestDark.accent,  dark.accent)
        assert(light.accent != colorTokensFor(MimeoThemeChoice.LIGHT, MimeoAccentScheme.EMBER).accent)
        assert(dark.accent  != colorTokensFor(MimeoThemeChoice.DARK,  MimeoAccentScheme.EMBER).accent)
    }

    @Test
    fun slateScheme_resolvesDifferentAccentFromEmber() {
        val light = colorTokensFor(MimeoThemeChoice.LIGHT, MimeoAccentScheme.SLATE)
        val dark  = colorTokensFor(MimeoThemeChoice.DARK,  MimeoAccentScheme.SLATE)
        assertEquals(MimeoAccentSchemes.SlateLight.accent, light.accent)
        assertEquals(MimeoAccentSchemes.SlateDark.accent,  dark.accent)
        assert(light.accent != colorTokensFor(MimeoThemeChoice.LIGHT, MimeoAccentScheme.EMBER).accent)
        assert(dark.accent  != colorTokensFor(MimeoThemeChoice.DARK,  MimeoAccentScheme.EMBER).accent)
    }

    @Test
    fun accentPreferenceMapping_feedsThemeTokenResolution() {
        val selectedScheme = AccentSchemePreference.SLATE.toMimeoAccentScheme()
        val tokens = colorTokensFor(MimeoThemeChoice.LIGHT, selectedScheme)
        assertEquals(MimeoAccentSchemes.SlateLight.accent, tokens.accent)
    }

    @Test
    fun allSchemes_haveDistinctLightAccents() {
        val schemes = MimeoAccentScheme.entries
        val lightAccents = schemes.map { colorTokensFor(MimeoThemeChoice.LIGHT, it).accent }
        assertEquals(schemes.size, lightAccents.toSet().size)
    }

    @Test
    fun allSchemes_haveDistinctDarkAccents() {
        val schemes = MimeoAccentScheme.entries
        val darkAccents = schemes.map { colorTokensFor(MimeoThemeChoice.DARK, it).accent }
        assertEquals(schemes.size, darkAccents.toSet().size)
    }

    @Test
    fun nonAccentFields_unchangedAcrossSchemes() {
        // bg, fg, line, etc. come from the base palette and must not vary with scheme
        val emberLight = colorTokensFor(MimeoThemeChoice.LIGHT, MimeoAccentScheme.EMBER)
        for (scheme in MimeoAccentScheme.entries) {
            val tokens = colorTokensFor(MimeoThemeChoice.LIGHT, scheme)
            assertEquals(emberLight.bg,        tokens.bg)
            assertEquals(emberLight.surface,   tokens.surface)
            assertEquals(emberLight.fg,        tokens.fg)
            assertEquals(emberLight.success,   tokens.success)
            assertEquals(emberLight.danger,    tokens.danger)
        }
    }
}
