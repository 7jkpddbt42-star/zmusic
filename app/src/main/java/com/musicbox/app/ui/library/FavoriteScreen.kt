package com.musicbox.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicbox.app.model.Song

@Composable
fun FavoriteScreen(
    songs: List<Song>,
    nowPlayingSongId: Long?,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (songs.isEmpty()) {
        EmptyHint("还没有收藏的歌曲。在播放页点心形即可收藏。", modifier)
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp)) {
        itemsIndexed(songs, key = { _, s -> s.id }) { _, song ->
            SongRow(song, song.id == nowPlayingSongId) { onSongClick(song) }
        }
    }
}

/** 收藏 / 历史共用的歌曲行。 */
@Composable
internal fun SongRow(song: Song, isCurrent: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isCurrent) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            )
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = song.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = song.artists.joinToString(" / ") + (song.album?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    HorizontalDivider()
}

@Composable
internal fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
