package com.musicbox.app.ui.library

import androidx.lifecycle.ViewModel
import com.musicbox.app.data.HistoryEntry
import com.musicbox.app.data.HistoryRepository
import com.musicbox.app.model.Song
import kotlinx.coroutines.flow.StateFlow

/**
 * 听歌历史状态。仓储本身就是 StateFlow 源，这里只做转发。
 *
 * 收藏功能已移除：喜欢走网易云「我喜欢的音乐」，由 [com.musicbox.app.ui.home.HomeViewModel] 管理。
 */
class LibraryViewModel(
    private val historyRepo: HistoryRepository,
) : ViewModel() {

    val history: StateFlow<List<HistoryEntry>> = historyRepo.history

    fun recordPlayed(song: Song) = historyRepo.record(song)

    fun clearHistory() = historyRepo.clear()
}
