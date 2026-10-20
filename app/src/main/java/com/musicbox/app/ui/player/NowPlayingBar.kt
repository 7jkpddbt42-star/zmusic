package com.musicbox.app.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicbox.app.ui.components.liquidglass.LocalGlassBackdrop
import com.musicbox.app.ui.components.liquidglass.liquidGlass
import com.musicbox.app.ui.theme.ArtworkImage

/** 底部迷你播放条。点整条打开播放页，右侧按钮直接播放/暂停。 */
@Composable
internal fun NowPlayingBar(
    state: PlaybackUiState,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
    lyricLine: String = "",
) {
    if (!state.hasCurrent) return

    // 液态玻璃横条：贴在底部、只圆顶部两角，实时折射壳层背景源（标签页内容）；
    // 进度线/封面/文字/按钮布局与行为不变
    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop = LocalGlassBackdrop.current,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                blurRadius = 20.dp,
                tint = Color.White.copy(alpha = 0.14f),
            ),
    ) {
        Column(modifier = Modifier.clickable(onClick = onExpand)) {
            // 进度条：2dp 细线，够用且不抢视觉
            val fraction = if (state.durationMs > 0) {
                (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(2.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceAtLeast(0.001f))
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary),
                ) {}
                Column(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                ) {}
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkImage(
                    url = state.artworkUrl,
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(6.dp),
                )
                Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
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
                TextButton(onClick = onTogglePlay) {
                    Text(if (state.isPlaying) "⏸" else "▶", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
