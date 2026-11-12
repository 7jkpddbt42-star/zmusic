package com.musicbox.app.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import com.musicbox.app.ui.components.Entrance
import com.musicbox.app.ui.components.GlassBackButton
import com.musicbox.app.ui.components.GlassEmptyState
import com.musicbox.app.ui.components.GlassListPanel
import com.musicbox.app.ui.components.GlassPanel
import com.musicbox.app.ui.components.GlassPill
import com.musicbox.app.ui.components.MusicIcons
import com.musicbox.app.ui.components.PageHeader
import com.musicbox.app.ui.theme.ArtworkImage
import com.musicbox.app.ui.theme.THUMB_EDGE_PX
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
        // 与首页/我的页同一套入场节奏：页头 → 下载面板 → 列表逐块浮现
        Entrance(index = 0) {
            PageHeader(
                title = "已下载",
                subtitle = if (songs.isEmpty()) {
                    null
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
        }

        // 未完成的下载放在最上面，行样式与下方歌曲列表保持一致；
        // 出现 / 收起都带高度过渡，避免下载一开始列表突然跳位
        Entrance(index = 1) {
            AnimatedVisibility(
                visible = tasks.isNotEmpty(),
                enter = expandVertically(
                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                ) + fadeIn(tween(200)),
                exit = shrinkVertically(
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                ) + fadeOut(tween(140)),
            ) {
                Column {
                    GlassPanel(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
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
            }
        }

        Entrance(index = 2, modifier = Modifier.weight(1f)) {
            if (songs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlassEmptyState(
                        icon = MusicIcons.DownloadDone,
                        title = "还没有下载的歌曲",
                        description = "歌单里点「下载全部」，或在播放页右上角「更多」里下载当前这一首。",
                        modifier = Modifier.padding(bottom = 48.dp),
                    )
                }
            } else {
                GlassListPanel(
                    // 短列表让面板贴住内容收口（+8dp 是面板与底部浮层的间距）
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = songListPanelHeight(sorted.size) + 8.dp)
                        .padding(bottom = 8.dp),
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

/** 一行未完成的下载：版式与 [SongRow] 一致（封面 + 歌名 / 状态），右侧是暂停·继续·重试与取消。 */
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
    val toggleIcon = when (state) {
        is DownloadState.Failed -> MusicIcons.Download
        is DownloadState.Paused -> MusicIcons.Play
        else -> MusicIcons.Pause
    }
    val toggleLabel = when (state) {
        is DownloadState.Failed -> "重试下载"
        is DownloadState.Paused -> "继续下载"
        else -> "暂停下载"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(
            url = task.song.coverUrl,
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(10.dp),
            maxEdgePx = THUMB_EDGE_PX,
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(
                text = task.song.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (state is DownloadState.Failed) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = toggleIcon,
                contentDescription = toggleLabel,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(22.dp))
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
