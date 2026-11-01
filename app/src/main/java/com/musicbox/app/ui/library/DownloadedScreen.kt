package com.musicbox.app.ui.library

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicbox.app.model.Song
import com.musicbox.app.player.DownloadState
import com.musicbox.app.player.DownloadTask
import com.musicbox.app.player.DownloadedSong
import com.musicbox.app.ui.components.GlassBackButton
import com.musicbox.app.ui.components.GlassEmptyState
import com.musicbox.app.ui.components.GlassListPanel
import com.musicbox.app.ui.components.GlassPanel
import com.musicbox.app.ui.components.GlassPill
import com.musicbox.app.ui.components.MusicIcons
import com.musicbox.app.ui.components.PageHeader
import java.util.Locale

/** 列表排序方式：默认按保存时间（新的在前），也可以按体积（大的在前）找占地方的那些。 */
private enum class DownloadSort { Recent, Size }

/**
 * 已下载列表：点整行直接播（本地文件，断网也能播），行尾垃圾桶删除单曲。
 * 页头右侧可切换排序、清空全部（带二次确认）。
 * 顶部额外展示未完成的下载（下载中 / 已暂停 / 失败），可暂停、继续、重试或取消。
 */
@Composable
fun DownloadedScreen(
    songs: List<DownloadedSong>,
    tasks: List<DownloadTask>,
    nowPlayingSongId: Long?,
    onSongClick: (Song) -> Unit,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit,
    onToggleTask: (DownloadTask) -> Unit,
    onCancelTask: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sort by remember { mutableStateOf(DownloadSort.Recent) }
    var confirmClear by remember { mutableStateOf(false) }

    val sorted = remember(songs, sort) {
        when (sort) {
            DownloadSort.Recent -> songs
            DownloadSort.Size -> songs.sortedByDescending { it.sizeBytes }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        PageHeader(
            title = "已下载",
            subtitle = if (songs.isEmpty()) {
                "下载过的歌会存在本机，断网也能听"
            } else {
                "${songs.size} 首 · 共 ${formatBytes(songs.sumOf { it.sizeBytes })}"
            },
            leading = { GlassBackButton(onClick = onBack) },
            trailing = if (songs.isEmpty()) {
                null
            } else {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassPill(
                            onClick = {
                                sort = if (sort == DownloadSort.Recent) {
                                    DownloadSort.Size
                                } else {
                                    DownloadSort.Recent
                                }
                            },
                        ) {
                            Text(
                                text = if (sort == DownloadSort.Recent) "按时间" else "按体积",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        GlassPill(onClick = { confirmClear = true }) {
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
                }
            },
        )
        // 未完成的下载放在最上面：下载中/暂停/失败都能直接操作，不必回播放页
        if (tasks.isNotEmpty()) {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        text = "下载中 · ${tasks.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    tasks.forEach { task ->
                        DownloadTaskRow(
                            task = task,
                            onToggle = { onToggleTask(task) },
                            onCancel = { onCancelTask(task.song.id) },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.size(10.dp, 10.dp))
        }

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
                itemsIndexed(sorted, key = { _, item -> item.song.id }) { _, item ->
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
                                // 触控目标按 48dp 的最小可点尺寸留足，图标本身仍是 16dp
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(20.dp))
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

    if (confirmClear) {
        val totalBytes = songs.sumOf { it.sizeBytes }
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空所有下载？") },
            text = {
                Text(
                    "将删除本机 ${songs.size} 首已下载歌曲，释放 ${formatBytes(totalBytes)}；" +
                        "收藏与播放记录不受影响。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClearAll()
                    },
                ) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
}

/** 一行未完成的下载：歌名 / 状态与进度条，右侧是暂停·继续·重试与取消。 */
@Composable
private fun DownloadTaskRow(
    task: DownloadTask,
    onToggle: () -> Unit,
    onCancel: () -> Unit,
) {
    val state = task.state
    val progress = when (state) {
        is DownloadState.InProgress -> state.progress
        is DownloadState.Paused -> state.progress
        else -> 0f
    }
    val statusText = when (state) {
        is DownloadState.InProgress ->
            if (progress >= 0f) "下载中 ${(progress * 100).toInt()}%" else "下载中"
        is DownloadState.Paused -> "已暂停 ${(progress * 100).toInt()}%"
        is DownloadState.Failed -> state.message
        DownloadState.Done -> "已完成"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.song.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (state is DownloadState.InProgress || state is DownloadState.Paused) {
                LinearProgressIndicator(
                    progress = { if (progress < 0f) 0f else progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, end = 16.dp)
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                )
            }
        }
        // 暂停 / 继续 / 重试：48dp 触控目标
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when (state) {
                    is DownloadState.Failed -> MusicIcons.Download
                    else -> MusicIcons.Pause
                },
                contentDescription = when (state) {
                    is DownloadState.Failed -> "重试下载"
                    else -> "暂停下载"
                },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MusicIcons.Close,
                contentDescription = "取消下载",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp),
            )
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
