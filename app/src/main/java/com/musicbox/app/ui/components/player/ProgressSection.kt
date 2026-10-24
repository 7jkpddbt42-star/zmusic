package com.musicbox.app.ui.components.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musicbox.app.player.PositionSource
import com.musicbox.app.ui.lyric.rememberFramePosition

/** 时间文本相对进度条的位置。不同风格用它拉开布局差异。 */
enum class ProgressTimePlacement {
    Inline,
    Above,
    Below,
    Hidden,
}

/**
 * 播放进度区：当前时间 + 总时长 + 可拖动进度条。
 *
 * - 进度条位置由 [rememberFramePosition] 逐帧读取，只失效绘制阶段，不触发每帧重组。
 * - 拖动时内部保存预览比例并回调 [onScrub]；按下/抬手都会调用 [onSeek]，
 *   直接写回现有播放控制器，播放核心逻辑不变。
 * - 轨道粗细、滑块大小、时间位置全部参数化，供 9 种风格复用。
 */
@Composable
fun ProgressSection(
    source: PositionSource,
    durationMs: Long,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    foreground: Color = Color.White,
    trackColor: Color = Color.White.copy(alpha = 0.26f),
    trackHeight: Dp = 3.dp,
    thumbRadius: Dp = 5.dp,
    showThumb: Boolean = true,
    timePlacement: ProgressTimePlacement = ProgressTimePlacement.Inline,
    timeStyle: TextStyle = MaterialTheme.typography.labelSmall,
    timeAlpha: Float = 0.66f,
    gap: Dp = 10.dp,
    onScrub: ((Float?) -> Unit)? = null,
) {
    val framePosition = rememberFramePosition(source)
    var dragFraction by remember { mutableStateOf<Float?>(null) }

    val displayPosition = dragFraction?.let { (it * durationMs).toLong() } ?: positionMs
    val timeColor = foreground.copy(alpha = timeAlpha)

    when (timePlacement) {
        ProgressTimePlacement.Inline -> Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatProgressTime(displayPosition),
                style = timeStyle,
                color = timeColor,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(gap))
            ProgressSeekBar(
                framePosition = framePosition,
                durationMs = durationMs,
                dragFraction = dragFraction,
                onDragFraction = { dragFraction = it },
                onSeek = onSeek,
                onScrub = onScrub,
                foreground = foreground,
                trackColor = trackColor,
                trackHeight = trackHeight,
                thumbRadius = thumbRadius,
                showThumb = showThumb,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(gap))
            Text(
                text = formatProgressTime(durationMs),
                style = timeStyle,
                color = timeColor,
                maxLines = 1,
            )
        }

        ProgressTimePlacement.Above -> Column(modifier = modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatProgressTime(displayPosition),
                    style = timeStyle,
                    color = timeColor,
                    maxLines = 1,
                )
                Text(
                    text = formatProgressTime(durationMs),
                    style = timeStyle,
                    color = timeColor,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            ProgressSeekBar(
                framePosition = framePosition,
                durationMs = durationMs,
                dragFraction = dragFraction,
                onDragFraction = { dragFraction = it },
                onSeek = onSeek,
                onScrub = onScrub,
                foreground = foreground,
                trackColor = trackColor,
                trackHeight = trackHeight,
                thumbRadius = thumbRadius,
                showThumb = showThumb,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ProgressTimePlacement.Below -> Column(modifier = modifier.fillMaxWidth()) {
            ProgressSeekBar(
                framePosition = framePosition,
                durationMs = durationMs,
                dragFraction = dragFraction,
                onDragFraction = { dragFraction = it },
                onSeek = onSeek,
                onScrub = onScrub,
                foreground = foreground,
                trackColor = trackColor,
                trackHeight = trackHeight,
                thumbRadius = thumbRadius,
                showThumb = showThumb,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatProgressTime(displayPosition),
                    style = timeStyle,
                    color = timeColor,
                    maxLines = 1,
                )
                Text(
                    text = formatProgressTime(durationMs),
                    style = timeStyle,
                    color = timeColor,
                    maxLines = 1,
                )
            }
        }

        ProgressTimePlacement.Hidden -> ProgressSeekBar(
            framePosition = framePosition,
            durationMs = durationMs,
            dragFraction = dragFraction,
            onDragFraction = { dragFraction = it },
            onSeek = onSeek,
            onScrub = onScrub,
            foreground = foreground,
            trackColor = trackColor,
            trackHeight = trackHeight,
            thumbRadius = thumbRadius,
            showThumb = showThumb,
            modifier = modifier.fillMaxWidth(),
        )
    }
}

/**
 * 细线进度条。轨道粗细与滑块大小可配；按下、拖动、抬手都会回调 [onSeek]。
 * 逐帧位置只在 Canvas 的 draw lambda 中读取，避免每帧重组。
 */
@Composable
private fun ProgressSeekBar(
    framePosition: State<Long>,
    durationMs: Long,
    dragFraction: Float?,
    onDragFraction: (Float?) -> Unit,
    onSeek: (Long) -> Unit,
    onScrub: ((Float?) -> Unit)?,
    foreground: Color,
    trackColor: Color,
    trackHeight: Dp,
    thumbRadius: Dp,
    showThumb: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .height(28.dp)
            .pointerInput(durationMs) {
                awaitEachGesture {
                    val widthPx = size.width.toFloat().coerceAtLeast(1f)
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()

                    var fraction = (down.position.x / widthPx).coerceIn(0f, 1f)
                    if (durationMs > 0L) onSeek((fraction * durationMs).toLong())
                    onDragFraction(fraction)
                    onScrub?.invoke(fraction)

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        fraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                        onDragFraction(fraction)
                        onScrub?.invoke(fraction)
                        change.consume()
                    }

                    if (durationMs > 0L) onSeek((fraction * durationMs).toLong())
                    onDragFraction(null)
                    onScrub?.invoke(null)
                }
            },
    ) {
        val playingFraction =
            if (durationMs > 0L) (framePosition.value.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        val fraction = dragFraction ?: playingFraction

        val stroke = trackHeight.toPx()
        val radius = thumbRadius.toPx()
        val centerY = size.height / 2f
        val inset = if (showThumb) radius else stroke / 2f
        val startX = inset
        val endX = (size.width - inset).coerceAtLeast(startX + 1f)
        val barWidth = endX - startX
        val progressX = startX + barWidth * fraction

        drawLine(
            color = trackColor,
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        if (barWidth > 0f) {
            drawLine(
                color = foreground,
                start = Offset(startX, centerY),
                end = Offset(progressX, centerY),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        if (showThumb) {
            drawCircle(
                color = foreground,
                radius = radius,
                center = Offset(progressX, centerY),
            )
        }
    }
}

/** 毫秒 → m:ss。 */
internal fun formatProgressTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
