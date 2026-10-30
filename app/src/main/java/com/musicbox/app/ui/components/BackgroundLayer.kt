package com.musicbox.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.musicbox.app.data.BackgroundSpec
import com.musicbox.app.data.BackgroundType
import java.io.File

/** 自定义背景位图解码的最大边长（像素），避免超大原图占满内存。 */
private const val BACKGROUND_MAX_EDGE = 1024

/**
 * 计算两遍解码的采样率：保证解码后最长边 ≤ [maxEdge]，且为 2 的幂。
 * 尺寸非法（≤0）或 [maxEdge] 非法时返回 1。纯函数，便于 JVM 单测。
 */
fun backgroundBitmapInSampleSize(width: Int, height: Int, maxEdge: Int = BACKGROUND_MAX_EDGE): Int {
    if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
    var sample = 1
    val longest = maxOf(width, height)
    while (longest / sample > maxEdge) sample *= 2
    return sample
}

/**
 * 跨页共享的背景视频播放器。
 *
 * 每个页面（发现 / 搜索 / 我的 / 设置 / 播放页 / 歌词页）都各自建一个 ExoPlayer 的话，
 * 切页时会 释放旧的 + 创建新的 + 重新 prepare/解码，叠加首帧等待，就是肉眼可见的卡顿；
 * 页面间转场时还会同时存在两三个实例在解码同一段视频。
 *
 * 这里按路径复用同一个实例，并用引用计数 + 延迟释放兜住「旧页先销毁、新页后挂载」的间隙。
 * 多个页面同时可见（播放页 ⇄ 歌词页交叉淡化）时，由最后挂载的 TextureView 接收帧，
 * 先挂载的那个保留最后一帧画面，转场结束后再切回来继续渲染。
 */
private object BackgroundVideoPool {
    private const val RELEASE_DELAY_MS = 2000L

    private val handler = Handler(Looper.getMainLooper())
    private var releaseTask: Runnable? = null

    private var playerPath: String? = null
    private var player: ExoPlayer? = null
    private var prepared = false
    private var leases = 0
    private val attachedViews = mutableListOf<TextureView>()
    private var boundView: TextureView? = null

    fun acquire(context: Context, path: String): ExoPlayer {
        releaseTask?.let(handler::removeCallbacks)
        releaseTask = null

        val cached = player
        if (cached != null && playerPath == path) {
            leases++
            return cached
        }

        // 换了素材：旧实例作废，重新建一个
        prepared = false
        attachedViews.clear()
        boundView = null
        cached?.let { runCatching { it.clearVideoSurface(); it.release() } }

        val fresh = ExoPlayer.Builder(context).build()
        player = fresh
        playerPath = path
        // 引用计数只增不减地跟踪 acquire/release 配对，换素材不清零，
        // 避免旧页面的 release 把新实例的计数减掉
        leases++
        return fresh
    }

    fun isPrepared(path: String): Boolean = prepared && playerPath == path

    fun markPrepared(path: String) {
        if (playerPath == path) prepared = true
    }

    fun attach(view: TextureView) {
        if (attachedViews.contains(view)) return
        attachedViews.add(view)
        bind(view)
    }

    /**
     * 某个 TextureView 销毁时，把画面还给仍在组合里的另一个视图，
     * 否则播放页在歌词页关闭后会停在最后一帧。
     * 只有画面确实挂在这个视图上时才切换，避免新旧页面交错挂载时的多余重绑。
     */
    fun detach(view: TextureView) {
        attachedViews.remove(view)
        if (boundView !== view) return
        val next = attachedViews.lastOrNull()
        if (next == null) {
            boundView = null
        } else {
            bind(next)
        }
    }

    private fun bind(view: TextureView) {
        boundView = view
        runCatching { player?.setVideoTextureView(view) }
    }

    fun release() {
        leases = (leases - 1).coerceAtLeast(0)
        if (leases > 0) return

        val task = Runnable {
            releaseTask = null
            if (leases > 0) return@Runnable
            attachedViews.clear()
            boundView = null
            val released = player
            player = null
            playerPath = null
            prepared = false
            runCatching { released?.clearVideoSurface(); released?.release() }
        }
        releaseTask = task
        handler.postDelayed(task, RELEASE_DELAY_MS)
    }
}

/**
 * 播放页最底层背景层。
 *
 * 优先级（与设计一致）：视频（可用时）> 自定义图片 > 封面模糊。
 * - 视频：ExoPlayer 播放本机文件，静音循环；绑定 TextureView 渲染。解码/绑定失败 → [onError]。
 * - 图片：BitmapFactory 采样解码后走 [BlurScrimBitmap] 毛玻璃路径；解码失败 → 回退封面模糊。
 * - 封面：复用 [BlurredBackdrop]。
 *
 * 纯装饰层：不拦截点击；任何素材不可用都回退到可用的下一档，不崩溃。
 */
@Composable
fun BackgroundLayer(
    spec: BackgroundSpec,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    coverBlurRadius: Dp = 48.dp,
    scrim: Boolean = true,
) {
    val path = spec.path
    // 视频不可用（解码失败/绑定失败）时回退封面模糊；切换素材时重置
    var videoFailed by remember(spec.path) { mutableStateOf(false) }

    when {
        spec.type == BackgroundType.Video && path != null && !videoFailed ->
            VideoBackground(path = path, onError = { videoFailed = true }, modifier = modifier)

        spec.type == BackgroundType.Image && path != null -> {
            val bitmap = rememberLocalBitmap(path)
            if (bitmap != null) {
                BlurScrimBitmap(
                    bitmap = bitmap,
                    modifier = modifier,
                    blurRadius = coverBlurRadius,
                    scrim = scrim,
                )
            } else {
                BlurredBackdrop(
                    url = coverUrl,
                    modifier = modifier,
                    blurRadius = coverBlurRadius,
                    scrim = scrim,
                )
            }
        }

        else -> BlurredBackdrop(
            url = coverUrl,
            modifier = modifier,
            blurRadius = coverBlurRadius,
            scrim = scrim,
        )
    }
}

/** 从本地文件两遍采样解码位图；文件不存在 / 解码失败返回 null，不抛异常。 */
@Composable
private fun rememberLocalBitmap(path: String?): Bitmap? = remember(path) { decodeLocalBitmap(path) }

private fun decodeLocalBitmap(path: String?): Bitmap? {
    if (path == null) return null
    return try {
        val file = File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val sample = backgroundBitmapInSampleSize(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, options)
    } catch (_: Throwable) {
        null
    }
}

/**
 * 视频背景：ExoPlayer 循环播放本机文件（静音），绑定 [TextureView] 渲染。
 *
 * 未使用 `androidx.media3.ui.PlayerView`（项目未依赖 `media3-ui`），
 * 而是直接用 [Player.setVideoTextureView] 绑定自建 TextureView。
 * 必须用 TextureView 而非 SurfaceView：SurfaceView 的内容在自己的独立合成层上呈现，
 * 不会被 GraphicsLayer.record（液态玻璃的背景捕获）捕获，玻璃折射会采不到视频画面；
 * TextureView 走普通 View 渲染管线，内容可被离屏记录。
 * 任何异常 → [onError]，由上层回退到封面模糊。
 */
@Composable
private fun VideoBackground(path: String, onError: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    // 跨页复用同一个播放器：切页不再重建解码器，视频也从原位置继续播
    val player = remember(path) { BackgroundVideoPool.acquire(context, path) }
    // onError 每次重组都会新建 lambda，用 rememberUpdatedState 避免 effect 依赖抖动
    val currentOnError by rememberUpdatedState(onError)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // 视频不可用（编解码/文件损坏）时静默回退封面模糊，不打断播放
                currentOnError()
            }
        }
        player.addListener(listener)
        onDispose {
            runCatching { player.removeListener(listener) }
            // 播放器的释放延后交给池：页面切换时下一次 acquire 会直接命中缓存
            BackgroundVideoPool.release()
        }
    }

    LaunchedEffect(player, path) {
        // 复用已有实例时不再重新 prepare，否则视频会跳回片头并额外卡一下
        if (BackgroundVideoPool.isPrepared(path)) return@LaunchedEffect
        try {
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            player.repeatMode = Player.REPEAT_MODE_ALL
            player.volume = 0f
            player.playWhenReady = true
            player.prepare()
            BackgroundVideoPool.markPrepared(path)
        } catch (_: Throwable) {
            currentOnError()
        }
    }

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).also { textureView -> BackgroundVideoPool.attach(textureView) }
        },
        modifier = modifier,
        onRelease = { textureView -> BackgroundVideoPool.detach(textureView) },
    )
}
