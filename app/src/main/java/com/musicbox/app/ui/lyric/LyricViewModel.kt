package com.musicbox.app.ui.lyric

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicbox.app.lyric.LyricParser
import com.musicbox.app.lyric.LyricTimeline
import com.musicbox.app.lyric.sharedLyricCache
import com.musicbox.app.source.MusicProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 歌词页的四种终态 + 两种中间态。 */
sealed interface LyricUiState {
    /** 还没选歌。 */
    data object Idle : LyricUiState

    /** 正在拉取。 */
    data object Loading : LyricUiState

    /** 拿到并解析成功。 */
    data class Ready(val timeline: LyricTimeline) : LyricUiState

    /** 没有歌词 / 纯音乐（接口成功但无内容）。 */
    data object Empty : LyricUiState

    /** 拉取失败。UI 一律显示"暂无歌词"，绝不影响播放（设计文档 §9）。 */
    data object Failed : LyricUiState
}

/**
 * 歌词状态机：切歌 → 拉取 → 解析 → Ready/Empty/Failed。
 * 失败的兜底是 Empty/Failed 文案，**不影响播放**——歌词是可选层。
 */
class LyricViewModel(
    private val provider: MusicProvider,
    private val cache: com.musicbox.app.lyric.LyricCache = sharedLyricCache,
) : ViewModel() {

    private val _state = MutableStateFlow<LyricUiState>(LyricUiState.Idle)
    val state: StateFlow<LyricUiState> = _state.asStateFlow()

    /** 译文 / 罗马音开关。MI-3 用内存态，MI-4 落 DataStore。 */
    private val _showTranslation = MutableStateFlow(true)
    val showTranslation: StateFlow<Boolean> = _showTranslation.asStateFlow()

    private val _showRomaji = MutableStateFlow(false)
    val showRomaji: StateFlow<Boolean> = _showRomaji.asStateFlow()

    private var loadedSongId: Long? = null
    private var job: Job? = null

    fun toggleTranslation() {
        _showTranslation.value = !_showTranslation.value
    }

    fun toggleRomaji() {
        _showRomaji.value = !_showRomaji.value
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
        loadedSongId = songId
        job?.cancel()

        cache.get(songId)?.let {
            _state.value = toReady(it)
            return
        }

        _state.value = LyricUiState.Loading
        job = viewModelScope.launch {
            val data = provider.getLyric(songId).getOrNull()
            if (loadedSongId != songId) return@launch
            if (data == null) {
                _state.value = LyricUiState.Failed
                return@launch
            }
            val timeline = withContext(Dispatchers.Default) { LyricParser.parse(data) }
            if (loadedSongId != songId) return@launch
            cache.put(songId, timeline)
            _state.value = toReady(timeline)
        }
    }

    private fun toReady(timeline: LyricTimeline): LyricUiState =
        if (timeline.isEmpty) LyricUiState.Empty else LyricUiState.Ready(timeline)
}
