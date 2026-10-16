package com.musicbox.app.ui.lyric

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicbox.app.data.LyricDiskCache
import com.musicbox.app.lyric.LyricParser
import com.musicbox.app.lyric.LyricTimeline
import com.musicbox.app.lyric.sharedLyricCache
import com.musicbox.app.source.MusicProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 歌词页的四种终态 + 两种中间态。 */
sealed interface LyricUiState {
    data object Idle : LyricUiState
    data object Loading : LyricUiState
    data class Ready(val timeline: LyricTimeline) : LyricUiState
    data object Empty : LyricUiState
    /** 拉取失败。UI 一律显示"暂无歌词"，绝不影响播放（设计文档 §9）。 */
    data object Failed : LyricUiState
}

/**
 * 歌词状态机：切歌 → 内存缓存 → 磁盘缓存 → 联网 → 解析 → Ready/Empty/Failed。
 * 失败**不影响播放**——歌词是可选层。
 *
 * 联网恢复后（[connectivity] 由 false 变 true）若当前处于 Failed，自动重试一次。
 */
class LyricViewModel(
    private val provider: MusicProvider,
    private val memoryCache: com.musicbox.app.lyric.LyricCache = sharedLyricCache,
    private val diskCache: LyricDiskCache? = null,
    private val connectivity: kotlinx.coroutines.flow.Flow<Boolean>? = null,
) : ViewModel() {

    private val _state = MutableStateFlow<LyricUiState>(LyricUiState.Idle)
    val state: StateFlow<LyricUiState> = _state.asStateFlow()

    private val _showTranslation = MutableStateFlow(true)
    val showTranslation: StateFlow<Boolean> = _showTranslation.asStateFlow()

    private val _showRomaji = MutableStateFlow(false)
    val showRomaji: StateFlow<Boolean> = _showRomaji.asStateFlow()

    private var loadedSongId: Long? = null
    private var job: Job? = null

    init {
        connectivity?.let { flow ->
            viewModelScope.launch {
                flow.distinctUntilChanged().collect { online ->
                    val id = loadedSongId ?: return@collect
                    if (online && _state.value is LyricUiState.Failed) {
                        startLoad(id, useMemoryCache = false)
                    }
                }
            }
        }
    }

    fun toggleTranslation() { _showTranslation.value = !_showTranslation.value }
    fun toggleRomaji() { _showRomaji.value = !_showRomaji.value }

    fun setShowTranslation(value: Boolean) { _showTranslation.value = value }
    fun setShowRomaji(value: Boolean) { _showRomaji.value = value }

    /** 歌词页"重试"按钮：忽略缓存重新拉。 */
    fun retry() {
        loadedSongId?.let { startLoad(it, useMemoryCache = false) }
    }

    /** 切歌时调用。同一首重复调用直接忽略；null 表示无当前歌 → Idle。 */
    fun load(songId: Long?) {
        if (songId == null) {
            job?.cancel()
            loadedSongId = null
            _state.value = LyricUiState.Idle
            return
        }
        if (songId == loadedSongId) return
        startLoad(songId, useMemoryCache = true)
    }

    private fun startLoad(songId: Long, useMemoryCache: Boolean) {
        loadedSongId = songId
        job?.cancel()

        if (useMemoryCache) {
            memoryCache.get(songId)?.let {
                _state.value = toReady(it)
                return
            }
            diskCache?.get(songId)?.let { raw ->
                val timeline = LyricParser.parse(raw)
                memoryCache.put(songId, timeline)
                _state.value = toReady(timeline)
                return
            }
        }

        _state.value = LyricUiState.Loading
        job = viewModelScope.launch {
            val result = provider.getLyric(songId)
            if (loadedSongId != songId) return@launch
            val data = result.getOrNull()
            if (data == null) {
                _state.value = LyricUiState.Failed
                return@launch
            }
            // 有内容才落盘：避免把"这首歌其实没歌词"也缓存成文件
            if (listOfNotNull(data.yrc, data.lrc, data.translation, data.romaji).any { it.isNotBlank() }) {
                diskCache?.put(songId, data)
            }
            val timeline = withContext(Dispatchers.Default) { LyricParser.parse(data) }
            if (loadedSongId != songId) return@launch
            memoryCache.put(songId, timeline)
            _state.value = toReady(timeline)
        }
    }

    private fun toReady(timeline: LyricTimeline): LyricUiState =
        if (timeline.isEmpty) LyricUiState.Empty else LyricUiState.Ready(timeline)
}
