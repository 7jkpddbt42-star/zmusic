package com.musicbox.app.lyric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricCacheTest {

    private fun timeline(tag: String) = LyricTimeline(listOf(LyricLine(0, 1000, tag)))

    @Test
    fun `get returns what was put`() {
        val cache = LyricCache(maxEntries = 4)
        cache.put(1L, timeline("a"))
        assertEquals("a", cache.get(1L)!!.lines[0].text)
    }

    @Test
    fun `get returns null for unknown id`() {
        val cache = LyricCache(maxEntries = 4)
        assertNull(cache.get(42L))
    }

    @Test
    fun `evicts least recently used when over capacity`() {
        val cache = LyricCache(maxEntries = 2)
        cache.put(1L, timeline("a"))
        cache.put(2L, timeline("b"))
        cache.get(1L)          // 1 号被访问 → 最近使用，2 号成为最久未使用
        cache.put(3L, timeline("c"))

        assertNull(cache.get(2L))
        assertEquals("a", cache.get(1L)!!.lines[0].text)
        assertEquals("c", cache.get(3L)!!.lines[0].text)
        assertEquals(2, cache.size())
    }
}
