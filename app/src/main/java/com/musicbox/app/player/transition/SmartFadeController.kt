package com.musicbox.app.player.transition

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 智能淡入淡出。
 *
 * 单播放器没法像 DJ 软件那样让两首歌同时响（那需要双解码器），所以这里的过渡是：
 * 上一首按节拍淡出 → 切歌 → 下一首按节拍淡入，中间没有硬切也没有静音空洞。
 *
 * 「智能」体现在三处（数据来自 [TrackAnalyzer]）：
 * 1. 淡化时长按拍数算（淡出 4 拍、淡入 2 拍），快歌短、慢歌长，而不是固定 3 秒；
 * 2. 下一首如果片头有静音，淡入推迟到真正出声的位置，音量抬升落在音乐上；
 * 3. 手动切歌也先淡出再切，避免上一首被拦腰截断的爆音感。
 *
 * 所有音量控制都集中在服务侧这一处，UI 不直接改音量，避免两个地方抢着设置
 * player.volume 导致音量来回跳。
 */
class SmartFadeController(
    private val player: Player,
    /** 读设置：关闭时完全不介入音量，且把可能残留的低音量恢复成 1。 */
    private val enabled: () -> Boolean,
    /** 按歌曲 id 取分析结果；还没分析完时返回 null，走默认时长。 */
    private val analysisOf: (Long) -> TrackAnalysis?,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var rampJob: Job? = null
    private var watchJob: Job? = null

    /** 已经开始淡出的曲目下标：保证一首歌只淡出一次（seek 回去也不重复触发）。 */
    private var fadedOutIndex = -1

    fun start() {
        if (watchJob != null) return
        watchJob = scope.launch {
            while (isActive) {
                runCatching { watchEndOfTrack() }
                delay(WATCH_INTERVAL_MS)
            }
        }
    }

    fun release() {
        scope.cancel()
        rampJob = null
        watchJob = null
    }

    /** 切歌完成：立即从 0 起淡入新歌。 */
    fun onMediaItemTransition(item: MediaItem?) {
        fadedOutIndex = -1
        rampJob?.cancel()
        rampJob = null

        if (!enabled()) {
            player.volume = 1f
            return
        }

        val analysis = item?.mediaId?.toLongOrNull()?.let(analysisOf)
        val durationMs = TransitionMath.fadeInMs(analysis)
        // 片头静音：先保持 0 音量，等播放位置越过静音段再开始抬升
        val introMs = analysis?.introStartMs ?: 0L

        rampJob = scope.launch {
            player.volume = 0f
            var waited = 0L
            while (isActive && waited < introMs) {
                if (player.isPlaying) waited += POSITION_POLL_MS
                delay(POSITION_POLL_MS)
            }
            rampTo(1f, durationMs)
        }
    }

    /** 手动上一首 / 下一首：先淡出再切。 */
    fun skipWithFade(forward: Boolean) {
        val switch = {
            if (forward) player.seekToNextMediaItem() else player.seekToPreviousMediaItem()
        }
        if (!enabled() || !player.isPlaying) {
            switch()
            return
        }
        rampJob?.cancel()
        rampJob = scope.launch {
            rampTo(0f, SKIP_FADE_MS)
            switch()
        }
    }

    /**
     * 播放中每 120ms 看一眼剩余时长：进入淡出窗口就开始降音量，
     * 让音量正好在曲末归零，随后由切歌回调接管淡入。
     */
    private fun watchEndOfTrack() {
        if (!enabled()) {
            // 关掉过渡后如果音量还停在淡出中途，直接恢复，否则会一直是小声
            if (rampJob?.isActive != true && player.volume < 1f) player.volume = 1f
            return
        }
        val duration = player.duration
        if (duration <= 0 || !player.isPlaying) return
        val index = player.currentMediaItemIndex
        if (index == fadedOutIndex) return
        val songId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return

        val fadeMs = TransitionMath.fadeOutMs(analysisOf(songId))
        val remaining = duration - player.currentPosition
        if (remaining in 1..fadeMs) {
            fadedOutIndex = index
            rampJob?.cancel()
            rampJob = scope.launch { rampTo(0f, remaining.coerceAtLeast(MIN_RAMP_MS)) }
        }
    }

    /** 从当前音量线性过渡到 [target]；用 smoothstep 让起步与收尾更柔和。 */
    private suspend fun rampTo(target: Float, durationMs: Long) {
        val from = player.volume
        val startedAt = SystemClock.elapsedRealtime()
        val span = durationMs.coerceAtLeast(MIN_RAMP_MS)
        while (true) {
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            val t = (elapsed.toFloat() / span).coerceIn(0f, 1f)
            val eased = t * t * (3f - 2f * t)
            player.volume = from + (target - from) * eased
            if (t >= 1f) break
            delay(RAMP_STEP_MS)
        }
        player.volume = target
    }

    private companion object {
        const val WATCH_INTERVAL_MS = 120L
        const val RAMP_STEP_MS = 16L
        const val POSITION_POLL_MS = 40L
        const val SKIP_FADE_MS = 420L
        const val MIN_RAMP_MS = 60L
    }
}
