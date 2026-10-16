package com.musicbox.app.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musicbox.app.model.Song

@Composable
fun HistoryScreen(
    songs: List<Song>,
    nowPlayingSongId: Long?,
    onSongClick: (Song) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp)) {
        if (songs.isEmpty()) {
            Text("还没有播放记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        androidx.compose.material3.TextButton(onClick = onClear) { Text("清空播放记录") }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(songs, key = { _, s -> s.id }) { _, song ->
                SongRow(song, song.id == nowPlayingSongId) { onSongClick(song) }
            }
        }
    }
}
