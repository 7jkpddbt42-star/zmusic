package com.musicbox.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.musicbox.app.player.PositionSource
import com.musicbox.app.ui.lyric.rememberFramePosition

/** 单根波形条的宽度。 */
private val BAR_WIDTH = 3.dp

/** 相邻两根波形条的间距。 */
private val BAR_GAP = 2.dp

/** 哈希种子：64 位黄金分割常数，用来把相邻下标彻底打散。 */
private const val GOLDEN_RATIO_64 = 0x9E3779B97F4A7C15uL

/**
 * 由歌曲 id 与条下标推导的单根条高度比例（相对 Canvas 高度）。
 *
 * **这是一个确定性伪波形**：并非真实音频频谱分析（不申请录音权限、不使用
 * `Visualizer`），只是用整数哈希把 `songId` + `index` 打散成一组稳定的伪随机高度。
 * 同一首歌、同一根条的结果恒定不变，所以看起来像一条有形状的波形。
 *
 * 输出恒定落在 `[0.15f, 1f]`：下限保证再安静的条也可见，上限保证不越出画布。
 * 纯函数、无随机数、无对象分配，可直接单测。
 */
fun waveformBarFraction(songId: Long?, index: Int): Float {
    // 组合成 64 位种子后做若干轮 xor-shift-multiply（splitmix64 风格）充分打散
    var x = index.toULong() * GOLDEN_RATIO_64 xor (songId ?: 0L).toULong()
    x = x xor (x shr 30)
    x = x * 0xBF58476D1CE4E5B9uL
    x = x xor (x shr 27)
    x = x * 0x94D049BB133111EBuL
    x = x xor (x shr 31)

    // 取低 32 位映射到 [0,1)，再线性拉伸到 [0.15, 1]
    val unit = (x and 0xFFFFFFFFuL).toFloat() / 4294967296f
    return 0.15f + 0.85f * unit
}

/**
 * 波形进度条：Canvas 绘制一排竖条，条高由 [waveformBarFraction] 决定，
 * 已播放段用主题色、未播放段用低透明背景色；保留原进度条的点击/拖动跳转职能。
 *
 * 逐帧位置：用 [rememberFramePosition] 拿每帧位置，且**只在 draw lambda 里读**它的值——
 * 这样每帧只让 draw 阶段失效，不会触发每帧重组（对比把位置读进 Composable 体的代价）。
 * 拖动预览比例 [dragFraction] 同样只在 draw lambda 里读。
 *
 * 暂停时位置恒定，Compose 结构相等短路无效化 → 不消耗额外帧。
 */
@Composable
fun WaveformSeekBar(
    source: PositionSource,
    songId: Long?,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onScrub: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val framePosition = rememberFramePosition(source)
    // 拖动中的预览比例；null 表示未拖动，显示真实播放位置
    var dragFraction by remember { mutableStateOf<Float?>(null) }

    // 主题色在 Composable 体里取好，避免每帧读 MaterialTheme
    val playedColor = MaterialTheme.colorScheme.primary
    val idleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)

    val density = LocalDensity.current
    val barWidthPx = with(density) { BAR_WIDTH.toPx() }
    val barGapPx = with(density) { BAR_GAP.toPx() }

    Canvas(
        modifier = modifier
            .height(40.dp)
            .pointerInput(durationMs) {
                awaitEachGesture {
                    val widthPx = size.width.toFloat().coerceAtLeast(1f)
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var fraction = (down.position.x / widthPx).coerceIn(0f, 1f)
                    // 按下即跳转；时长非法时不 seek（避免跳到 [0, duration) 之外）
                    if (durationMs > 0L) onSeek((fraction * durationMs).toLong())

                    // 拖动过程中持续更新预览比例并上报
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        fraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                        dragFraction = fraction
                        onScrub?.invoke(fraction)
                        change.consume()
                    }

                    // 抬手：落定并清理拖动预览
                    if (durationMs > 0L) onSeek((fraction * durationMs).toLong())
                    dragFraction = null
                }
            },
    ) {
        val count = (size.width / (barWidthPx + barGapPx)).toInt().coerceAtLeast(1)
        val playingFraction =
            if (durationMs > 0L) (framePosition.value.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        val displayFraction = dragFraction ?: playingFraction

        for (i in 0 until count) {
            val height = size.height * waveformBarFraction(songId, i)
            val left = i * (barWidthPx + barGapPx)
            // 第 i 根条的"中心比例"，用中心而非左缘比较，避免整体偏左
            val center = (i + 0.5f) / count
            val color = if (center <= displayFraction) playedColor else idleColor
            drawRoundRect(
                color = color,
                topLeft = Offset(left, size.height - height),
                size = Size(barWidthPx, height),
                cornerRadius = CornerRadius(barWidthPx / 2f),
            )
        }
    }
}
