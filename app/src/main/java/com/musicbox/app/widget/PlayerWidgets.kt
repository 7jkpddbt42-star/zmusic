package com.musicbox.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.view.View
import android.widget.RemoteViews
import com.musicbox.app.MainActivity
import com.musicbox.app.R
import com.musicbox.app.lyric.LyricParser
import com.musicbox.app.lyric.LyricTimeline
import com.musicbox.app.lyric.currentLineTextAt
import com.musicbox.app.lyric.sharedLyricCache
import com.musicbox.app.player.ACTION_WIDGET_NEXT
import com.musicbox.app.player.ACTION_WIDGET_PREVIOUS
import com.musicbox.app.player.ACTION_WIDGET_TOGGLE
import com.musicbox.app.player.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** 小组件要显示的一屏内容。存在 SharedPreferences，进程被杀后重进也能立刻画出上一态。 */
data class WidgetSnapshot(
    val title: String = "",
    val subtitle: String = "",
    val lyric: String = "",
    val isPlaying: Boolean = false,
    val hasSong: Boolean = false,
)

/**
 * 小组件快照与封面文件。
 *
 * 封面不能直接给 RemoteViews 一个 http 地址（桌面进程不会替我们下载），
 * 也不方便走 content:// 授权，所以在播放服务里提前把封面裁好、圆角处理好存成本地小图，
 * 更新时只读文件 + `setImageViewBitmap`。
 */
object WidgetStore {

    private const val PREFS = "widget_state"
    private const val KEY_TITLE = "title"
    private const val KEY_SUBTITLE = "subtitle"
    private const val KEY_LYRIC = "lyric"
    private const val KEY_PLAYING = "playing"
    private const val KEY_HAS_SONG = "has_song"

    private const val COVER_EDGE_PX = 192

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    // 解码后的封面缓存：歌词每几秒刷新一次，不必每次都从磁盘解码
    @Volatile
    private var cachedCover: Bitmap? = null

    @Volatile
    private var cachedCoverStamp: Long = -1L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun coverFile(context: Context): File =
        File(context.applicationContext.filesDir, "widget/cover.png")

    fun read(context: Context): WidgetSnapshot {
        val p = prefs(context)
        return WidgetSnapshot(
            title = p.getString(KEY_TITLE, null).orEmpty(),
            subtitle = p.getString(KEY_SUBTITLE, null).orEmpty(),
            lyric = p.getString(KEY_LYRIC, null).orEmpty(),
            isPlaying = p.getBoolean(KEY_PLAYING, false),
            hasSong = p.getBoolean(KEY_HAS_SONG, false),
        )
    }

    fun write(context: Context, snapshot: WidgetSnapshot) {
        prefs(context).edit()
            .putString(KEY_TITLE, snapshot.title)
            .putString(KEY_SUBTITLE, snapshot.subtitle)
            .putString(KEY_LYRIC, snapshot.lyric)
            .putBoolean(KEY_PLAYING, snapshot.isPlaying)
            .putBoolean(KEY_HAS_SONG, snapshot.hasSong)
            .apply()
    }

    fun cover(context: Context): Bitmap? {
        val file = coverFile(context)
        if (!file.isFile) {
            cachedCover = null
            cachedCoverStamp = -1L
            return null
        }
        val stamp = file.lastModified()
        cachedCover?.let { if (stamp == cachedCoverStamp) return it }
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }
            .getOrNull()
            ?.also {
                cachedCover = it
                cachedCoverStamp = stamp
            }
    }

    fun clearCover(context: Context) {
        cachedCover = null
        cachedCoverStamp = -1L
        runCatching { coverFile(context).delete() }
    }

    /**
     * 下载封面 → 居中裁方 → 缩到 [COVER_EDGE_PX] → 圆角 → 存盘。
     * 返回 false 表示本次没有新封面（无地址 / 下载失败），不影响其它内容更新。
     */
    suspend fun saveCover(context: Context, url: String?): Boolean = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) {
            clearCover(context)
            return@withContext false
        }
        val bytes = runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) null else response.body.bytes()
            }
        }.getOrNull() ?: return@withContext false

        val decoded = runCatching { decodeSampled(bytes, COVER_EDGE_PX) }.getOrNull()
            ?: return@withContext false
        val rounded = runCatching { squareWithRoundCorners(decoded, COVER_EDGE_PX) }.getOrNull()
            ?: return@withContext false

        val target = coverFile(context)
        runCatching {
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.outputStream().use { out -> rounded.compress(Bitmap.CompressFormat.PNG, 100, out) }
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
        }.isSuccess
    }

    private fun decodeSampled(bytes: ByteArray, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / (sample * 2) >= maxEdge) sample *= 2
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }

    private fun squareWithRoundCorners(source: Bitmap, size: Int): Bitmap {
        val side = minOf(source.width, source.height)
        val left = (source.width - side) / 2
        val top = (source.height - side) / 2
        val square = Bitmap.createBitmap(source, left, top, side, side)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val shader = BitmapShader(square, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        shader.setLocalMatrix(Matrix().apply { setScale(size.toFloat() / side, size.toFloat() / side) })
        paint.shader = shader
        val radius = size * 0.16f
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, paint)
        return output
    }
}

/**
 * 桌面小组件。按钮通过显式服务 Intent 发给 [PlaybackService]，由服务的
 * onStartCommand 处理；不依赖 MediaButtonReceiver（它在 API 26+ 只接受播放/暂停键）。
 */
class PlayerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { render(context, appWidgetManager, it) }
        // 组件是在播放过程中添加的：快照有歌名与播放态，但封面 / 歌词要服务补一次
        WidgetBridge.updater?.onSongChanged()
    }

    companion object {

        fun hasWidgets(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            return manager.getAppWidgetIds(
                ComponentName(context, PlayerWidgetProvider::class.java),
            ).isNotEmpty()
        }

        /** 全量刷新已放置的小组件；没有放置时直接返回。 */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, PlayerWidgetProvider::class.java),
            )
            ids.forEach { render(context, manager, it) }
        }

        private fun render(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_player)
            val snapshot = WidgetStore.read(context)

            views.setTextViewText(
                R.id.widget_title,
                snapshot.title.ifBlank { context.getString(R.string.widget_idle_title) },
            )
            views.setTextViewText(
                R.id.widget_artist,
                snapshot.subtitle.ifBlank { context.getString(R.string.widget_idle_subtitle) },
            )

            // 歌词只在有歌且有内容时显示，剩余空间由歌名/歌手占满
            if (snapshot.hasSong && snapshot.lyric.isNotBlank()) {
                views.setTextViewText(R.id.widget_lyric, snapshot.lyric)
                views.setViewVisibility(R.id.widget_lyric, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_lyric, View.GONE)
            }

            val cover = if (snapshot.hasSong) WidgetStore.cover(context) else null
            if (cover != null) {
                views.setImageViewBitmap(R.id.widget_cover, cover)
            }

            val playIcon = if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            views.setImageViewResource(R.id.widget_play, playIcon)
            // RemoteViews#setContentDescription 是 Android 12 才有的 API，旧版本跳过即可
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setContentDescription(
                    R.id.widget_play,
                    context.getString(
                        if (snapshot.isPlaying) R.string.widget_pause_desc else R.string.widget_play_desc,
                    ),
                )
            }

            views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
            views.setOnClickPendingIntent(
                R.id.widget_prev,
                action(context, ACTION_WIDGET_PREVIOUS, 1),
            )
            views.setOnClickPendingIntent(
                R.id.widget_play,
                action(context, ACTION_WIDGET_TOGGLE, 2),
            )
            views.setOnClickPendingIntent(
                R.id.widget_next,
                action(context, ACTION_WIDGET_NEXT, 3),
            )

            manager.updateAppWidget(appWidgetId, views)
        }

        private fun openApp(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
            }
            return PendingIntent.getActivity(
                context,
                10,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        private fun action(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, PlaybackService::class.java).setAction(action)
            return PendingIntent.getService(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}

/** 小组件刷新节奏：歌词每 1 秒对一次时间轴，只有文本变化时才真正重画。 */
private const val TICK_MS = 1000L

/**
 * 桌面组件与播放服务在同一个进程里，用一张静态表接上服务侧的驱动器：
 * 新增组件的 onUpdate 无法自己拿到 player，只能通过这里让服务补封面与歌词。
 */
object WidgetBridge {
    @Volatile
    var updater: WidgetUpdater? = null
}

/**
 * 播放服务侧的小组件驱动器。
 *
 * 服务是唯一知道"当前歌 / 播放位置 / 播放态"的地方，所以由它写快照并刷新桌面。
 * 封面与歌词在 IO 协程里准备，主线程只做一次 RemoteViews 更新。
 */
class WidgetUpdater(
    private val context: Context,
    private val container: com.musicbox.app.AppContainer,
    private val player: androidx.media3.common.Player,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val mainHandler: Handler,
) {

    private var timeline: LyricTimeline? = null
    private var lyricSongId: Long? = null
    private var lastLyric: String = ""
    private var ticking = false

    private val ticker = object : Runnable {
        override fun run() {
            ticking = false
            if (!PlayerWidgetProvider.hasWidgets(context)) return
            if (!player.isPlaying) return
            val line = timeline?.currentLineTextAt(player.currentPosition, false).orEmpty()
            if (line != lastLyric) {
                lastLyric = line
                push()
            }
            startTicker()
        }
    }

    /** 切歌：重置歌词、刷新快照与封面，再异步拉新歌的歌词。 */
    fun onSongChanged() {
        val songId = player.currentMediaItem?.mediaId?.toLongOrNull()
        timeline = null
        lastLyric = ""
        lyricSongId = songId
        // 快照与组件无关地始终写：用户播放到一半才添加组件时，桌面能立刻拿到歌名与播放态
        push()
        // 封面下载与歌词拉取只服务桌面组件，没有组件时不做
        if (!PlayerWidgetProvider.hasWidgets(context)) {
            stopTicker()
            return
        }
        if (songId == null) {
            WidgetStore.clearCover(context)
            PlayerWidgetProvider.updateAll(context)
            stopTicker()
            return
        }
        loadCover(songId)
        loadLyric(songId)
        startTicker()
    }

    /** 播放 / 暂停：只改按钮图标与播放态，不动歌词。 */
    fun onPlayStateChanged() {
        // 同上：暂停态也要落盘，否则桌面会停在"正在播放"的假状态
        push()
        if (player.isPlaying && PlayerWidgetProvider.hasWidgets(context)) startTicker() else stopTicker()
    }

    /** 服务退出：别让桌面停在"正在播放"的假状态。 */
    fun release() {
        stopTicker()
        WidgetStore.write(context, WidgetStore.read(context).copy(isPlaying = false))
        PlayerWidgetProvider.updateAll(context)
    }

    private fun push() {
        val item = player.currentMediaItem
        val meta = item?.mediaMetadata
        val title = meta?.title?.toString().orEmpty()
        if (item == null || title.isBlank()) {
            WidgetStore.write(
                context,
                WidgetSnapshot(
                    title = "",
                    subtitle = "",
                    lyric = "",
                    isPlaying = false,
                    hasSong = false,
                ),
            )
        } else {
            WidgetStore.write(
                context,
                WidgetSnapshot(
                    title = title,
                    subtitle = meta?.artist?.toString().orEmpty(),
                    lyric = lastLyric,
                    isPlaying = player.isPlaying,
                    hasSong = true,
                ),
            )
        }
        PlayerWidgetProvider.updateAll(context)
    }

    private fun startTicker() {
        if (ticking) return
        ticking = true
        mainHandler.postDelayed(ticker, TICK_MS)
    }

    private fun stopTicker() {
        ticking = false
        mainHandler.removeCallbacks(ticker)
    }

    private fun loadCover(songId: Long) {
        val url = player.currentMediaItem?.mediaMetadata?.artworkUri?.toString()
        scope.launch {
            val saved = WidgetStore.saveCover(context, url)
            if (!saved) return@launch
            withContext(Dispatchers.Main) {
                if (player.currentMediaItem?.mediaId?.toLongOrNull() == songId) {
                    PlayerWidgetProvider.updateAll(context)
                }
            }
        }
    }

    private fun loadLyric(songId: Long) {
        scope.launch {
            val cached = sharedLyricCache.get(songId)
                ?: container.lyricDiskCache.get(songId)?.let { raw ->
                    LyricParser.parse(raw).also { parsed ->
                        if (!parsed.isEmpty) sharedLyricCache.put(songId, parsed)
                    }
                }
            val parsed = cached ?: run {
                val data = container.provider.getLyric(songId).getOrNull() ?: return@launch
                val fresh = withContext(Dispatchers.Default) { LyricParser.parse(data) }
                if (!fresh.isEmpty) {
                    container.lyricDiskCache.put(songId, data)
                    sharedLyricCache.put(songId, fresh)
                }
                fresh
            }
            withContext(Dispatchers.Main) {
                if (lyricSongId != songId) return@withContext
                timeline = parsed.takeUnless { it.isEmpty }
                lastLyric = ""
                if (player.isPlaying) {
                    lastLyric = timeline?.currentLineTextAt(player.currentPosition, false).orEmpty()
                }
                push()
            }
        }
    }
}
