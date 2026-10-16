package com.musicbox.app.ui.search

import com.musicbox.app.model.Song
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelevanceTest {

    private fun song(name: String, artist: String) =
        Song(1, name, listOf(artist), null, 0, null, "netease")

    @Test
    fun `matches when song name contains query`() {
        assertTrue(hasRealMatch("海阔天空", listOf(song("海阔天空", "Beyond"))))
    }

    @Test
    fun `matches when artist contains query`() {
        assertTrue(hasRealMatch("beyond", listOf(song("光辉岁月", "Beyond"))))
    }

    @Test
    fun `no match for gibberish against real looking results`() {
        val fuzzy = listOf(song("随便一首歌", "某歌手"), song("另一首", "另一个"))
        assertFalse(hasRealMatch("asdkjhqwe", fuzzy))
    }

    @Test
    fun `multi token matches if any token hits`() {
        assertTrue(hasRealMatch("周杰伦 稻香", listOf(song("稻香", "周杰伦"))))
    }

    @Test
    fun `blank query counts as match`() {
        assertTrue(hasRealMatch("   ", listOf(song("x", "y"))))
    }

    @Test
    fun `empty result list has no match`() {
        assertFalse(hasRealMatch("海阔天空", emptyList()))
    }
}
