package com.musicbox.app.ui.components

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musicbox.app.ui.theme.rememberArtworkBitmap

/** 降级路径下先缩到的边长（像素），越小放大后越柔。 */
private const val LOW_RES_EDGE = 24

/** 切歌时背景交叉淡入淡出的时长。 */
private const val CROSSFADE_MS = 720

/** 从「还没有封面」到「拿到第一张封面」的淡入时长。 */
private const val FIRST_FADE_MS = 620

/**
 * 全屏毛玻璃背景层：封面放大铺满 → 模糊 → 暗化/染色遮罩。
 *
 * 纯装饰层：不含任何 clickable/pointerInput，不拦截点击。
 * url 为空或加载失败时表现为渐变底，不崩溃。
 *
 * 切歌（url 变化）不会硬切：新封面解码完成后与旧封面交叉淡入淡出，
 * 新封面还没就绪时旧封面保持不动，避免中途闪成渐变底。
 */
@Composable
fun BlurredBackdrop(
    url: String?,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 48.dp,
    scrim: Boolean = true,
) {
    BlurScrimBitmap(
        bitmap = rememberArtworkBitmap(url),
        modifier = modifier,
        blurRadius = blurRadius,
        scrim = scrim,
    )
}

/**
 * 位图 → 毛玻璃渲染 + 染色 + 遮罩的公共实现，供 [BlurredBackdrop]（封面）与
 * 自定义背景层（本机图片）共用。
 *
 * **两级模糊，调用方无感**：
 * - API 31+：真实模糊，[Modifier.blur]。
 * - API 26–30：把位图降到 [LOW_RES_EDGE]×[LOW_RES_EDGE] 再以 `FillBounds` 放大铺满，
 *   放大后天然柔化，视觉近似毛玻璃；**不**在低版本调用 [Modifier.blur]（无效）。
 *
 * 遮罩：`surface` 由上方高透明渐变到底部不透明（保证前景文字可读），
 * 另叠一层主题 `primary` 的极低透明染色与主题色呼应。位图为 null 时仅剩渐变底。
 */
@Composable
internal fun BlurScrimBitmap(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 48.dp,
    scrim: Boolean = true,
) {
    val surface = MaterialTheme.colorScheme.surface
    val primary = MaterialTheme.colorScheme.primary
    val fade = rememberBackdropCrossfade(bitmap)

    Box(modifier = modifier) {
        // 旧图垫底淡出，新图淡入；alpha 在 graphicsLayer 内读取，只走图层阶段不触发重组
        fade.previous?.let { previous ->
            BackdropBitmapLayer(
                bitmap = previous,
                blurRadius = blurRadius,
                alpha = { 1f - fade.progress.value },
            )
        }
        fade.current?.let { current ->
            BackdropBitmapLayer(
                bitmap = current,
                blurRadius = blurRadius,
                alpha = { fade.progress.value },
            )
        }

        if (fade.current != null) {
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

/** 交叉淡入所需的两张位图与进度。 */
private class BackdropCrossfade(
    val current: Bitmap?,
    val previous: Bitmap?,
    val progress: Animatable<Float, AnimationVector1D>,
)

/**
 * 位图切换时的交叉淡入状态。
 *
 * [bitmap] 为 null（新封面还在下载）时不改动现有背景；新图到位后才把旧图设为
 * [BackdropCrossfade.previous] 并在 [CROSSFADE_MS] 内淡入新图，结束后释放旧图引用。
 */
@Composable
private fun rememberBackdropCrossfade(bitmap: Bitmap?): BackdropCrossfade {
    var current by remember { mutableStateOf(bitmap) }
    var previous by remember { mutableStateOf<Bitmap?>(null) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(bitmap) {
        val next = bitmap ?: return@LaunchedEffect
        if (next === current) return@LaunchedEffect
        val outgoing = current
        previous = outgoing
        current = next
        if (outgoing == null) {
            // 首次拿到封面（开始播放 / 进入页面）：从当前底色淡入，不再瞬切。
            // 之前这里直接 snapTo(1f)，主页点歌那一刻背景会"啪"地变成封面。
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(FIRST_FADE_MS, easing = FastOutSlowInEasing),
            )
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(CROSSFADE_MS, easing = FastOutSlowInEasing),
        )
        previous = null
    }

    return BackdropCrossfade(current, previous, progress)
}

/** 单层背景图：按系统版本选择真实模糊或低分辨率放大柔化。 */
@Composable
private fun BackdropBitmapLayer(
    bitmap: Bitmap,
    blurRadius: Dp,
    alpha: () -> Float,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha() }
                .blur(blurRadius),
            contentScale = ContentScale.Crop,
        )
    } else {
        val lowRes = remember(bitmap) {
            Bitmap.createScaledBitmap(bitmap, LOW_RES_EDGE, LOW_RES_EDGE, true)
        }
        Image(
            bitmap = lowRes.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha() },
            contentScale = ContentScale.FillBounds,
            filterQuality = FilterQuality.Low,
        )
    }
}
