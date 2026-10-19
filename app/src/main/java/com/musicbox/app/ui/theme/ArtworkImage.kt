package com.musicbox.app.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 封面异步加载 Composable。自建轻量加载器：OkHttp + BitmapFactory 采样 + 内存 LruCache，
 * 不引入 Coil/Glide，规避新依赖与 Kotlin/AGP 的兼容风险。
 *
 * - 只做内存缓存（约 8MB），跨页面复用；不做磁盘缓存。
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
) {
    var bitmap by remember(url) { mutableStateOf(url?.let { memoryCache.get(it) }) }

    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        if (bitmap == null) {
            val loaded = load(url).await()
            if (loaded != null) bitmap = loaded
        }
    }

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
            Text(text = "♪", style = MaterialTheme.typography.displayMedium)
        }
    }
}

private const val MAX_EDGE_PX = 512
private const val CACHE_BYTES = 8 * 1024 * 1024

private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .callTimeout(10, TimeUnit.SECONDS)
    .build()

/** 以字节数计量的内存缓存（约 8MB）。 */
private val memoryCache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

private val loaderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** 同一 URL 的在途加载，避免并发重复请求。 */
private val inFlight = ConcurrentHashMap<String, Deferred<Bitmap?>>()

private fun load(url: String): Deferred<Bitmap?> {
    memoryCache.get(url)?.let { return CompletableDeferred(it) }
    return inFlight.computeIfAbsent(url) {
        loaderScope.async {
            try {
                val bytes = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body.bytes()
                }
                bytes?.let { decodeSampled(it) }?.also { memoryCache.put(url, it) }
            } catch (t: Throwable) {
                null
            } finally {
                inFlight.remove(url)
            }
        }
    }
}

/** 两遍解码：先读边界算采样率，把最大边压到 ~512px 以内再实际解码。 */
private fun decodeSampled(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val maxEdge = maxOf(bounds.outWidth, bounds.outHeight)
    if (maxEdge <= 0) return null
    var sample = 1
    while (maxEdge / sample > MAX_EDGE_PX) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}
