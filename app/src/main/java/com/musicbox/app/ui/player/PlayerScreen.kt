package com.musicbox.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 全屏播放页。MI-2 只保证能控制与信息完整（含试听标注），
 * 封面取色、过渡动画、图标体系等视觉打磨留给 MI-4。
 */
@Composable
internal fun PlayerScreen(
    state: PlaybackUiState,
    modeLabel: String,
    onCollapse: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleMode: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyric: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onCollapse)

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCollapse) { Text("收起") }
                Text(
                    text = "正在播放",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenQueue) { Text("队列") }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Artwork(
                url = state.artworkUrl,
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenLyric),
            )
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(onClick = onOpenLyric) { Text("歌词") }

            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = state.title.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = state.artist.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.isTrial) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "试听片段 · 登录解锁完整版",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            var dragging by remember { mutableStateOf(false) }
            var dragValue by remember { mutableFloatStateOf(0f) }
            val max = state.durationMs.coerceAtLeast(1L).toFloat()
            val shown = if (dragging) dragValue else state.positionMs.toFloat()

            Slider(
                value = shown.coerceIn(0f, max),
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    onSeek(dragValue.toLong())
                    dragging = false
                },
                valueRange = 0f..max,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = formatTime(shown.toLong()), style = MaterialTheme.typography.labelSmall)
                Text(text = formatTime(state.durationMs), style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCycleMode) { Text(modeLabel) }
                TextButton(onClick = onPrevious) {
                    Text("⏮", style = MaterialTheme.typography.headlineSmall)
                }
                TextButton(onClick = onTogglePlay) {
                    Text(
                        text = if (state.isPlaying) "⏸" else "▶",
                        style = MaterialTheme.typography.displaySmall,
                    )
                }
                TextButton(onClick = onNext) {
                    Text("⏭", style = MaterialTheme.typography.headlineSmall)
                }
                // 占位保持左右对称，避免按钮横向跳动
                TextButton(onClick = {}, enabled = false) { Text(" ") }
            }

            if (state.isBuffering) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "缓冲中…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 毫秒 → m:ss。 */
internal fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
