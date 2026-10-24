package com.musicbox.app.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicbox.app.player.PositionSource
import com.musicbox.app.ui.components.MusicIcons
import com.musicbox.app.ui.components.liquidglass.LocalGlassBackdrop
import com.musicbox.app.ui.components.liquidglass.liquidGlass
import com.musicbox.app.ui.lyric.rememberFramePosition
import com.musicbox.app.ui.theme.ArtworkImage
import com.musicbox.app.ui.theme.THUMB_EDGE_PX

/**
 * 底部迷你播放条（悬浮玻璃胶囊）。
 *
 * 与旧版的区别：
 * - 整条改为圆角胶囊并四周留白，玻璃边缘的折射/高光可见；
 * - 播放键改用 [MusicIcons] 线性图标，不再用 `▶` / `⏸` 字形；
 * - 进度线由逐帧时钟驱动、只在 Canvas 的 draw 阶段读取位置，滑动时不再触发重组。
 */
@Composable
internal fun NowPlayingBar(
    state: PlaybackUiState,
    source: PositionSource,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
    lyricLine: String = "",
) {
    if (!state.hasCurrent) return

    // 按压反馈：clickable 与玻璃共用同一交互源，按下时整体 0.98 缩放、折射 +20%、高光 +10%
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop = LocalGlassBackdrop.current,
                shape = RoundedCornerShape(24.dp),
                blurRadius = 6.dp,
                tint = Color.White.copy(alpha = 0.08f),
                interactionSource = interactionSource,
            ),
    ) {
        Column(
            modifier = Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onExpand,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 8.dp, top = 9.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkImage(
                    url = state.artworkUrl,
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    maxEdgePx = THUMB_EDGE_PX,
                )
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        text = state.title.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append(state.artist.orEmpty())
                            if (state.isTrial) append(" · 试听片段")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 当前歌词行：始终占据一行高度，随行变化淡入淡出，避免迷你条抖动
                    AnimatedContent(
                        targetState = lyricLine,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        contentAlignment = Alignment.CenterStart,
                        label = "miniLyric",
                        modifier = Modifier.fillMaxWidth(),
                    ) { text ->
                        Text(
                            // 空行渲染单个空格占位，视觉上为空白且高度稳定
                            text = text.ifEmpty { " " },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                PlayPauseButton(isPlaying = state.isPlaying, onClick = onTogglePlay)
            }

            MiniProgress(
                source = source,
                durationMs = state.durationMs,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 9.dp),
            )
        }
    }
}

/** 播放/暂停圆形按钮：主题色淡底 + 线性图标。 */
@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPlaying) MusicIcons.Pause else MusicIcons.Play,
            contentDescription = if (isPlaying) "暂停" else "播放",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 迷你进度线：2dp 圆头细线。
 * 位置每帧从 [rememberFramePosition] 读取，且读取发生在 Canvas 的 draw lambda 内，
 * 只失效绘制阶段，不触发 Composable 重组。
 */
@Composable
private fun MiniProgress(
    source: PositionSource,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val position = rememberFramePosition(source)
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val activeColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.height(2.dp)) {
        val fraction = if (durationMs > 0L) {
            (position.value.toFloat() / durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
        val y = size.height / 2f
        drawLine(
            color = trackColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = size.height,
            cap = StrokeCap.Round,
        )
        if (fraction > 0.001f) {
            drawLine(
                color = activeColor,
                start = Offset(0f, y),
                end = Offset(size.width * fraction, y),
                strokeWidth = size.height,
                cap = StrokeCap.Round,
            )
        }
    }
}
