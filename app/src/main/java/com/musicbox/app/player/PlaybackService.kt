package com.musicbox.app.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.musicbox.app.AppContainer
import com.musicbox.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * 前台播放服务。用 Media3 的 MediaSessionService，通知栏 / 锁屏 / 耳机线控 / 自动续播
 * 都由框架承担，UI 侧只通过 MediaController 观察与控制（见 PlaybackConnection）。
 *
 * 队列里的 MediaItem 用占位 URI（musicbox://song/<id>），真正播放时才由
 * [resolveUri] 向网易云换取真实地址，结果写进 [sharedSongStreamCache]。
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var container: AppContainer

    // 预取下一首用：不能占用播放/主线程
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        val upstream = DefaultDataSource.Factory(this)
        val resolving = ResolvingDataSource.Factory(upstream) { spec ->
            spec.withUri(resolveUri(spec.uri))
        }
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                prefetchNext()
            }
        })

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                // 从通知栏 / 锁屏点进来时回到已有任务并直达播放页，避免叠出第二个 Activity
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        prefetchScope.cancel()
        session?.release()
        session = null
        player.release()
        super.onDestroy()
    }

    /**
     * 把占位 URI 换成真实播放地址。缓存命中直接返回；未命中则阻塞式取流（带超时），
     * 失败时抛 IOException 交给 ExoPlayer 的 onPlayerError 处理（UI 侧会提示并跳下一首）。
     *
     * 这里用 runBlocking 是被允许的简化：resolver 运行在 ExoPlayer 的加载线程上，
     * 不在主线程，也不会与 UI 协程互相等待。
     */
    private fun resolveUri(uri: Uri): Uri {
        val songId = SongUri.songIdOf(uri.toString()) ?: return uri

        sharedSongStreamCache.get(songId)?.let { return Uri.parse(it.url) }

        val info = runBlocking { resolveWithRetry(songId) }
            ?: throw IOException("取流失败 songId=$songId")

        sharedSongStreamCache.put(songId, info)
        return Uri.parse(info.url)
    }

    /**
     * 取流失败自动重连（设计文档 §9：指数退避，最多 3 次）。
     * 退避 0.5s → 1s → 2s，总等待 3.5s，不会让用户等太久，也足以扛过瞬时抖动。
     */
    private suspend fun resolveWithRetry(songId: Long): com.musicbox.app.model.StreamInfo? {
        var backoff = 500L
        repeat(MAX_STREAM_ATTEMPTS) { attempt ->
            val info = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                container.provider.getStreamUrl(songId)
            }?.getOrNull()
            if (info != null) return info
            if (attempt < MAX_STREAM_ATTEMPTS - 1) {
                kotlinx.coroutines.delay(backoff)
                backoff *= 2
            }
        }
        return null
    }

    /** 提前把下一首的地址取好，让切歌时 resolver 直接命中缓存、不阻塞。 */
    private fun prefetchNext() {
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return
        val item = player.getMediaItemAt(nextIndex)
        val songId = item.mediaId.toLongOrNull() ?: return
        if (sharedSongStreamCache.get(songId) != null) return

        prefetchScope.launch {
            container.provider.getStreamUrl(songId)
                .onSuccess { sharedSongStreamCache.put(songId, it) }
        }
    }

    private companion object {
        // 免费听歌模式要串行试几个通道，单次取流给足预算，避免还没试完就被丢弃
        const val RESOLVE_TIMEOUT_MS = 20_000L
        const val MAX_STREAM_ATTEMPTS = 3
    }
}
