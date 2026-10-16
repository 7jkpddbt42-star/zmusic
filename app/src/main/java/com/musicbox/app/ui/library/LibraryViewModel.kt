package com.musicbox.app.ui.library

import androidx.lifecycle.ViewModel
import com.musicbox.app.data.FavoriteEntry
import com.musicbox.app.data.FavoriteRepository
import com.musicbox.app.data.HistoryEntry
import com.musicbox.app.data.HistoryRepository
import com.musicbox.app.model.Song
import kotlinx.coroutines.flow.StateFlow

/**
 * 收藏 / 历史列表的状态。仓储本身就是 StateFlow 源，这里只做转发与"是否已收藏"查询，
 * 不额外造状态。
 */
class LibraryViewModel(
    private val favoritesRepo: FavoriteRepository,
    private val historyRepo: HistoryRepository,
) : ViewModel() {

    val favorites: StateFlow<List<FavoriteEntry>> = favoritesRepo.favorites
    val history: StateFlow<List<HistoryEntry>> = historyRepo.history

    fun isFavorite(songId: Long): Boolean = favoritesRepo.isFavorite(songId)

    /** 返回操作后是否已收藏，供 UI 提示。 */
    fun toggleFavorite(song: Song): Boolean = favoritesRepo.toggle(song)

    fun recordPlayed(song: Song) = historyRepo.record(song)

    fun clearHistory() = historyRepo.clear()
}
