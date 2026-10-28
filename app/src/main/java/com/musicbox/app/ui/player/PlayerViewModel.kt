package com.musicbox.app.ui.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.musicbox.app.data.ListeningStatsStore
import com.musicbox.app.model.PlayMode
import com.musicbox.app.model.Song
import com.musicbox.app.player.SongUri
import com.musicbox.app.player.applyTo
import com.musicbox.app.player.sharedSongStreamCache
import com.musicbox.app.source.MusicProvider
import com.musicbox.app.ui.toUserMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val provider: MusicProvider,
    private val connection: PlaybackConnection,
    private val listeningStats: ListeningStatsStore,
) : ViewModel() {

    val state: StateFlow<PlaybackUiState> = connection.state
    val mode: StateFlow<PlayMode> = connection.playMode

    init {
        // 播放中每秒累计一次本机收听时长，用于「我的」页统计
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                if (connection.state.value.isPlaying) listeningStats.addSeconds(1)
            }
        }
    }

    override fun onCleared() {
        listeningStats.flush()
        super.onCleared()
    }

    /** 一次性提示（播放失败、模式切换等），由 UI 消费后清空。 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun clearError() = connection.clearError()

    /**
     * 点歌：把整套列表作为队列交给 ExoPlayer，从 startIndex 开始播。
     *
     * 先预取目标歌曲地址；如果它不可播（版权 / 数字专辑 / 无地址），
     * 自动向后找同队列里第一首可播的歌，而不是整单失败。
     */
    fun play(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty() || startIndex !in songs.indices) return
        val queue = songs.filter { it.id > 0 }
        if (queue.isEmpty()) {
            _message.value = "队列里没有可播放的歌曲"
            return
        }
        val targetId = songs[startIndex].id
        val firstIndex = queue.indexOfFirst { it.id == targetId }.coerceAtLeast(0)
        viewModelScope.launch {
            val controller = connection.awaitController()
            if (controller == null) {
                _message.value = "播放服务连接中，请稍后重试"
                return@launch
            }

            var playIndex = -1
            var lastError: Throwable? = null
            // 只探测前几首：整张歌单都不可播时，不应该串行打上百个取流请求把用户晾在这里
            val attempts = minOf(queue.size, MAX_STREAM_PREFETCH)
            for (offset in 0 until attempts) {
                val index = (firstIndex + offset) % queue.size
                val info = provider.getStreamUrl(queue[index].id)
                    .getOrElse { error ->
                        lastError = error
                        null
                    }
                if (info != null) {
                    sharedSongStreamCache.put(queue[index].id, info)
                    playIndex = index
                    break
                }
            }
            if (playIndex < 0) {
                _message.value = lastError?.toUserMessage() ?: "队列里没有可播放的歌曲"
                return@launch
            }
            if (playIndex != firstIndex) {
                _message.value = "已跳过不可播放的歌曲"
            }

            controller.setMediaItems(
                queue.map { it.toQueueMediaItem() },
                playIndex,
                C.TIME_UNSET,
            )
            connection.playMode.value.applyTo(controller)
            controller.prepare()
            controller.play()
        }
    }

    fun togglePlay() {
        val controller = connection.controller.value ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun next() {
        connection.controller.value?.seekToNextMediaItem()
    }

    fun previous() {
        connection.controller.value?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        connection.controller.value?.seekTo(positionMs)
    }

    /** 队列面板里点某一首 → 直接跳过去播。 */
    fun selectQueueItem(index: Int) {
        connection.controller.value?.seekTo(index, 0L)
    }

    /** 顺序 → 单曲循环 → 随机 轮换，切换后立刻作用到播放器，并记住选择。 */
    fun cyclePlayMode() {
        val next = connection.cyclePlayMode()
        _message.value = "播放模式：${next.label}"
    }

    fun removeQueueItem(index: Int) {
        val controller = connection.controller.value ?: return
        if (index !in 0 until controller.mediaItemCount) return
        controller.removeMediaItem(index)
    }

    /**
     * 切换音源模式后重新取流：把当前队列项的占位 URI 换一条（带随机 nonce），
     * ExoPlayer 就会重建媒体源、按新模式重新解析地址，同时把播放进度拉回原处。
     * 歌曲、队列、播放模式都不变，只有地址来源换了。
     */
    fun reloadCurrentStream() {
        val controller = connection.controller.value ?: return
        if (controller.mediaItemCount == 0) return
        val index = controller.currentMediaItemIndex
        if (index == C.INDEX_UNSET) return
        val item = controller.getMediaItemAt(index)
        val songId = item.mediaId.toLongOrNull() ?: return
        val position = controller.currentPosition
        val wasPlaying = controller.isPlaying
        val metadata = item.mediaMetadata
        viewModelScope.launch {
            val rebuilt = MediaItem.Builder()
                .setMediaId(item.mediaId)
                .setUri(
                    SongUri.placeholder(
                        songId = songId,
                        nonce = System.currentTimeMillis().toString(),
                    ),
                )
                .setMediaMetadata(metadata)
                .build()
            controller.replaceMediaItem(index, rebuilt)
            controller.prepare()
            controller.seekTo(index, position)
            if (wasPlaying) controller.play()
        }
    }
}

/**
 * Song → 队列 MediaItem。URI 用占位形式，真实地址由 PlaybackService 的
 * ResolvingDataSource 在播放那一刻才换取（见 SongUri 的注释）。
 */
private fun Song.toQueueMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(SongUri.placeholder(id))
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(name)
            .setArtist(artists.joinToString(" / "))
            .setAlbumTitle(album)
            .setArtworkUri(coverUrl?.let(Uri::parse))
            .setIsPlayable(true)
            .build(),
    )
    .build()

/** 起播前最多串行探测多少首，避免整张歌单都不可播时长时间无响应。 */
private const val MAX_STREAM_PREFETCH = 8
