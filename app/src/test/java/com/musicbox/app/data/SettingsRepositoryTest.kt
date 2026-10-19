package com.musicbox.app.data

import com.musicbox.app.ui.theme.SeedSource
import com.musicbox.app.ui.theme.ThemeMode
import com.musicbox.app.ui.theme.ThemeSpec
import org.junit.Assert.assertEquals
import org.junit.Test

/** 内存实现，供单测使用。 */
class InMemoryKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, Any>()
    override fun getString(key: String): String? = map[key] as? String
    override fun putString(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        map[key] as? Boolean ?: defaultValue
    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
    override fun getInt(key: String, defaultValue: Int): Int = map[key] as? Int ?: defaultValue
    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
}

class SettingsRepositoryTest {

    @Test
    fun `defaults are translation on romaji off empty fallback`() {
        val repo = SettingsRepository(InMemoryKeyValueStore())
        assertEquals(true, repo.showTranslation.value)
        assertEquals(false, repo.showRomaji.value)
        assertEquals("", repo.fallbackBaseUrl.value)
    }

    @Test
    fun `writes are persisted and re-read by a new instance`() {
        val store = InMemoryKeyValueStore()
        val repo = SettingsRepository(store)
        repo.setShowTranslation(false)
        repo.setShowRomaji(true)
        repo.setFallbackBaseUrl("  https://example.com/api  ")

        val reopened = SettingsRepository(store)
        assertEquals(false, reopened.showTranslation.value)
        assertEquals(true, reopened.showRomaji.value)
        // 地址两端空白被裁掉
        assertEquals("https://example.com/api", reopened.fallbackBaseUrl.value)
    }

    @Test
    fun `theme spec defaults match theme spec`() {
        val repo = SettingsRepository(InMemoryKeyValueStore())
        val spec = repo.themeSpec.value
        assertEquals(ThemeMode.System, spec.mode)
        assertEquals(SeedSource.Cover, spec.seedSource)
        assertEquals(ThemeSpec.DEFAULT_SEED_ARGB, spec.fixedSeedArgb)
        assertEquals(false, spec.separatePlayerTheme)
        assertEquals(SeedSource.Cover, spec.playerSeedSource)
        assertEquals(ThemeSpec.DEFAULT_SEED_ARGB, spec.playerFixedSeedArgb)
    }

    @Test
    fun `theme spec writes are persisted and re-read by a new instance`() {
        val store = InMemoryKeyValueStore()
        val repo = SettingsRepository(store)
        repo.setThemeMode(ThemeMode.Dark)
        repo.setSeedSource(SeedSource.Fixed)
        repo.setFixedSeedArgb(0xFF00B4A0.toInt())
        repo.setSeparatePlayerTheme(true)
        repo.setPlayerSeedSource(SeedSource.Wallpaper)
        repo.setPlayerFixedSeedArgb(0xFFE5484D.toInt())

        val reopened = SettingsRepository(store)
        val spec = reopened.themeSpec.value
        assertEquals(ThemeMode.Dark, spec.mode)
        assertEquals(SeedSource.Fixed, spec.seedSource)
        assertEquals(0xFF00B4A0.toInt(), spec.fixedSeedArgb)
        assertEquals(true, spec.separatePlayerTheme)
        assertEquals(SeedSource.Wallpaper, spec.playerSeedSource)
        assertEquals(0xFFE5484D.toInt(), spec.playerFixedSeedArgb)
    }

    @Test
    fun `unknown persisted keys fall back to defaults`() {
        val store = InMemoryKeyValueStore()
        store.putString("theme_mode", "nonsense")
        store.putString("seed_source", "nope")
        val repo = SettingsRepository(store)
        assertEquals(ThemeMode.System, repo.themeSpec.value.mode)
        assertEquals(SeedSource.Cover, repo.themeSpec.value.seedSource)
    }
}
