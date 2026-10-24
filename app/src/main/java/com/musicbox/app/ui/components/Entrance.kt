package com.musicbox.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val START_DELAY_MS = 40L
private const val STAGGER_MS = 45L
private const val DURATION_MS = 360
private val ENTRANCE_OFFSET = 26.dp

/**
 * 板块入场动效：淡入 + 自下而上滑入，按 [index] 依次错开，形成整页"逐块浮现"。
 *
 * 性能：动画只在 [graphicsLayer] 的 layer lambda 里读取进度，只失效绘制/图层阶段，
 * 不触发重组，也不影响内部玻璃面板对背景源的采样。
 * 每个页面首次组合时播放一次；页面离开后重新进入会再次播放。
 */
@Composable
fun Entrance(
    index: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val distance = with(LocalDensity.current) { ENTRANCE_OFFSET.toPx() }

    LaunchedEffect(Unit) {
        delay(START_DELAY_MS + index * STAGGER_MS)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = DURATION_MS, easing = LinearOutSlowInEasing),
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress.value
                translationY = (1f - progress.value) * distance
            },
        content = { content() },
    )
}
