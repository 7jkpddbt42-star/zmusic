package com.musicbox.app.player

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.musicbox.app.AppContainer
import com.musicbox.app.MainActivity
import com.musicbox.app.model.Song
import com.musicbox.app.player.transition.SmartFadeController
import com.musicbox.app.player.transition.TrackAnalyzer
import com.musicbox.app.player.transition.TransitionAnalysisCache
import com.musicbox.app.player.transition.TransitionCommands
import com.musicbox.app.widget.WidgetBridge
import com.musicbox.app.widget.WidgetUpdater
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
    private lateinit var fade: SmartFadeController

    // 预取下一首用：不能占用播放/主线程
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 过渡分析要解码音频，必须离开主线程；与预取分开，避免互相排队
    private val analysisScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val analyzer = TrackAnalyzer()

    // 睡眠定时：必须活在服务里，界面退出后仍要按时暂停
    private val mainHandler = Handler(Looper.getMainLooper())
    private var sleepRunnable: Runnable? = null
    private var pauseAfterCurrent = false

    // 均衡器：挂在自建的 audio session 上，开关与增益都来自设置
    private var audioSessionId = 0
    private var equalizer: Equalizer? = null

    // 桌面小组件：快照 / 封面 / 歌词都由服务侧驱动
    private lateinit var widgetUpdater: WidgetUpdater

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        val upstream = DefaultDataSource.Factory(this)
        val resolving = ResolvingDataSource.Factory(upstream) { spec ->
            spec.withUri(resolveUri(spec.uri))
        }
        // 最近播放离线缓存：已经听过的歌整曲落在本地，断网 / 弱网时直接命中，
        // 也省掉「打开应用 → 拉网 → 卡 1 秒才出声」这段等待。
        val dataSourceFactory = SongDiskCache.get(this)?.let { cache ->
            CacheDataSource.Factory()
                .setCache(cache)
                // 缓存键固定成 songId：真实音频地址带时效与随机参数，拿地址当键永远命中不了
                .setCacheKeyFactory { spec ->
                    SongUri.songIdOf(spec.uri.toString())?.let { "song-$it" } ?: spec.uri.toString()
                }
                .setUpstreamDataSourceFactory(resolving)
                // 缓存损坏时跳过缓存、直接走网络，不要因为缓存问题把整首歌变成播放失败
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } ?: resolving

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
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

        // 倍速是跨会话偏好，服务重建后按上次选择恢复，避免用户重开应用又变回 1.0x
        player.setPlaybackSpeed(container.settings.playbackSpeedNow())

        // 均衡器需要独占的 audio session：自己生成一个，避免挂到全局输出（0）影响别的应用。
        // 必须在首次 prepare 之前设置。
        audioSessionId = getSystemService(AudioManager::class.java).generateAudioSessionId()
        player.setAudioSessionId(audioSessionId)
        applyAudioFx(container.settings.isAudioFxEnabledNow(), container.settings.audioFxBandsNow())

        fade = SmartFadeController(
            player = player,
            enabled = { container.settings.isSmartTransitionEnabledNow() },
            analysisOf = { songId -> TransitionAnalysisCache.get(songId) },
        )

        widgetUpdater = WidgetUpdater(this, container, player, prefetchScope, mainHandler)
        WidgetBridge.updater = widgetUpdater

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // 「播完当前这首」：自动切歌的瞬间暂停，手动切歌不算
                if (pauseAfterCurrent && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    pauseAfterCurrent = false
                    player.pause()
                }
                prefetchNext()
                fade.onMediaItemTransition(mediaItem)
                // 当前歌的过渡分析：淡出要用它的 BPM 与节拍
                mediaItem?.mediaId?.toLongOrNull()?.let { ensureAnalysis(it) }
                // 通知栏心形与桌面小组件跟着切歌刷新
                refreshMediaButtons()
                widgetUpdater.onSongChanged()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                widgetUpdater.onPlayStateChanged()
            }
        })
        fade.start()

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
            .setCallback(sessionCallback)
            .build()
        // 通知栏按钮：上一首 / 下一首 + 喜欢（心形随当前歌状态换图标）
        refreshMediaButtons()
    }

    /**
     * 手动切歌要「先淡出再切」，而 MediaController 的 seekToNextMediaItem 是立即生效的，
     * 所以把切歌包成自定义命令：服务侧先降音量再跳曲，切歌回调里再淡入。
     */
    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            // 自定义命令必须显式加进会话命令集，控制器才允许发送
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(TransitionCommands.ACTION_FADE_SKIP, Bundle.EMPTY))
                .add(SessionCommand(ACTION_SLEEP_TIMER, Bundle.EMPTY))
                .add(SessionCommand(ACTION_AUDIO_FX, Bundle.EMPTY))
                .add(SessionCommand(ACTION_NOTIFY_LIKE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_SLEEP_TIMER) {
                scheduleSleepTimer(args.getInt(ARG_SLEEP_MINUTES, 0))
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == ACTION_AUDIO_FX) {
                applyAudioFx(
                    enabled = args.getBoolean(ARG_FX_ENABLED, false),
                    bands = intArrayOf(
                        args.getInt(ARG_FX_LOW, 0),
                        args.getInt(ARG_FX_MID, 0),
                        args.getInt(ARG_FX_HIGH, 0),
                    ),
                )
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == ACTION_NOTIFY_LIKE) {
                toggleLikeCurrent()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction != TransitionCommands.ACTION_FADE_SKIP) {
                return super.onCustomCommand(session, controller, customCommand, args)
            }
            fade.skipWithFade(args.getBoolean(TransitionCommands.ARG_FORWARD, true))
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    /**
     * 睡眠定时：[minutes] > 0 到点暂停；0 取消；[SLEEP_AFTER_CURRENT] 播完当前这首暂停。
     * 到点只暂停不退出服务，用户还能继续点播放。
     */
    private fun scheduleSleepTimer(minutes: Int) {
        cancelSleepTimer()
        when {
            minutes == SLEEP_AFTER_CURRENT -> pauseAfterCurrent = true
            minutes > 0 -> {
                val task = Runnable {
                    sleepRunnable = null
                    player.pause()
                }
                sleepRunnable = task
                mainHandler.postDelayed(task, minutes * 60_000L)
            }
        }
    }

    private fun cancelSleepTimer() {
        sleepRunnable?.let(mainHandler::removeCallbacks)
        sleepRunnable = null
        pauseAfterCurrent = false
    }

    /**
     * 应用均衡器设置。关闭时释放效果器；设备 / 蓝牙链路不支持 AudioEffect 时
     * 创建会抛异常，这里静默忽略——不能因为音效把播放带崩。
     */
    private fun applyAudioFx(enabled: Boolean, bands: IntArray) {
        if (!enabled) {
            equalizer?.let { eq ->
                runCatching { eq.enabled = false }
                runCatching { eq.release() }
            }
            equalizer = null
            return
        }
        val eq = equalizer ?: runCatching { Equalizer(0, audioSessionId) }.getOrNull() ?: return
        equalizer = eq
        runCatching {
            eq.enabled = true
            // 电平单位是 millibel（1 dB = 100 mB），且必须落在设备给出的范围内
            val range = eq.bandLevelRange
            FX_FREQUENCIES.forEachIndexed { index, hz ->
                val band = nearestBand(eq, hz) ?: return@forEachIndexed
                val millibel = (bands.getOrElse(index) { 0 } * 100)
                    .coerceIn(range[0].toInt(), range[1].toInt())
                eq.setBandLevel(band, millibel.toShort())
            }
        }
    }

    /** 按对数频率距离找最接近 [hz] 的硬件频段（不同设备频段数不同）。 */
    private fun nearestBand(eq: Equalizer, hz: Int): Short? = runCatching {
        var best: Short? = null
        var bestDistance = Float.MAX_VALUE
        for (band in 0 until eq.numberOfBands) {
            val freqRange = eq.getBandFreqRange(band.toShort())
            val center = (freqRange[0] + freqRange[1]) / 2f
            if (center <= 0f) continue
            val distance = kotlin.math.abs(kotlin.math.ln(center / hz))
            if (distance < bestDistance) {
                bestDistance = distance
                best = band.toShort()
            }
        }
        best
    }.getOrNull()

    /**
     * 桌面小组件的三个按钮走显式服务 Intent（见 PlayerWidgetProvider）。
     * 冷启动时队列是空的，处理完就自停，不在后台留一个空服务。
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action != null && action.startsWith(WIDGET_ACTION_PREFIX)) {
            when (action) {
                ACTION_WIDGET_TOGGLE -> if (player.isPlaying) player.pause() else player.play()
                ACTION_WIDGET_NEXT -> fade.skipWithFade(forward = true)
                ACTION_WIDGET_PREVIOUS -> fade.skipWithFade(forward = false)
                ACTION_WIDGET_LIKE -> toggleLikeCurrent()
            }
            if (player.mediaItemCount == 0) {
                stopSelf(startId)
                return START_NOT_STICKY
            }
            return START_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /** 当前曲目 → [Song]，供通知栏喜欢按钮写入本机收藏。 */
    private fun currentSong(): Song? {
        val item = player.currentMediaItem ?: return null
        val id = item.mediaId.toLongOrNull() ?: return null
        val meta = item.mediaMetadata
        val title = meta.title?.toString().orEmpty()
        if (title.isBlank()) return null
        return Song(
            id = id,
            name = title,
            artists = meta.artist?.toString()
                ?.split(" / ")
                ?.filter { it.isNotBlank() }
                .orEmpty(),
            album = meta.albumTitle?.toString(),
            durationMs = meta.durationMs ?: 0L,
            coverUrl = meta.artworkUri?.toString(),
            providerId = "netease",
        )
    }

    /**
     * 通知栏按钮偏好：上一首 / 下一首固定展示，喜欢按钮用心形图标随当前歌状态切换。
     * Media3 的内置图标常量会被默认通知样式直接画出来，不必额外做 drawable。
     */
    private fun refreshMediaButtons() {
        val session = session ?: return
        val song = currentSong()
        val liked = song != null && container.liked.isLiked(song.id)

        val likeButton = CommandButton.Builder(
            if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED,
        )
            .setSessionCommand(SessionCommand(ACTION_NOTIFY_LIKE, Bundle.EMPTY))
            .setDisplayName(if (liked) "取消喜欢" else "喜欢")
            .setEnabled(song != null)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()

        val previous = CommandButton.Builder(CommandButton.ICON_PREVIOUS)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .setDisplayName("上一首")
            .setSlots(CommandButton.SLOT_BACK)
            .build()

        val next = CommandButton.Builder(CommandButton.ICON_NEXT)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .setDisplayName("下一首")
            .setSlots(CommandButton.SLOT_FORWARD)
            .build()

        session.setMediaButtonPreferences(ImmutableList.of(previous, next, likeButton))
    }

    /**
     * 心形点击（通知栏 / 桌面组件共用）：先改本机收藏（两处图标立刻换），登录时再同步到网易云；
     * 同步失败回滚，保证桌面上的心形不会和账号状态长期背离。
     */
    private fun toggleLikeCurrent() {
        val song = currentSong() ?: return
        val nextLiked = container.liked.toggle(song)
        refreshMediaButtons()
        widgetUpdater.onLikeChanged()

        val s = container.sessionStore.session.value
        if (!s.isLoggedIn || s.userId <= 0) return
        prefetchScope.launch {
            container.personalApi.likeSong(song.id, nextLiked, s.userId)
                .onFailure {
                    if (nextLiked) container.liked.remove(song.id) else container.liked.add(song)
                    mainHandler.post {
                        refreshMediaButtons()
                        widgetUpdater.onLikeChanged()
                    }
                }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        cancelSleepTimer()
        fade.release()
        widgetUpdater.release()
        WidgetBridge.updater = null
        prefetchScope.cancel()
        analysisScope.cancel()
        session?.release()
        session = null
        applyAudioFx(enabled = false, bands = IntArray(3))
        player.release()
        // player 释放后再放缓存：DataSource 仍持有缓存实例时提前 release 会打断收尾
        SongDiskCache.release()
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

        // 用户显式下载过的歌直接读本地文件：断网也能播，也不再走缓存 / 网络
        Downloads.get(this).localUri(songId)?.let { return it }

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
        if (sharedSongStreamCache.get(songId) == null) {
            prefetchScope.launch {
                container.provider.getStreamUrl(songId)
                    .onSuccess { sharedSongStreamCache.put(songId, it) }
                    .onSuccess { ensureAnalysis(songId) }
            }
        } else {
            ensureAnalysis(songId)
        }
    }

    /**
     * 过渡分析（BPM / 节拍相位 / 片头起点）。按歌曲缓存，失败也只记一次。
     * 只在「智能过渡」开启时做，避免关掉功能还白耗流量与电量。
     */
    private fun ensureAnalysis(songId: Long) {
        if (!container.settings.isSmartTransitionEnabledNow()) return
        if (TransitionAnalysisCache.isKnown(songId)) return
        analysisScope.launch {
            val url = sharedSongStreamCache.get(songId)?.url
                ?: withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { container.provider.getStreamUrl(songId) }
                    ?.getOrNull()
                    ?.url
            if (url == null) {
                TransitionAnalysisCache.put(songId, null)
                return@launch
            }
            TransitionAnalysisCache.put(songId, analyzer.analyze(url))
        }
    }

    private companion object {
        // 免费听歌模式要串行试几个通道，单次取流给足预算，避免还没试完就被丢弃
        const val RESOLVE_TIMEOUT_MS = 20_000L
        const val MAX_STREAM_ATTEMPTS = 3

        /** 三个滑块对应的目标频率：低音 / 中频 / 高音。 */
        val FX_FREQUENCIES = intArrayOf(100, 1000, 8000)
    }
}

/** 睡眠定时自定义命令：UI 侧发送，参数为分钟数（0 取消，-1 播完当前）。 */
const val ACTION_SLEEP_TIMER = "musicbox.sleep.timer"
const val ARG_SLEEP_MINUTES = "minutes"
const val SLEEP_AFTER_CURRENT = -1

/** 均衡器自定义命令：开关 + 低 / 中 / 高三个频段增益（dB）。 */
const val ACTION_AUDIO_FX = "musicbox.audio.fx"
const val ARG_FX_ENABLED = "fx_enabled"
const val ARG_FX_LOW = "fx_low"
const val ARG_FX_MID = "fx_mid"
const val ARG_FX_HIGH = "fx_high"

/** 通知栏喜欢按钮自定义命令。 */
const val ACTION_NOTIFY_LIKE = "musicbox.notify.like"

/** 桌面小组件按钮：显式服务 Intent 的 action 前缀与四个动作。 */
const val ACTION_WIDGET_TOGGLE = "musicbox.widget.toggle"
const val ACTION_WIDGET_NEXT = "musicbox.widget.next"
const val ACTION_WIDGET_PREVIOUS = "musicbox.widget.previous"
const val ACTION_WIDGET_LIKE = "musicbox.widget.like"
private const val WIDGET_ACTION_PREFIX = "musicbox.widget."
