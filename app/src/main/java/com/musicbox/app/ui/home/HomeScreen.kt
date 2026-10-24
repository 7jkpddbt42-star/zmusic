package com.musicbox.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicbox.app.data.NetEaseSession
import com.musicbox.app.model.PlaylistSummary
import com.musicbox.app.model.Song
import com.musicbox.app.ui.components.GlassPanel
import com.musicbox.app.ui.components.GlassPill
import com.musicbox.app.ui.components.Entrance
import com.musicbox.app.ui.components.MusicIcons
import com.musicbox.app.ui.components.PageHeader
import com.musicbox.app.ui.components.PlaylistRow
import com.musicbox.app.ui.library.SongRow
import com.musicbox.app.ui.player.formatTime
import com.musicbox.app.ui.theme.ArtworkImage
import com.musicbox.app.ui.theme.THUMB_EDGE_PX
import java.util.Calendar

/**
 * 「发现」页（默认首页）：网易云式个人内容。
 *
 * 结构：问候头 → 游客登录卡 → 每日推荐 / 私人FM / 我喜欢的音乐三块入口 →
 * 每日推荐试听列表 → 我的歌单。整页沿用玻璃面板 + 手绘线性图标。
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    session: NetEaseSession,
    fmLoading: Boolean,
    nowPlayingSongId: Long?,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onPlayFm: () -> Unit,
    onOpenTarget: (PlaylistTarget) -> Unit,
    onOpenPlaylists: () -> Unit,
    onPlayList: (List<Song>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "早上好"
            in 12..17 -> "下午好"
            else -> "晚上好"
        }
    }
    val dailyTarget = PlaylistTarget(
        title = "每日推荐",
        coverUrl = state.daily.firstOrNull()?.coverUrl,
        subtitle = "${state.daily.size} 首 · 每天 6:00 更新",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Entrance(index = 0) {
            PageHeader(
                title = "发现",
                subtitle = if (session.isLoggedIn) {
                    "$greeting，${session.nickname ?: "音乐爱好者"}"
                } else {
                    "$greeting，登录网易云同步你的每日推荐"
                },
                leading = if (session.isLoggedIn && !session.avatarUrl.isNullOrBlank()) {
                    {
                        ArtworkImage(
                            url = session.avatarUrl,
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            maxEdgePx = THUMB_EDGE_PX,
                        )
                    }
                } else {
                    null
                },
                trailing = {
                    GlassPill(onClick = onRefresh) {
                        Text(
                            text = "刷新",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }

        if (!session.isLoggedIn) {
            Entrance(index = 1) {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(primary.copy(alpha = 0.14f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = MusicIcons.Person,
                                contentDescription = null,
                                tint = primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(
                                text = "登录网易云音乐",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "同步我喜欢的音乐与个人歌单",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        GlassPill(onClick = onLogin, tint = primary.copy(alpha = 0.14f)) {
                            Text(
                                text = "登录",
                                style = MaterialTheme.typography.labelLarge,
                                color = primary,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Entrance(index = 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FeatureTile(
                    icon = MusicIcons.MusicNote,
                    title = "每日推荐",
                    subtitle = if (state.daily.isEmpty()) "等待加载" else "${state.daily.size} 首",
                    onClick = { if (state.daily.isNotEmpty()) onOpenTarget(dailyTarget) },
                    modifier = Modifier.weight(1f),
                )
                FeatureTile(
                    icon = MusicIcons.Play,
                    title = "私人FM",
                    subtitle = "为你播放",
                    loading = fmLoading,
                    onClick = onPlayFm,
                    modifier = Modifier.weight(1f),
                )
                FeatureTile(
                    icon = if (state.liked != null) MusicIcons.HeartFilled else MusicIcons.Heart,
                    title = "我喜欢的",
                    subtitle = state.liked?.let { "${it.trackCount} 首" } ?: "登录同步",
                    onClick = {
                        val liked = state.liked
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
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Entrance(index = 3) {
            SectionHeader(
                title = "每日推荐",
                subtitle = if (state.error != null) null else "根据你的口味每日更新",
                trailing = if (state.daily.isNotEmpty()) {
                    {
                        GlassPill(
                            onClick = { onPlayList(state.daily, 0) },
                            tint = primary.copy(alpha = 0.14f),
                        ) {
                            Icon(
                                imageVector = MusicIcons.Play,
                                contentDescription = null,
                                tint = primary,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "播放全部",
                                style = MaterialTheme.typography.labelMedium,
                                color = primary,
                            )
                        }
                    }
                } else {
                    null
                },
            )
        }

        Entrance(index = 4) {
            when {
                state.error != null -> ErrorPanel(message = state.error, onRetry = onRefresh)
                state.daily.isEmpty() && state.loading -> LoadingPanel("正在获取每日推荐…")
                state.daily.isEmpty() -> LoadingPanel("今天还没有推荐，稍后再刷新看看")
                else -> {
                    GlassPanel(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            state.daily.take(PREVIEW_COUNT).forEachIndexed { index, song ->
                                SongRow(
                                    song = song,
                                    isCurrent = song.id == nowPlayingSongId,
                                    onClick = { onPlayList(state.daily, index) },
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

        Entrance(index = 5) {
            SectionHeader(
                title = "我的歌单",
                subtitle = when {
                    !session.isLoggedIn -> "登录后同步网易云歌单"
                    state.playlists.isEmpty() && state.loading -> "正在同步…"
                    state.playlists.isEmpty() -> "还没有创建歌单"
                    else -> "${state.playlists.size} 个歌单"
                },
                trailing = if (state.playlists.size > PREVIEW_COUNT) {
                    {
                        GlassPill(onClick = onOpenPlaylists) {
                            Text(
                                text = "全部 ${state.playlists.size}",
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
                LoginHint(onLogin)
            } else if (state.playlists.isNotEmpty()) {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        // 只渲染预览条目：整页是 verticalScroll，全量组合会让滚动时每帧重绘上百行
                        state.playlists.take(PREVIEW_COUNT).forEach { playlist ->
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
            } else {
                LoadingPanel(if (state.loading) "正在同步你的歌单…" else "登录网易云后，你的歌单会出现在这里")
            }
        }

        Entrance(index = 7) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "MusicBox · 每日推荐与私人FM由网易云提供",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

private const val PREVIEW_COUNT = 5

/** 三宫格入口卡：圆形图标 + 标题 + 一行说明。 */
@Composable
private fun FeatureTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    val primary = MaterialTheme.colorScheme.primary
    GlassPanel(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = primary,
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 9.dp),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
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

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = MusicIcons.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = "每日推荐加载失败",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            GlassPill(onClick = onRetry) {
                Text(
                    text = "重试",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun LoadingPanel(text: String) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = MusicIcons.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoginHint(onLogin: () -> Unit) {
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
                imageVector = MusicIcons.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = "登录后同步我的歌单",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "每日推荐与私人FM在游客态也可试听",
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
}
