package com.musicbox.app.data

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
}
