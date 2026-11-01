package com.musicbox.app.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.musicbox.app.ui.components.MusicIcons
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 观察某 URL 的封面位图，返回可被重组读取的 [Bitmap]?。
 *
 * - 命中 [memoryCache] 时**同步**返回（首次组合即可用，不闪烁）。
 * - 未命中时启动一次异步加载，完成后写入状态并触发重组。
 * - url 为空或加载失败返回 null，不抛异常。
 * - [maxEdgePx] 控制解码尺寸：列表缩略图传小值，避免为大图浪费解码与纹理内存。
 *
 * [ArtworkImage] 与 `BlurredBackdrop` 共用本函数，避免重复的解码与状态逻辑。
 */
@Composable
fun rememberArtworkBitmap(url: String?, maxEdgePx: Int = MAX_EDGE_PX): Bitmap? {
    val key = remember(url, maxEdgePx) { url?.let { cacheKey(it, maxEdgePx) } }
    var bitmap by remember(key) { mutableStateOf(key?.let { memoryCache.get(it) }) }
    // 磁盘缓存需要应用私有目录；用 applicationContext 避免持有 Activity
    val cacheContext = LocalContext.current.applicationContext

    LaunchedEffect(key) {
        val targetUrl = url ?: return@LaunchedEffect
        val cacheKey = key ?: return@LaunchedEffect
        if (bitmap == null) {
            val loaded = load(cacheContext, cacheKey, targetUrl, maxEdgePx).await()
            if (loaded != null) bitmap = loaded
        }
    }

    return bitmap
}

/**
 * 封面异步加载 Composable。自建轻量加载器：OkHttp + BitmapFactory 采样 + 内存 LruCache + 磁盘缓存，
 * 不引入 Coil/Glide，规避新依赖与 Kotlin/AGP 的兼容风险。
 *
 * - 两层缓存：内存约 32MB（跨页面复用）、磁盘约 64MB（跨进程复用）；
 * - 同一 URL 并发只加载一次（[inFlight] 去重）。
 * - url 为空 / 加载失败时显示占位（底色 + 音符），不崩溃。
 * - [onBitmap] 在首次解码成功时回调一次，供 [CoverPalette] 取色。
 */
@Composable
fun ArtworkImage(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    onBitmap: ((Bitmap) -> Unit)? = null,
    maxEdgePx: Int = MAX_EDGE_PX,
) {
    val bitmap = rememberArtworkBitmap(url, maxEdgePx)

    // 解码成功（含缓存命中）即回调一次，供取色使用；url/bitmap 变化时才会重新触发
    LaunchedEffect(url, bitmap) {
        val b = bitmap
        if (b != null) onBitmap?.invoke(b)
    }

    Box(
        modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val shown = bitmap
        if (shown != null) {
            Image(
                bitmap = shown.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = MusicIcons.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/**
 * 默认解码最大边长。播放页封面最大显示约 0.94 × 1080px，取 1080 保证 1:1 不放大；
 * 列表缩略图通过 [THUMB_EDGE_PX] 覆盖，避免为大图浪费解码与纹理内存。
 */
private const val MAX_EDGE_PX = 1080
private const val CACHE_BYTES = 32 * 1024 * 1024

/** 列表缩略图（44–46dp 行）解码尺寸：4x 屏也够清晰。 */
const val THUMB_EDGE_PX = 192

private fun cacheKey(url: String, maxEdgePx: Int): String = "$maxEdgePx|$url"

private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .callTimeout(10, TimeUnit.SECONDS)
    .build()

/** 以字节数计量的内存缓存（约 32MB）。 */
private val memoryCache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

/** 加载协程：并发上限 4，避免快速滚动时几十行同时下载 + 解码把 CPU 打满。 */
private val loaderScope = CoroutineScope(
    SupervisorJob() + Dispatchers.IO.limitedParallelism(4),
)

/** 同一 URL 的在途加载，避免并发重复请求。 */
private val inFlight = ConcurrentHashMap<String, Deferred<Bitmap?>>()

private fun load(context: Context, key: String, url: String, maxEdgePx: Int): Deferred<Bitmap?> {
    memoryCache.get(key)?.let { return CompletableDeferred(it) }
    return inFlight.computeIfAbsent(key) {
        loaderScope.async {
            try {
                // 先读磁盘：冷启动 / 断网时也能立刻拿到上次解码过的封面
                val cached = ArtworkDiskCache.read(context, key)
                if (cached != null) {
                    memoryCache.put(key, cached)
                    return@async cached
                }

                val bytes = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body.bytes()
                }
                val bitmap = bytes?.let { decodeSampled(it, maxEdgePx) }
                if (bitmap != null) {
                    memoryCache.put(key, bitmap)
                    // 写盘是纯 IO，失败只影响下次是否要重新下载，不影响本次显示
                    ArtworkDiskCache.write(context, key, bitmap)
                }
                bitmap
            } catch (t: Throwable) {
                null
            } finally {
                inFlight.remove(key)
            }
        }
    }
}

/**
 * 封面磁盘缓存。
 *
 * 内存缓存随进程结束一起消失，冷启动、断网或回看旧列表时所有封面都会退化成占位色块，
 * 还要重新走一遍网络下载 + 双遍解码。这里把**已经按目标尺寸解码过**的位图压成 WebP 落盘：
 * 再进应用时只需本地解码一次，既省流量也省掉 JPEG 采样缩放那段 CPU。
 *
 * - 文件按「尺寸+URL」的摘要命名，同一首歌的大图与列表缩略图互不覆盖；
 * - 写入先落临时文件再 rename，避免进程被杀时留下半个文件；
 * - 总量超过 [DISK_CACHE_BYTES] 时按最近使用时间淘汰，只在写入时顺带检查（限频 60s）。
 */
private object ArtworkDiskCache {
    private const val DIR_NAME = "artwork"
    private const val DISK_CACHE_BYTES = 64L * 1024 * 1024

    @Volatile
    private var lastTrimAt = 0L

    private val compressFormat: Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

    private fun dirFor(context: Context): File = File(context.cacheDir, DIR_NAME)

    private fun fileFor(context: Context, key: String): File =
        File(dirFor(context), digest(key) + ".webp")

    fun read(context: Context, key: String): Bitmap? {
        val file = fileFor(context, key)
        if (!file.isFile) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }
            .getOrNull()
            ?.also {
                // 标记最近使用，供淘汰时排序
                runCatching { file.setLastModified(System.currentTimeMillis()) }
            }
    }

    fun write(context: Context, key: String, bitmap: Bitmap) {
        val dir = dirFor(context)
        runCatching {
            if (!dir.exists() && !dir.mkdirs()) return
            val target = File(dir, digest(key) + ".webp")
            val tmp = File(dir, target.name + ".tmp")
            tmp.outputStream().use { out ->
                bitmap.compress(compressFormat, WEBP_QUALITY, out)
            }
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) tmp.delete()
            }
        }
        trimIfNeeded(context)
    }

    /** 超过上限就删掉最久未使用的文件，压到 3/4 上限留出余量。 */
    private fun trimIfNeeded(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastTrimAt < TRIM_INTERVAL_MS) return
        lastTrimAt = now

        val files = dirFor(context).listFiles() ?: return
        var total = files.sumOf { it.length() }
        if (total <= DISK_CACHE_BYTES) return
        val target = DISK_CACHE_BYTES * 3 / 4
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= target) break
            total -= file.length()
            file.delete()
        }
    }

    private fun digest(key: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return buildString(bytes.size * 2) {
            for (byte in bytes) append("%02x".format(byte))
        }
    }

    private const val TRIM_INTERVAL_MS = 60_000L
    private const val WEBP_QUALITY = 85
}

/**
 * 两遍解码：先读边界算采样率，再把最长边精确压到 [maxEdgePx]。
 *
 * 关键点：`inSampleSize` 只能取 2 的幂，直接解码会明显小于目标尺寸（例如 1300px 的图
 * 请求 512px 时被降到 325px），放大后必然发糊。这里先用最小采样率保证解码结果
 * **不小于**目标，再用 `inDensity`/`inTargetDensity` 在解码阶段精确缩放到目标边长，
 * 既清晰又不需要额外的大图内存。
 */
private fun decodeSampled(bytes: ByteArray, maxEdgePx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val maxEdge = maxOf(bounds.outWidth, bounds.outHeight)
    if (maxEdge <= 0) return null
    if (maxEdge <= maxEdgePx) {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    var sample = 1
    while (maxEdge / (sample * 2) >= maxEdgePx) sample *= 2
    val sampledEdge = maxEdge / sample
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inScaled = true
        inDensity = sampledEdge
        inTargetDensity = maxEdgePx
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}
