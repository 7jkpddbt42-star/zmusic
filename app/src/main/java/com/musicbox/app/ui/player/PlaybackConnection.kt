package com.musicbox.app.ui.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.musicbox.app.model.PlayMode
import com.musicbox.app.player.PlaybackService
import com.musicbox.app.player.PositionSource
import com.musicbox.app.player.applyTo
import com.musicbox.app.player.sharedSongStreamCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 队列里的一项（给 UI 展示用，取自 ExoPlayer timeline 上的 MediaItem 元数据）。 */
data class QueueItem(
    val index: Int,
    val title: String,
    val artist: String,
    val isCurrent: Boolean,
    /** 下一首的封面：黑胶风格左右滑动切歌时要把下一张唱片一起滑出来。 */
    val artworkUrl: String? = null,
)

/** UI 需要的全部播放状态快照。 */
data class PlaybackUiState(
    val connected: Boolean = false,
    val hasCurrent: Boolean = false,
    /** 当前播放歌曲的音源 id，供搜索列表高亮用。 */
    val currentSongId: Long? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val artworkUrl: String? = null,
    val isPlaying: Boolean = false,
    val isTrial: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<QueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val errorMessage: String? = null,
)

/**
 * MediaController 的连接与状态汇聚。UI 只跟它打交道，不直接接触 ExoPlayer。
 *
 * 位置每 250 毫秒采一次：进度条不需要逐帧精度。逐字歌词的逐帧时钟另走
 * [com.musicbox.app.player.PositionSource]（见 FrameClock）。
 */
class PlaybackConnection(
    private val context: Context,
    initialMode: PlayMode = PlayMode.ORDER,
    /** 模式变化回写偏好，重启后沿用上次选择。 */
    private val onModeChange: (PlayMode) -> Unit = {},
) : PositionSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var future: ListenableFuture<MediaController>? = null
    private var ticker: Job? = null

    /** 播放模式。放在连接层而不是 Service：它是 UI 偏好，用不着跨进程。 */
    val playMode = MutableStateFlow(initialMode)

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh(player)

        override fun onPlayerError(error: PlaybackException) {
            // 取流失败 / 版权不可播：显式提示并自动跳下一首（设计文档 §9）
            val isNetwork = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
            val text = if (isNetwork) {
                "网络异常，正在尝试下一首"
            } else {
                "当前歌曲无法播放，已跳过"
            }
            _state.update { it.copy(errorMessage = text) }
            val current = _controller.value
            if (current != null && current.hasNextMediaItem()) {
                current.seekToNextMediaItem()
            }
        }
    }

    /** 连接播放服务。幂等：重复调用直接返回。 */
    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val built = MediaController.Builder(context, token).buildAsync()
        future = built
        built.addListener(
            {
                val controller = runCatching { built.get() }.getOrNull() ?: return@addListener
                controller.addListener(listener)
                _controller.value = controller
                refresh(controller)
                startTicker(controller)
            },
            // Context.mainExecutor 需要 API 28，minSdk 是 26，用兼容入口避免低版本崩溃
            { runnable -> ContextCompat.getMainExecutor(context).execute(runnable) },
        )
    }

    /** 顺序 → 单曲循环 → 随机 轮换：立即作用到播放器并回写偏好。 */
    fun cyclePlayMode(): PlayMode {
        val next = playMode.value.next()
        playMode.value = next
        controller.value?.let { next.applyTo(it) }
        onModeChange(next)
        return next
    }

    fun release() {
        ticker?.cancel()
        ticker = null
        _controller.value?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        _controller.value = null
        _state.value = PlaybackUiState()
        scope.cancel()
    }

    /** 等连接就绪。点歌时调用；还没连上就挂起等它连上。 */
    suspend fun awaitController(): MediaController? {
        connect()
        return controller.first { it != null }
    }

    fun clearError() = _state.update { it.copy(errorMessage = null) }

    /**
     * 逐帧可读的当前位置。直接读 MediaController 的 currentPosition（毫秒精度），
     * 供逐字歌词的逐帧时钟调用；与 250ms 的 state.positionMs 是两条独立通路。
     */
    override fun positionMs(): Long =
        _controller.value?.currentPosition?.coerceAtLeast(0L) ?: 0L

    private fun startTicker(controller: MediaController) {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                refresh(controller)
                delay(POSITION_TICK_MS)
            }
        }
    }

    private fun refresh(controller: Player) {
        val item = controller.currentMediaItem
        val metadata = item?.mediaMetadata
        val songId = item?.mediaId?.toLongOrNull()
        val duration = controller.duration
        val index = controller.currentMediaItemIndex
        _state.update { previous ->
            // 队列每 250ms 才刷新一次位置，如果每次都重建整条 QueueItem 列表，
            // 一首歌 30 首队列就是每秒 120 次多余对象分配；只在队列结构真正变化时重建。
            val queue = if (
                previous.queue.size == controller.mediaItemCount &&
                previous.currentIndex == index &&
                previous.currentSongId == songId
            ) {
                previous.queue
            } else {
                queueOf(controller)
            }
            previous.copy(
                connected = true,
                hasCurrent = item != null,
                currentSongId = songId,
                title = metadata?.title?.toString(),
                artist = metadata?.artist?.toString(),
                album = metadata?.albumTitle?.toString(),
                artworkUrl = metadata?.artworkUri?.toString(),
                isPlaying = controller.isPlaying,
                isTrial = songId?.let { sharedSongStreamCache.get(it)?.isTrial } == true,
                positionMs = controller.currentPosition.coerceAtLeast(0),
                durationMs = if (duration > 0) duration else 0L,
                queue = queue,
                currentIndex = index,
            )
        }
    }

    private fun queueOf(controller: Player): List<QueueItem> =
        (0 until controller.mediaItemCount).map { index ->
            val item: MediaItem = controller.getMediaItemAt(index)
            QueueItem(
                index = index,
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString().orEmpty(),
                isCurrent = index == controller.currentMediaItemIndex,
                artworkUrl = item.mediaMetadata.artworkUri?.toString(),
            )
        }

    private companion object {
        const val POSITION_TICK_MS = 250L
    }
}
