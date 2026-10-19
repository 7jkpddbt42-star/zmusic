package com.musicbox.app.ui.lyric

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicbox.app.lyric.LyricLine
import com.musicbox.app.lyric.LyricWord
import com.musicbox.app.lyric.wordProgress
import com.musicbox.app.player.PositionSource

/**
 * 逐字歌词页。动画由 [rememberFramePosition] 的逐帧时钟驱动（设计文档 §5.2），
 * 每个字的"填充进度"每一帧重算一次，因此跟得上、不顿。
 *
 * 逐字填充的实现：每个词画两遍文字——暗色的底 + 亮色的面，亮色那遍用
 * clipRect(right = width * progress) 由左向右揭开。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LyricScreen(
    state: LyricUiState,
    source: PositionSource,
    title: String?,
    artist: String?,
    showTranslation: Boolean,
    showRomaji: Boolean,
    onToggleTranslation: () -> Unit,
    onToggleRomaji: () -> Unit,
    onSeek: (Long) -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onCollapse)

    // 逐帧时钟：每帧读一次真实播放位置
    val position by rememberFramePosition(source)

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCollapse) { Text("返回") }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = artist.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(onClick = onToggleTranslation) {
                    Text(
                        text = "译",
                        color = if (showTranslation) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onToggleRomaji) {
                    Text(
                        text = "音",
                        color = if (showRomaji) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when (val s = state) {
                is LyricUiState.Ready -> LyricList(
                    lines = s.timeline.lines,
                    positionMs = position,
                    showTranslation = showTranslation,
                    showRomaji = showRomaji,
                    onLineClick = onSeek,
                    modifier = Modifier.weight(1f),
                )

                else -> Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = when (s) {
                            LyricUiState.Loading -> "歌词加载中…"
                            LyricUiState.Failed -> "歌词获取失败"
                            else -> "暂无歌词"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricList(
    lines: List<LyricLine>,
    positionMs: Long,
    showTranslation: Boolean,
    showRomaji: Boolean,
    onLineClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // 当前行 → 每次变化把该行滚到视口约 1/3 高处（负 offset 把 item 往下压）
    val activeIndex = lines.indexOfLast { it.startMs <= positionMs }
    LaunchedEffect(activeIndex, lines) {
        if (activeIndex < 0) return@LaunchedEffect
        val viewportHeight = listState.layoutInfo.viewportSize.height
        val offset = if (viewportHeight > 0) -(viewportHeight / 3) else 0
        listState.animateScrollToItem(activeIndex, offset)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
    ) {
        item { Spacer(modifier = Modifier.height(160.dp)) }
        itemsIndexed(lines) { index, line ->
            LyricLineRow(
                line = line,
                isActive = index == activeIndex,
                positionMs = positionMs,
                showTranslation = showTranslation,
                showRomaji = showRomaji,
                onClick = { onLineClick(line.startMs) },
            )
        }
        item { Spacer(modifier = Modifier.height(240.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricLineRow(
    line: LyricLine,
    isActive: Boolean,
    positionMs: Long,
    showTranslation: Boolean,
    showRomaji: Boolean,
    onClick: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val dim = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)

    // 当前行的"已唱"部分用主题色，未唱部分用半透明 onSurface；非当前行整体暗
    val baseColor = if (isActive) onSurface.copy(alpha = 0.30f) else dim
    val fillColor = if (isActive) MaterialTheme.colorScheme.primary else dim

    val alpha by animateFloatAsState(targetValue = if (isActive) 1f else 0.85f, label = "lineAlpha")
    // 当前行整体轻微放大；行级过渡允许用 animateFloatAsState（非逐帧）
    val scale by animateFloatAsState(targetValue = if (isActive) 1.02f else 1f, label = "lineScale")
    val style: TextStyle = if (isActive) {
        MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 32.sp)
    } else {
        MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 26.sp)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        if (line.words.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.Start) {
                line.words.forEach { word ->
                    KaraokeWord(
                        word = word,
                        positionMs = positionMs,
                        baseColor = baseColor,
                        fillColor = fillColor,
                        style = style,
                        alpha = alpha,
                        animate = isActive,
                    )
                }
            }
        } else {
            // LRC 降级：整行高亮（设计文档 §5.3）
            Text(
                text = line.text,
                style = style,
                color = if (isActive) onSurface else dim,
            )
        }

        if (showTranslation && !line.translation.isNullOrBlank()) {
            Text(
                text = line.translation!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isActive) 0.9f else 0.5f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (showRomaji && !line.romaji.isNullOrBlank()) {
            Text(
                text = line.romaji!!,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isActive) 0.75f else 0.4f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * 一个词的逐字填充：暗底 + 亮面（亮面按进度裁剪）。
 * progress 每帧由调用方传入的 positionMs 重算（见 wordProgress）。
 *
 * 当前行额外做逐字"上浮 + 放大"——同样由 progress 每帧驱动，
 * 严禁使用 animateFloatAsState（会丢毫秒精度）。变换加在包住
 * 「暗底 + 亮面」两层文字的外层 Box 上，保证两层同步位移、不错位。
 * 非当前行（[animate] == false）不套变换：历史行 progress 恒为 1，
 * 否则已唱过的行会永久上浮。
 */
@Composable
private fun KaraokeWord(
    word: LyricWord,
    positionMs: Long,
    baseColor: Color,
    fillColor: Color,
    style: TextStyle,
    alpha: Float,
    animate: Boolean,
) {
    val progress = wordProgress(positionMs, word)
    // 上浮幅度约 0.25em：style.fontSize.value 为 sp 数值
    val d = LocalDensity.current
    val risePx = style.fontSize.value * d.density * d.fontScale * 0.25f
    Box(
        modifier = if (animate) {
            Modifier.graphicsLayer {
                translationY = -risePx * progress
                val s = 1f + 0.06f * progress
                scaleX = s
                scaleY = s
            }
        } else {
            Modifier
        },
    ) {
        Text(
            text = word.text,
            style = style,
            color = baseColor.copy(alpha = baseColor.alpha * alpha),
            softWrap = false,
        )
        if (progress > 0f) {
            Text(
                text = word.text,
                style = style,
                color = fillColor.copy(alpha = fillColor.alpha * alpha),
                softWrap = false,
                modifier = Modifier.drawWithContent {
                    clipRect(right = size.width * progress) {
                        this@drawWithContent.drawContent()
                    }
                },
            )
        }
    }
}
