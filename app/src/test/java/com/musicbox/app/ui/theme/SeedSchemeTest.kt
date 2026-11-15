package com.musicbox.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedSchemeTest {

    private val delta = 1e-3f

    @Test
    fun `null seed falls back to material defaults for both modes`() {
        val dark = buildScheme(null, dark = true)
        val light = buildScheme(null, dark = false)
        // 默认暗色 surface 很暗，默认浅色 surface 很亮
        assertTrue(perceivedLuminance(dark.surface) < 0.2f)
        assertTrue(perceivedLuminance(light.surface) > 0.5f)
    }

    @Test
    fun `same seed yields brighter primary in dark than in light`() {
        val seed = Color(0xFF3D7BFF.toInt())
        val darkPrimary = buildScheme(seed, dark = true).primary
        val lightPrimary = buildScheme(seed, dark = false).primary
        assertTrue(perceivedLuminance(darkPrimary) > perceivedLuminance(lightPrimary))
    }

    @Test
    fun `built primary stays readable against its own dark surface`() {
        val scheme = buildScheme(Color(0xFF00B4A0.toInt()), dark = true)
        assertTrue(perceivedLuminance(scheme.primary) >= 0.35f)
        assertTrue(perceivedLuminance(scheme.onSurface) > perceivedLuminance(scheme.surface))
    }

    @Test
    fun `seed changes the resulting scheme`() {
        val a = buildScheme(Color(0xFFE5484D.toInt()), dark = true)
        val b = buildScheme(Color(0xFF3BA55D.toInt()), dark = true)
        val defaults = buildScheme(null, dark = true)
        assertNotEquals(a.primary, b.primary)
        assertNotEquals(a.primary, defaults.primary)
    }

    @Test
    fun `warm seed still yields cool tinted surfaces`() {
        // 冷调改造的核心约束：封面再暖，页面底色与强调色也必须偏蓝，
        // 否则整个界面会随封面回到暖色，冷调就不成立。
        listOf(true, false).forEach { dark ->
            val scheme = buildScheme(Color(0xFFE5484D.toInt()), dark = dark)
            assertTrue("surface 偏暖 (dark=$dark)", scheme.surface.blue > scheme.surface.red)
            assertTrue("primary 偏暖 (dark=$dark)", scheme.primary.blue > scheme.primary.red)
        }
    }

    @Test
    fun `adjustForContrast brightens a dark color on dark theme`() {
        val color = Color(0xFF333333.toInt())
        val before = perceivedLuminance(color)
        val after = perceivedLuminance(adjustForContrast(color, dark = true, minContrast = 4.5f))
        assertTrue(after > before)
        assertTrue(after >= 0.35f)
    }

    @Test
    fun `adjustForContrast darkens a light color on light theme`() {
        val color = Color(0xFFEEEEEE.toInt())
        val before = perceivedLuminance(color)
        val after = perceivedLuminance(adjustForContrast(color, dark = false, minContrast = 4.5f))
        assertTrue(after < before)
        assertTrue(after < 0.5f)
    }

    @Test
    fun `normalizeSeed clamps luminance into readable band`() {
        val tooDark = perceivedLuminance(normalizeSeed(Color(0xFF050505.toInt())))
        val tooBright = perceivedLuminance(normalizeSeed(Color(0xFFFDFDFD.toInt())))
        assertTrue("too dark: $tooDark", tooDark >= 0.30f)
        assertTrue("too bright: $tooBright", tooBright <= 0.82f)
    }

    @Test
    fun `enum key parsing round trips and falls back`() {
        assertEquals(ThemeMode.System, ThemeMode.fromKey(ThemeMode.System.key))
        assertEquals(ThemeMode.Dark, ThemeMode.fromKey(ThemeMode.Dark.key))
        assertEquals(ThemeMode.Light, ThemeMode.fromKey(ThemeMode.Light.key))
        assertEquals(ThemeMode.System, ThemeMode.fromKey("bogus"))
        assertEquals(ThemeMode.System, ThemeMode.fromKey(null))

        assertEquals(SeedSource.Cover, SeedSource.fromKey(SeedSource.Cover.key))
        assertEquals(SeedSource.Wallpaper, SeedSource.fromKey(SeedSource.Wallpaper.key))
        assertEquals(SeedSource.Fixed, SeedSource.fromKey(SeedSource.Fixed.key))
        assertEquals(SeedSource.Cover, SeedSource.fromKey("bogus"))
        assertEquals(SeedSource.Cover, SeedSource.fromKey(null))
    }

    @Test
    fun `theme spec defaults`() {
        val spec = ThemeSpec()
        // 默认观感是冷调深色，不随系统浅色跑掉
        assertEquals(ThemeMode.Dark, spec.mode)
        assertEquals(SeedSource.Cover, spec.seedSource)
        assertEquals(ThemeSpec.DEFAULT_SEED_ARGB, spec.fixedSeedArgb)
        assertEquals(false, spec.separatePlayerTheme)
        assertEquals(SeedSource.Cover, spec.playerSeedSource)
        assertEquals(ThemeSpec.DEFAULT_SEED_ARGB, spec.playerFixedSeedArgb)
    }

    @Test
    fun `perceived luminance matches wcag weights`() {
        assertEquals(1f, perceivedLuminance(Color.White), delta)
        assertEquals(0f, perceivedLuminance(Color.Black), delta)
    }
}
