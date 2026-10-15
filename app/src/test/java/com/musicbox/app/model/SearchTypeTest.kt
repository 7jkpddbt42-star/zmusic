package com.musicbox.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTypeTest {

    @Test
    fun `search type maps to netease apiValue`() {
        assertEquals(1, SearchType.SONG.apiValue)
        assertEquals(10, SearchType.ALBUM.apiValue)
        assertEquals(100, SearchType.ARTIST.apiValue)
    }
}
