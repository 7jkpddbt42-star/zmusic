package com.musicbox.app.data

import com.musicbox.app.model.LyricData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LyricDiskCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun cache() = LyricDiskCache(tmp.newFolder("lyrics"))

    @Test
    fun `get returns null when absent`() {
        assertNull(cache().get(1L))
    }

    @Test
    fun `put then get round trips all fields`() {
        val c = cache()
        val data = LyricData(yrc = "[0,100]a", lrc = "[00:00]a", translation = "译", romaji = "ro")
        c.put(42L, data)
        assertEquals(data, c.get(42L))
        assertEquals(1, c.size())
    }

    @Test
    fun `clear removes everything`() {
        val c = cache()
        c.put(1L, LyricData("y", null, null, null))
        c.clear()
        assertEquals(0, c.size())
        assertNull(c.get(1L))
    }

    @Test
    fun `corrupt file is discarded and treated as absent`() {
        val dir = tmp.newFolder("lyrics2")
        dir.resolve("7.json").writeText("not json")
        val c = LyricDiskCache(dir)
        assertNull(c.get(7L))
        assertEquals(0, c.size())
    }
}
