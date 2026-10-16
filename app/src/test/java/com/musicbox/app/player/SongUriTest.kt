package com.musicbox.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongUriTest {

    @Test
    fun `placeholder and parse round trip`() {
        assertEquals("musicbox://song/347368", SongUri.placeholder(347368L))
        assertEquals(347368L, SongUri.songIdOf("musicbox://song/347368"))
    }

    @Test
    fun `non placeholder uri is not a queue song`() {
        assertNull(SongUri.songIdOf("http://m802.music.126.net/a.mp3"))
        assertNull(SongUri.songIdOf("musicbox://song/not-a-number"))
    }
}
