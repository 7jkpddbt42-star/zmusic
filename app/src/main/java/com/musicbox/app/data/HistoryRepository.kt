package com.musicbox.app.data

import com.google.gson.reflect.TypeToken
import com.musicbox.app.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** 一条播放历史。 */
data class HistoryEntry(val song: Song, val playedAtMs: Long)

/**
 * 播放历史仓储。同一首歌重复播放 → 挪到最前（去重），总条数封顶。
 */
class HistoryRepository(
    private val store: JsonFileStore<List<HistoryEntry>>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxEntries: Int = 200,
) {

    private val _history = MutableStateFlow(store.read().orEmpty())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    fun record(song: Song) {
        val next = (
            listOf(HistoryEntry(song, clock())) +
                _history.value.filterNot { it.song.id == song.id }
            ).take(maxEntries)
        _history.value = next
        store.write(next)
    }

    fun clear() {
        _history.value = emptyList()
        store.write(emptyList())
    }

    companion object {
        val listType: java.lang.reflect.Type =
            object : TypeToken<List<HistoryEntry>>() {}.type

        fun forDir(dir: File): HistoryRepository =
            HistoryRepository(JsonFileStore(File(dir, "history.json"), listType))
    }
}
