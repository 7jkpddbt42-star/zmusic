package com.musicbox.app.data

import com.musicbox.app.model.Song
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HistoryRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun repo(max: Int = 200): HistoryRepository =
        HistoryRepository(
            JsonFileStore(File(tmp.root, "history.json"), HistoryRepository.listType),
            clock = { 1L },
            maxEntries = max,
        )

    private fun song(id: Long) = Song(id, "歌$id", listOf("歌手"), null, 0L, null, "netease")

    @Test
    fun `record puts newest first and dedupes`() {
        val r = repo()
        r.record(song(1))
        r.record(song(2))
        r.record(song(1))
        assertEquals(listOf(1L, 2L), r.history.value.map { it.song.id })
    }

    @Test
    fun `caps at maxEntries`() {
        val r = repo(max = 3)
        (1L..5L).forEach { r.record(song(it)) }
        assertEquals(listOf(5L, 4L, 3L), r.history.value.map { it.song.id })
    }

    @Test
    fun `clear empties list and disk`() {
        val file = File(tmp.root, "history.json")
        val r = HistoryRepository(JsonFileStore(file, HistoryRepository.listType), clock = { 1L })
        r.record(song(1))
        r.clear()

        val reopened =
            HistoryRepository(JsonFileStore(file, HistoryRepository.listType), clock = { 1L })
        assertEquals(0, reopened.history.value.size)
    }
}
