package com.musicbox.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.musicbox.app.model.Song
import com.musicbox.app.player.DownloadedSong
import com.musicbox.app.ui.components.GlassBackButton
import com.musicbox.app.ui.components.GlassEmptyState
import com.musicbox.app.ui.components.GlassListPanel
import com.musicbox.app.ui.components.MusicIcons
import com.musicbox.app.ui.components.PageHeader
import java.util.Locale

/**
 * 已下载列表：点整行直接播（本地文件，断网也能播），行尾垃圾桶删除单曲。
 * 版式与「最近播放」一致：页头 + 整块玻璃列表。
 */
@Composable
fun DownloadedScreen(
    songs: List<DownloadedSong>,
    nowPlayingSongId: Long?,
    onSongClick: (Song) -> Unit,
    onDelete: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        PageHeader(
            title = "已下载",
            subtitle = if (songs.isEmpty()) {
                "下载过的歌会存在本机，断网也能听"
            } else {
                "${songs.size} 首 · 共 ${formatBytes(songs.sumOf { it.sizeBytes })}"
            },
            leading = { GlassBackButton(onClick = onBack) },
        )
        if (songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassEmptyState(
                    icon = MusicIcons.DownloadDone,
                    title = "还没有下载的歌曲",
                    description = "在播放页点下载按钮，整首歌会存到本机。",
                    modifier = Modifier.padding(bottom = 48.dp),
                )
            }
        } else {
            GlassListPanel(
                modifier = Modifier.fillMaxSize().padding(bottom = 8.dp),
                shape = RoundedCornerShape(26.dp),
            ) {
                itemsIndexed(songs, key = { _, item -> item.song.id }) { _, item ->
                    SongRow(
                        song = item.song,
                        isCurrent = item.song.id == nowPlayingSongId,
                        onClick = { onSongClick(item.song) },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatBytes(item.sizeBytes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                        .copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(17.dp))
                                        .clickable { onDelete(item.song.id) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = MusicIcons.Trash,
                                        contentDescription = "删除下载",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            .copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/** 字节数 → 人类可读体积。 */
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 ->
        String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.US, "%d KB", bytes / 1024)
}
