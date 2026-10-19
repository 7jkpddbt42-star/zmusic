package com.musicbox.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicbox.app.ui.theme.ArtworkImage

/** 底部迷你播放条。点整条打开播放页，右侧按钮直接播放/暂停。 */
@Composable
internal fun NowPlayingBar(
    state: PlaybackUiState,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.hasCurrent) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                }
                TextButton(onClick = onTogglePlay) {
                    Text(if (state.isPlaying) "⏸" else "▶", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
