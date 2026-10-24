package com.musicbox.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 本机累计收听时长。
 *
 * 网易云公开接口没有"账号总收听时长"字段，这里记录 App 自身真实播放过的秒数，
 * 作为「我的」页展示的听歌时长，跨启动持久化。
 * 内存里每秒累加，每满 [FLUSH_SECONDS] 秒才落盘一次并刷新 [totalSeconds]，
 * 避免每秒写 SharedPreferences、也避免 UI 每秒重组。
 */
class ListeningStatsStore(private val store: KeyValueStore) {

    private val _totalSeconds = MutableStateFlow(store.getString(KEY)?.toLongOrNull() ?: 0L)
    val totalSeconds: StateFlow<Long> = _totalSeconds.asStateFlow()

    private var pendingSeconds = 0L

    fun addSeconds(seconds: Long) {
        if (seconds <= 0) return
        pendingSeconds += seconds
        if (pendingSeconds >= FLUSH_SECONDS) flush()
    }

    /** 把未落盘的秒数写进存储；退出时调用，避免丢最后一段。 */
    fun flush() {
        if (pendingSeconds <= 0) return
        val next = _totalSeconds.value + pendingSeconds
        pendingSeconds = 0
        _totalSeconds.value = next
        store.putString(KEY, next.toString())
    }

    private companion object {
        const val KEY = "listening_total_seconds"
        const val FLUSH_SECONDS = 30L
    }
}
