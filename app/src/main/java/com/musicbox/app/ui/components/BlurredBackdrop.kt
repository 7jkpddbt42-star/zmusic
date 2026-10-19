package com.musicbox.app.ui.components

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musicbox.app.ui.theme.rememberArtworkBitmap

/** 降级路径下先缩到的边长（像素），越小放大后越柔。 */
private const val LOW_RES_EDGE = 24

/**
 * 全屏毛玻璃背景层：封面放大铺满 → 模糊 → 暗化/染色遮罩。
 *
 * **两级模糊，调用方无感**：
 * - API 31+：真实模糊，[Modifier.blur]。
 * - API 26–30：把封面降到 [LOW_RES_EDGE]×[LOW_RES_EDGE] 再以 `FillBounds` 放大铺满，
 *   放大后天然柔化，视觉近似毛玻璃；**不**在低版本调用 [Modifier.blur]（无效）。
 *
 * 遮罩：`surface` 由上方高透明渐变到底部不透明（保证前景文字可读），
 * 另叠一层主题 `primary` 的极低透明染色与主题色呼应。
 *
 * 纯装饰层：不含任何 clickable/pointerInput，不拦截点击。
 * url 为空或加载失败时表现为渐变底，不崩溃。
 */
@Composable
fun BlurredBackdrop(
    url: String?,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 48.dp,
    scrim: Boolean = true,
) {
    val bitmap = rememberArtworkBitmap(url)
    val surface = MaterialTheme.colorScheme.surface
    val primary = MaterialTheme.colorScheme.primary

    Box(modifier = modifier) {
        if (bitmap != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(blurRadius),
                    contentScale = ContentScale.Crop,
                )
            } else {
                val lowRes = remember(bitmap) {
                    Bitmap.createScaledBitmap(bitmap, LOW_RES_EDGE, LOW_RES_EDGE, true)
                }
                Image(
                    bitmap = lowRes.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.Low,
                )
            }
            // 主题染色：极低透明度，让背景与主题色呼应
            Box(Modifier.fillMaxSize().background(primary.copy(alpha = 0.06f)))
        }

        if (scrim) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(
                            surface.copy(alpha = 0.30f),
                            surface.copy(alpha = 0.72f),
                            surface.copy(alpha = 1f),
                        ),
                    ),
                ),
            )
        }
    }
}
