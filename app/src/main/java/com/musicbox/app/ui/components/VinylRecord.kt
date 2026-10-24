package com.musicbox.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.musicbox.app.ui.theme.rememberArtworkBitmap

/** 唱片每秒旋转的角度（约 18 秒一圈）。 */
private const val ROTATION_DEG_PER_SEC = 20f

/** 中心封面占盘体直径的比例。 */
private const val COVER_DIAMETER_FRACTION = 0.62f

/**
 * 旋转黑胶唱片。
 *
 * - 盘体：`Canvas` 画深色径向渐变盘面 + 若干同心圆纹路（`drawCircle(style = Stroke)`）。
 * - 中心封面：占盘体约 [COVER_DIAMETER_FRACTION] 直径，圆形裁剪；中心再画一个小圆孔。
 * - **旋转规则：播放时转、暂停时停在当前角度、恢复后从该角度继续**。
 *   这里用 `LaunchedEffect(isPlaying)` + [withFrameNanos] 逐帧累加角度：`isPlaying` 为假时
 *   不累加且角度保留；进入播放循环时重置首帧时间戳，避免首帧跳变。
 *   注意**不能**用 `rememberInfiniteTransition`：它既无法暂停，也无法把角度停在中间值。
 * - 角度写入 `graphicsLayer { rotationZ = angle }`，在 lambda 中读 state，只触发布局/图层更新。
 * - 盘体大小由调用方 `modifier` 控制（内部 `fillMaxSize`）。
 *
 * [onBitmap] 在封面解码成功时回调一次，供主题取色（与 [com.musicbox.app.ui.theme.ArtworkImage] 一致）。
 */
@Composable
fun VinylRecord(
    artworkUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onBitmap: ((Bitmap) -> Unit)? = null,
) {
    val bitmap = rememberArtworkBitmap(artworkUrl)

    // 解码成功（含缓存命中）即回调一次，供取色使用；url/bitmap 变化时才会重新触发
    LaunchedEffect(artworkUrl, bitmap) {
        val b = bitmap
        if (b != null) onBitmap?.invoke(b)
    }

    // 旋转角度：暂停时保留，恢复播放从该角度继续
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastFrameNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastFrameNanos != 0L) {
                    val deltaSeconds = (now - lastFrameNanos) / 1_000_000_000f
                    angle = (angle + deltaSeconds * ROTATION_DEG_PER_SEC) % 360f
                }
                lastFrameNanos = now
            }
        }
    }

    Box(
        modifier = modifier.graphicsLayer { rotationZ = angle },
        contentAlignment = Alignment.Center,
    ) {
        // 盘体：深色径向渐变 + 同心圆纹路
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF2C2C30), Color(0xFF0C0C0E)),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
            var groove = radius * 0.30f
            val step = radius * 0.035f
            while (groove < radius * 0.96f) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.045f),
                    radius = groove,
                    center = center,
                    style = Stroke(width = 1f),
                )
                groove += step
            }
        }

        // 中心封面：圆形裁剪，占盘体约 0.62 直径
        val cover = bitmap
        if (cover != null) {
            Image(
                bitmap = cover.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(COVER_DIAMETER_FRACTION).clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize(COVER_DIAMETER_FRACTION)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MusicIcons.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        // 中心小圆孔
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color(0xFF08080A),
                radius = size.minDimension * 0.03f,
                center = Offset(size.width / 2f, size.height / 2f),
            )
        }
    }
}
