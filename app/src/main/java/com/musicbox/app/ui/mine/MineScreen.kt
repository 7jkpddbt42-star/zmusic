package com.musicbox.app.ui.mine

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.musicbox.app.data.NetEaseSession
import com.musicbox.app.model.PlaylistSummary
import com.musicbox.app.model.Song
import com.musicbox.app.ui.components.Entrance
import com.musicbox.app.ui.components.GlassEmptyState
import com.musicbox.app.ui.components.GlassPanel
import com.musicbox.app.ui.components.GlassPill
import com.musicbox.app.ui.components.MusicIcons
import com.musicbox.app.ui.components.PageHeader
import com.musicbox.app.ui.components.PlaylistRow
import com.musicbox.app.ui.home.PlaylistTarget
import com.musicbox.app.ui.library.SongRow
import com.musicbox.app.ui.player.formatTime
import com.musicbox.app.ui.theme.ArtworkImage
import com.musicbox.app.ui.theme.THUMB_EDGE_PX

/**
 * 「我的」页（网易云式排版）。
 *
 * 账号卡 → 我的音乐入口（我喜欢的 / 最近播放）→ 最近播放预览 →
 * 我的歌单 → 设置。听歌历史从底部标签迁入本页，并可展开查看全部。
 */
@Composable
fun MineScreen(
    session: NetEaseSession,
    history: List<Song>,
    liked: PlaylistSummary?,
    playlists: List<PlaylistSummary>,
    nowPlayingSongId: Long?,
    /** 本机累计收听秒数，来自 [com.musicbox.app.data.ListeningStatsStore]。 */
    listeningSeconds: Long,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTarget: (PlaylistTarget) -> Unit,
    onOpenPlaylists: () -> Unit,
    onSongClick: (List<Song>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Entrance(index = 0) {
            PageHeader(
                title = "我的",
                subtitle = if (session.isLoggedIn) "网易云账号与本地音乐" else "本地音乐与游客模式",
            )
        }

        // 账号卡
        Entrance(index = 1) {
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (session.isLoggedIn && !session.avatarUrl.isNullOrBlank()) {
                        // 网易云账号头像；加载失败时 ArtworkImage 会显示音符占位
                        ArtworkImage(
                            url = session.avatarUrl,
                            modifier = Modifier.size(58.dp),
                            shape = CircleShape,
                            maxEdgePx = THUMB_EDGE_PX,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .background(primary.copy(alpha = 0.14f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = MusicIcons.Person,
                                contentDescription = null,
                                tint = primary,
                                modifier = Modifier.size(27.dp),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(
                            text = if (session.isLoggedIn) session.nickname ?: "已登录网易云" else "游客模式",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = accountSubtitle(session, listeningSeconds),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    GlassPill(
                        onClick = if (session.isLoggedIn) onLogout else onLogin,
                        tint = primary.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = if (session.isLoggedIn) "退出" else "登录",
                            style = MaterialTheme.typography.labelLarge,
                            color = primary,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 我的音乐入口
        Entrance(index = 2) {
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    MusicEntry(
                        icon = if (liked != null) MusicIcons.HeartFilled else MusicIcons.Heart,
                        title = "我喜欢的音乐",
                        subtitle = when {
                            liked != null -> "${liked.trackCount} 首 · 网易云同步"
                            session.isLoggedIn -> "登录信息同步后显示"
                            else -> "登录后同步"
                        },
                        onClick = {
                            if (liked != null) {
                                onOpenTarget(
                                    PlaylistTarget(
                                        title = "我喜欢的音乐",
                                        coverUrl = liked.coverUrl,
                                        subtitle = "${liked.trackCount} 首 · 网易云同步",
                                        playlistId = liked.id,
                                    ),
                                )
                            } else {
                                onLogin()
                            }
                        },
                    )
                    EntryDivider()
                    MusicEntry(
                        icon = MusicIcons.History,
                        title = "最近播放",
                        subtitle = "${history.size} 首 · 听歌历史",
                        onClick = onOpenHistory,
                    )
                }
            }
        }

        // 最近播放预览
        Entrance(index = 3) {
            SectionHeader(
                title = "最近播放",
                subtitle = if (history.isEmpty()) "播放过的歌会自动记录" else "${history.size} 首",
                trailing = if (history.isNotEmpty()) {
                    {
                        GlassPill(onClick = onOpenHistory) {
                            Text(
                                text = "查看全部",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    null
                },
            )
        }

        Entrance(index = 4) {
            if (history.isEmpty()) {
                GlassEmptyState(
                    icon = MusicIcons.History,
                    title = "还没有播放记录",
                    description = "播放过的歌曲会出现在这里，方便回来再听。",
                )
            } else {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        history.take(HISTORY_PREVIEW).forEachIndexed { index, song ->
                            SongRow(
                                song = song,
                                isCurrent = song.id == nowPlayingSongId,
                                onClick = { onSongClick(history, index) },
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

        // 我的歌单
        Entrance(index = 5) {
            SectionHeader(
                title = "我的歌单",
                subtitle = when {
                    !session.isLoggedIn -> "登录后同步网易云歌单"
                    playlists.isEmpty() -> "还没有创建歌单"
                    else -> "${playlists.size} 个歌单"
                },
                trailing = if (playlists.size > PLAYLIST_PREVIEW) {
                    {
                        GlassPill(onClick = onOpenPlaylists) {
                            Text(
                                text = "全部 ${playlists.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    null
                },
            )
        }

        Entrance(index = 6) {
            if (!session.isLoggedIn) {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(26.dp))
                            .clickable(onClick = onLogin)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = MusicIcons.MusicNote,
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(
                                text = "登录网易云音乐",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "同步你创建和收藏的歌单",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Icon(
                            imageVector = MusicIcons.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            } else if (playlists.isNotEmpty()) {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        // 只渲染预览条目，完整列表走「全部」覆盖页的 LazyColumn
                        playlists.take(PLAYLIST_PREVIEW).forEach { playlist ->
                            PlaylistRow(
                                summary = playlist,
                                onClick = {
                                    onOpenTarget(
                                        PlaylistTarget(
                                            title = playlist.name,
                                            coverUrl = playlist.coverUrl,
                                            subtitle = "${playlist.trackCount} 首 · 我的歌单",
                                            playlistId = playlist.id,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 设置入口
        Entrance(index = 7) {
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenSettings)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = MusicIcons.Sliders,
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            text = "设置",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "主题、背景、桌面歌词与备用音源",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = MusicIcons.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Entrance(index = 8) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "MusicBox · 纯原生在线播放器",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

private const val HISTORY_PREVIEW = 5
private const val PLAYLIST_PREVIEW = 5

/** 账号卡副标题：网易云累计听歌（首）与本机收听时长（真实播放时长）拼在同一行。 */
private fun accountSubtitle(session: NetEaseSession, listeningSeconds: Long): String {
    if (!session.isLoggedIn) return "登录后同步推荐与个人歌单"
    val parts = buildList {
        if (session.listenSongs > 0) add("累计听歌 ${session.listenSongs} 首")
        if (listeningSeconds > 0) add("本机收听 ${formatListeningTime(listeningSeconds)}")
    }
    return parts.joinToString(" · ").ifEmpty {
        if (session.userId > 0) "网易云 ID ${session.userId}" else "正在同步账号信息…"
    }
}

/** 秒 → 「X 小时 Y 分」/「Y 分钟」/「Z 秒」。 */
private fun formatListeningTime(seconds: Long): String {
    if (seconds < 60) return "${seconds} 秒"
    val totalMinutes = seconds / 60
    if (totalMinutes < 60) return "$totalMinutes 分钟"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (minutes == 0L) "$hours 小时" else "$hours 小时 $minutes 分"
}

/** 我的音乐入口行。 */
@Composable
private fun MusicEntry(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(19.dp),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Icon(
            imageVector = MusicIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun EntryDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 62.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        trailing?.invoke()
    }
}
