package com.musicbox.app.ui.lyric

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicbox.app.data.LyricDiskCache
import com.musicbox.app.lyric.LyricParser
import com.musicbox.app.lyric.LyricTimeline
import com.musicbox.app.lyric.currentLineTextAt
import com.musicbox.app.lyric.currentTranslationAt
import com.musicbox.app.lyric.nextLineTextAt
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

    /** 迷你播放条展示的"当前行文本"（原文 · 译文），仅在当前行变化时才更新。 */
    private val _currentLineText = MutableStateFlow("")
    val currentLineText: StateFlow<String> = _currentLineText.asStateFlow()

    /** 播放页歌词预览第一排：原文单独一份，便于和译文分成两排排版。 */
    private val _currentLineOriginal = MutableStateFlow("")
    val currentLineOriginal: StateFlow<String> = _currentLineOriginal.asStateFlow()

    /** 播放页歌词预览第二排：译文；关闭译文或无译文时为空串。 */
    private val _currentLineTranslation = MutableStateFlow("")
    val currentLineTranslation: StateFlow<String> = _currentLineTranslation.asStateFlow()

    private var loadedSongId: Long? = null
    private var job: Job? = null

    /** 最近一次的播放位置，用于切换译文开关时立即重算当前行文本。 */
    private var lastPositionMs: Long = 0L

    /** 当前是否有歌曲（[updatePlayback] 注入）——桌面歌词悬浮窗是否该显示的开关。 */
    private var hasCurrent: Boolean = false

    /** 当前是否在播放（[updatePlayback] 注入），供悬浮窗渲染播放/暂停态。 */
    private var playing: Boolean = false

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

    fun setShowTranslation(value: Boolean) {
        _showTranslation.value = value
        // 切换译文开关后立即用最近一次位置重算，迷你条不必等到下一帧位置回调
        updatePosition(lastPositionMs)
    }
    fun setShowRomaji(value: Boolean) { _showRomaji.value = value }

    /**
     * 由外部（已有 250ms 播放位置）驱动，刷新迷你条当前行文本。
     * 仅 [LyricUiState.Ready] 才取时间轴；文本未变化时不写入，避免无谓重组。
     */
    fun updatePosition(positionMs: Long) {
        lastPositionMs = positionMs
        val timeline = (_state.value as? LyricUiState.Ready)?.timeline
        val text = timeline?.currentLineTextAt(positionMs, _showTranslation.value).orEmpty()
        if (text != _currentLineText.value) _currentLineText.value = text
        val original = timeline?.currentLineTextAt(positionMs, includeTranslation = false).orEmpty()
        if (original != _currentLineOriginal.value) _currentLineOriginal.value = original
        val translation = timeline?.currentTranslationAt(positionMs, _showTranslation.value).orEmpty()
        if (translation != _currentLineTranslation.value) _currentLineTranslation.value = translation
        publishOverlay()
    }

    /**
     * 由 UI 收集的播放状态驱动，刷新桌面歌词悬浮窗的可见性与播放态。
     * 与位置驱动解耦：可见性只在"有没有歌 / 播没播"变化时才需要。
     */
    fun updatePlayback(hasCurrent: Boolean, isPlaying: Boolean) {
        this.hasCurrent = hasCurrent
        this.playing = isPlaying
        publishOverlay()
    }

    /**
     * 向 [LyricBus] 发布**行级**悬浮窗状态（[LyricBus] 内部已去重）。
     * 仅"有歌且歌词 Ready"才可见，否则一律发 EMPTY，让悬浮窗隐藏。
     */
    private fun publishOverlay() {
        val timeline = (_state.value as? LyricUiState.Ready)?.timeline
        if (hasCurrent && timeline != null) {
            LyricBus.publish(
                LyricOverlayState(
                    visible = true,
                    currentLine = _currentLineText.value,
                    nextLine = timeline.nextLineTextAt(lastPositionMs, _showTranslation.value),
                    isPlaying = playing,
                ),
            )
        } else {
            LyricBus.publish(LyricOverlayState.EMPTY)
        }
    }

    /** 歌词页"重试"按钮：忽略缓存重新拉。 */
    fun retry() {
        loadedSongId?.let { startLoad(it, useMemoryCache = false) }
    }

    /** 切歌时调用。同一首重复调用直接忽略；null 表示无当前歌 → Idle。 */
    fun load(songId: Long?) {
        if (songId == null) {
            job?.cancel()
            loadedSongId = null
            _currentLineText.value = ""
            _currentLineOriginal.value = ""
            _currentLineTranslation.value = ""
            _state.value = LyricUiState.Idle
            // 无当前歌：桌面歌词悬浮窗立即隐藏
            LyricBus.publish(LyricOverlayState.EMPTY)
            return
        }
        if (songId == loadedSongId) return
        startLoad(songId, useMemoryCache = true)
    }

    private fun startLoad(songId: Long, useMemoryCache: Boolean) {
        loadedSongId = songId
        job?.cancel()
        // 进入新歌的加载（含 Loading）：清空上一首遗留的当前行文本
        _currentLineText.value = ""
        _currentLineOriginal.value = ""
        _currentLineTranslation.value = ""
        // 新歌歌词尚未 Ready：先隐藏悬浮窗，待 Ready 后由位置驱动重新发布
        LyricBus.publish(LyricOverlayState.EMPTY)

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
