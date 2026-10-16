package com.musicbox.app.player

import com.musicbox.app.model.StreamInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongStreamCacheTest {

    private val full = StreamInfo(url = "http://m1.music.126.net/a.mp3", bitrateBps = 320000)
    private val trial =
        StreamInfo(url = "http://m2.music.126.net/b.mp3", bitrateBps = 128012, isTrial = true)

    @Test
    fun `returns stored info and keeps trial flag`() {
        val cache = SongStreamCache()
        cache.put(1L, trial)

        assertEquals(trial, cache.get(1L))
        assertNull(cache.get(2L))
    }

    @Test
    fun `expires after ttl`() {
        var now = 1_000L
        val cache = SongStreamCache(ttlMs = 600_000L) { now }
        cache.put(1L, full)

        now += 599_999L
        assertEquals(full, cache.get(1L))

        now += 2L
        assertNull(cache.get(1L))
        assertEquals(0, cache.size())
    }

    @Test
    fun `evicts least recently used entry beyond capacity`() {
        val cache = SongStreamCache(maxEntries = 2)
        cache.put(1L, full)
        cache.put(2L, full)
        cache.get(1L)       // 1 变成最近使用
        cache.put(3L, full) // 淘汰 2

        assertNull(cache.get(2L))
        assertEquals(full, cache.get(1L))
        assertEquals(full, cache.get(3L))
        assertEquals(2, cache.size())
    }
}
