package com.musicbox.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate

/**
 * 流光进度：0..1 的循环值。
 *
 * 流光本身应一直流动、与播放状态无关，故用 [rememberInfiniteTransition] +
 * [animateFloat] 做线性无限循环（[LinearEasing] 保证匀速，无缓动插值）。
 */
@Composable
fun rememberFlowProgress(periodMs: Int = 2600): Float {
    val transition = rememberInfiniteTransition(label = "flow")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "flowProgress",
    )
    return progress
}

/**
 * 在本 Modifier 区域内绘制一条斜向流光带。
 *
 * - `startX` 随 [progress] 由 `-width` 扫到 `+width`，终点沿斜向偏移 [h]，形成斜向渐变光带。
 * - [colors] 由亮色渐到透明；[heightFraction] 控制光带高度占区域高度的比例。
 * - [angle] 可整体旋转光带方向。
 * - 纯装饰：`drawBehind` 不改变布局尺寸、不拦截点击。
 *
 * [progress] 由调用方从 [rememberFlowProgress] 传入——本函数因此无需是 `@Composable`，
 * 也不在内部起动画。
 */
fun Modifier.flowingLight(
    progress: Float,
    colors: List<Color>,
    angle: Float = 0f,
    heightFraction: Float = 1f,
): Modifier = drawBehind {
    if (colors.size < 2) return@drawBehind
    val h = size.height * heightFraction.coerceIn(0f, 1f)
    val w = size.width
    if (h <= 0f || w <= 0f) return@drawBehind

    rotate(degrees = angle) {
        // 斜向光带：起点从 -width 扫到 +width
        val startX = -w + progress.coerceIn(0f, 1f) * 2f * w
        val start = Offset(startX, 0f)
        val end = Offset(startX + h, h)
        drawRect(
            brush = Brush.linearGradient(colors = colors, start = start, end = end),
            topLeft = Offset.Zero,
            size = Size(w, h),
        )
    }
}
