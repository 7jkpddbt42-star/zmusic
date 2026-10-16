package com.musicbox.app.ui.player

import android.graphics.BitmapFactory
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

// 封面来源是 p*.music.126.net 的 jpg，一张十几行就够，没必要为它引入图片库
private val artworkClient = OkHttpClient()
private val artworkCache = mutableMapOf<String, ImageBitmap>()

@Composable
private fun rememberArtwork(url: String?): ImageBitmap? {
    var bitmap by remember(url) { mutableStateOf(url?.let { artworkCache[it] }) }

    LaunchedEffect(url) {
        if (url == null || bitmap != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                artworkClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    BitmapFactory.decodeStream(response.body.byteStream())
                }
            }.getOrNull()?.asImageBitmap()
        }
        if (loaded != null) {
            artworkCache[url] = loaded
            bitmap = loaded
        }
    }
    return bitmap
}

/** 封面方块。拿不到图时用底色 + 音符占位，不留空白。 */
@Composable
internal fun Artwork(url: String?, modifier: Modifier = Modifier) {
    val bitmap = rememberArtwork(url)
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(text = "♪", style = MaterialTheme.typography.displayMedium)
        }
    }
}
