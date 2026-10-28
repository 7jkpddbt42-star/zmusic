package com.musicbox.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicbox.app.ui.components.GlassBackButton
import com.musicbox.app.ui.components.GlassEmptyState
import com.musicbox.app.ui.components.GlassListPanel
import com.musicbox.app.ui.components.GlassPill
import com.musicbox.app.ui.components.MusicIcons
import com.musicbox.app.ui.library.SongRow
import com.musicbox.app.ui.player.formatTime
import com.musicbox.app.ui.theme.ArtworkImage

/**
 * 歌单详情（每日推荐 / 我喜欢的音乐 / 个人歌单共用）。
 * 头部是封面 + 标题 + 播放全部，下面是整块玻璃歌曲列表。
 */
@Composable
fun PlaylistDetailScreen(
    state: PlaylistDetailUiState,
    nowPlayingSongId: Long?,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onSongClick: (Int) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassBackButton(onClick = onBack)
            Spacer(modifier = Modifier.weight(1f))
            if (state.songs.isNotEmpty()) {
                GlassPill(
                    onClick = onPlayAll,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                ) {
                    Icon(
                        imageVector = MusicIcons.Play,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "播放全部",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkImage(
                url = state.coverUrl,
                modifier = Modifier.size(76.dp),
                shape = RoundedCornerShape(18.dp),
                maxEdgePx = 320,
            )
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        when {
            state.loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 48.dp),
                    )
                }
            }

            state.error != null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(bottom = 48.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    GlassEmptyState(
                        icon = MusicIcons.MusicNote,
                        title = "歌单加载失败",
                        description = state.error,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    GlassPill(onClick = onRetry) {
                        Text(
                            text = "重试",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            state.songs.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlassEmptyState(
                        icon = MusicIcons.MusicNote,
                        title = "歌单还是空的",
                        description = "换一个歌单，或稍后再来看看。",
                        modifier = Modifier.padding(bottom = 48.dp),
                    )
                }
            }

            else -> {
                GlassListPanel(
                    modifier = Modifier.fillMaxSize().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(26.dp),
                ) {
                    // key 带索引：万一同名歌单里出现重复曲目 id，也不会因为重复 key 崩溃
                    itemsIndexed(state.songs, key = { index, song -> "$index-${song.id}" }) { index, song ->
                        SongRow(
                            song = song,
                            isCurrent = song.id == nowPlayingSongId,
                            onClick = { onSongClick(index) },
                            trailing = {
                                Text(
                                    text = formatTime(song.durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
