package com.musicbox.app.data

import com.musicbox.app.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FavoriteRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 1000L

    private fun repo(): FavoriteRepository =
        FavoriteRepository(
            JsonFileStore(File(tmp.root, "favorites.json"), FavoriteRepository.listType),
            clock = { now++ },
        )

    private fun song(id: Long) = Song(id, "歌$id", listOf("歌手"), "专辑", 1000L, null, "netease")

    @Test
    fun `toggle adds then removes`() {
        val r = repo()
        assertTrue(r.toggle(song(1)))
        assertTrue(r.isFavorite(1))
        assertFalse(r.toggle(song(1)))
        assertFalse(r.isFavorite(1))
        assertTrue(r.favorites.value.isEmpty())
    }

    @Test
    fun `newly added goes first`() {
        val r = repo()
        r.toggle(song(1))
        r.toggle(song(2))
        assertEquals(listOf(2L, 1L), r.favorites.value.map { it.song.id })
    }

    @Test
    fun `persists across instances`() {
        val file = File(tmp.root, "favorites.json")
        val type = FavoriteRepository.listType
        FavoriteRepository(JsonFileStore(file, type)) { 1L }.toggle(song(9))

        val reopened = FavoriteRepository(JsonFileStore(file, type)) { 1L }
        assertTrue(reopened.isFavorite(9))
        assertEquals("歌9", reopened.favorites.value.first().song.name)
    }
}
