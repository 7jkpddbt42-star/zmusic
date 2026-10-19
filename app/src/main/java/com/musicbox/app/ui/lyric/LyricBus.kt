package com.musicbox.app.ui.lyric

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 桌面歌词悬浮窗需要的最小状态。
 *
 * 注意：**只到"行"级**——当前行、下一行、是否可见、是否在播放。
 * 绝不含逐帧（wordProgress / positionMs）数据，避免高频跨进程/跨层写入。
 */
data class LyricOverlayState(
    val visible: Boolean = false,
    val currentLine: String = "",
    val nextLine: String = "",
    val isPlaying: Boolean = false,
) {
    companion object {
        /** 不可见、无文本、未播放。切歌/清空时统一发布它。 */
        val EMPTY: LyricOverlayState = LyricOverlayState()
    }
}

/**
 * 歌词进程内总线。UI 侧（[LyricViewModel]）发布行级状态，桌面歌词服务（同进程）
 * 订阅并自行渲染。单例 object → 服务与 Activity 共享同一实例。
 *
 * [publish] 内部做去重：值相同不写，避免无谓唤醒订阅者。
 */
object LyricBus {
    private val _state = MutableStateFlow(LyricOverlayState.EMPTY)
    val state: StateFlow<LyricOverlayState> = _state.asStateFlow()

    fun publish(s: LyricOverlayState) {
        if (s != _state.value) _state.value = s
    }
}
