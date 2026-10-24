package com.musicbox.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musicbox.app.model.Song
import com.musicbox.app.ui.components.GlassBackButton
import com.musicbox.app.ui.components.GlassEmptyState
import com.musicbox.app.ui.components.GlassPanel
import com.musicbox.app.ui.components.GlassPill
import com.musicbox.app.ui.components.MusicIcons
import com.musicbox.app.ui.components.PageHeader
import com.musicbox.app.ui.player.formatTime

/**
 * 最近播放页（重绘版）。页头右侧是玻璃「清空」胶囊；列表整块玻璃，行尾显示时长。
 * [onBack] 非空时用于全屏覆盖层：页头左侧出现返回胶囊。
 */
@Composable
fun HistoryScreen(
    songs: List<Song>,
    nowPlayingSongId: Long?,
    onSongClick: (Song) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        PageHeader(
            title = "最近播放",
            subtitle = if (songs.isEmpty()) "播放过的歌会自动记录" else "${songs.size} 首 · 按播放时间排序",
            leading = if (onBack == null) null else {
                { GlassBackButton(onClick = onBack) }
            },
            trailing = if (songs.isEmpty()) {
                null
            } else {
                {
                    GlassPill(onClick = onClear) {
                        Icon(
                            imageVector = MusicIcons.Trash,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "清空",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
        if (songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassEmptyState(
                    icon = MusicIcons.History,
                    title = "还没有播放记录",
                    description = "播放过的歌曲会按时间记在这里，方便回来再听。",
                    modifier = Modifier.padding(bottom = 48.dp),
                )
            }
        } else {
            GlassPanel(
                modifier = Modifier.fillMaxSize().padding(bottom = 8.dp),
                shape = RoundedCornerShape(26.dp),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(songs, key = { _, song -> song.id }) { _, song ->
                        SongRow(
                            song = song,
                            isCurrent = song.id == nowPlayingSongId,
                            onClick = { onSongClick(song) },
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
