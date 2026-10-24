package com.musicbox.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musicbox.app.model.PlaylistSummary
import com.musicbox.app.ui.components.GlassBackButton
import com.musicbox.app.ui.components.GlassEmptyState
import com.musicbox.app.ui.components.GlassPanel
import com.musicbox.app.ui.components.MusicIcons
import com.musicbox.app.ui.components.PageHeader
import com.musicbox.app.ui.components.PlaylistRow

/**
 * 全部歌单覆盖页。
 *
 * 首页与「我的」只预览前几个歌单，避免整页纵向滚动里一次性组合上百行；
 * 完整列表放到这里的 [LazyColumn] 中，按可视区域惰性组合与绘制。
 */
@Composable
fun AllPlaylistsScreen(
    playlists: List<PlaylistSummary>,
    onOpenPlaylist: (PlaylistSummary) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        PageHeader(
            title = "我的歌单",
            subtitle = if (playlists.isEmpty()) "还没有创建歌单" else "${playlists.size} 个歌单",
            leading = { GlassBackButton(onClick = onBack) },
        )
        if (playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassEmptyState(
                    icon = MusicIcons.MusicNote,
                    title = "还没有歌单",
                    description = "在网易云创建或收藏歌单后，会同步到这里。",
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
                    items(playlists, key = { it.id }) { playlist ->
                        PlaylistRow(
                            summary = playlist,
                            onClick = { onOpenPlaylist(playlist) },
                        )
                    }
                }
            }
        }
    }
}
