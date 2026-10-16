package com.musicbox.app.data

import com.google.gson.reflect.TypeToken
import com.musicbox.app.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** 一条收藏记录。带上收藏时间是为了排序稳定（新的在前）。 */
data class FavoriteEntry(val song: Song, val addedAtMs: Long)

/**
 * 收藏仓储。全量 JSON 落盘（数据量小），每次变更写一次。
 * UI 通过 [favorites] 的 StateFlow 订阅。
 */
class FavoriteRepository(
    private val store: JsonFileStore<List<FavoriteEntry>>,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val _favorites = MutableStateFlow(store.read().orEmpty())
    val favorites: StateFlow<List<FavoriteEntry>> = _favorites.asStateFlow()

    fun isFavorite(songId: Long): Boolean = _favorites.value.any { it.song.id == songId }

    /** 收藏 / 取消收藏，返回操作后是否处于"已收藏"。 */
    fun toggle(song: Song): Boolean {
        val current = _favorites.value
        val exists = current.any { it.song.id == song.id }
        val next = if (exists) {
            current.filterNot { it.song.id == song.id }
        } else {
            listOf(FavoriteEntry(song, clock())) + current
        }
        persist(next)
        return !exists
    }

    fun remove(songId: Long) {
        persist(_favorites.value.filterNot { it.song.id == songId })
    }

    private fun persist(list: List<FavoriteEntry>) {
        _favorites.value = list
        store.write(list)
    }

    companion object {
        /** 统一在这里声明 Gson 的泛型 Type，避免各处各写一份。 */
        val listType: java.lang.reflect.Type =
            object : TypeToken<List<FavoriteEntry>>() {}.type

        fun forDir(dir: File): FavoriteRepository =
            FavoriteRepository(JsonFileStore(File(dir, "favorites.json"), listType))
    }
}
