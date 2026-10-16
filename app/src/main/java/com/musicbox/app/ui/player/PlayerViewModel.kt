package com.musicbox.app.ui.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.musicbox.app.model.PlayMode
import com.musicbox.app.model.Song
import com.musicbox.app.player.SongUri
import com.musicbox.app.player.applyTo
import com.musicbox.app.player.sharedSongStreamCache
import com.musicbox.app.source.MusicProvider
import com.musicbox.app.ui.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val provider: MusicProvider,
    private val connection: PlaybackConnection,
) : ViewModel() {

    val state: StateFlow<PlaybackUiState> = connection.state
    val mode: StateFlow<PlayMode> = connection.playMode

    /** 一次性提示（播放失败、模式切换等），由 UI 消费后清空。 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun clearError() = connection.clearError()

    /** 点歌：把整套搜索结果作为队列交给 ExoPlayer，从 startIndex 开始播。 */
    fun play(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty() || startIndex !in songs.indices) return
        viewModelScope.launch {
            val controller = connection.awaitController()
            if (controller == null) {
                _message.value = "播放服务连接中，请稍后重试"
                return@launch
            }

            // 先把当前这首的地址取好并写进共享缓存，让 resolver 打开时不阻塞
            val target = songs[startIndex]
            val info = provider.getStreamUrl(target.id).getOrElse { error ->
                _message.value = error.toUserMessage()
                return@launch
            }
            sharedSongStreamCache.put(target.id, info)

            controller.setMediaItems(
                songs.map { it.toQueueMediaItem() },
                startIndex,
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

    /** 顺序 → 单曲循环 → 随机 轮换，切换后立刻作用到播放器。 */
    fun cyclePlayMode() {
        val controller = connection.controller.value ?: return
        val next = connection.playMode.value.next()
        next.applyTo(controller)
        connection.playMode.value = next
        _message.value = "播放模式：${next.label}"
    }

    fun removeQueueItem(index: Int) {
        val controller = connection.controller.value ?: return
        if (index !in 0 until controller.mediaItemCount) return
        controller.removeMediaItem(index)
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
