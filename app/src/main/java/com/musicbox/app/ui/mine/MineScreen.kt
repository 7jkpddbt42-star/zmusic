package com.musicbox.app.ui.mine

import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicbox.app.data.NetEaseSession
import com.musicbox.app.model.PlaylistSummary
import com.musicbox.app.model.Song
import com.musicbox.app.ui.components.Entrance
import com.musicbox.app.ui.components.GlassPanel
import com.musicbox.app.ui.components.MusicIcons
import com.musicbox.app.ui.components.PlaylistRow
import com.musicbox.app.ui.home.PlaylistTarget
import com.musicbox.app.ui.theme.ArtworkImage

/**
 * 「我的」页：按网易云手机端「我的」页的排布重做。
 *
 * 顶部是账号行（头像 / 昵称 / 设置），下面一行四个快捷入口（我喜欢的 / 最近播放 / 已下载 / 我的歌单），
 * 再往下是平铺的歌单列表。原来那几张卡（统计卡、下载卡、设置卡）都收进了这几个入口。
 */
@Composable
fun MineScreen(
    session: NetEaseSession,
    history: List<Song>,
    liked: PlaylistSummary?,
    playlists: List<PlaylistSummary>,
    /** 本机累计收听秒数，来自 [com.musicbox.app.data.ListeningStatsStore]。 */
    listeningSeconds: Long,
    /** 本机已下载歌曲数，显示在「已下载」入口下。 */
    downloadedCount: Int,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenTarget: (PlaylistTarget) -> Unit,
    onOpenPlaylists: () -> Unit,
    modifier: Modifier = Modifier,
    // 页面状态由主壳层持有：切走再切回来时滚动位置保留、入场动画不重播
    scrollState: ScrollState = rememberScrollState(),
    animateEntrance: Boolean = true,
) {
    val openLiked = {
        if (!session.isLoggedIn) {
            onLogin()
        } else {
            onOpenTarget(
                PlaylistTarget(
                    title = "我喜欢的音乐",
                    coverUrl = liked?.coverUrl,
                    subtitle = liked?.let { "${it.trackCount} 首 · 网易云同步" } ?: "网易云同步",
                    playlistId = liked?.id,
                    isLiked = true,
                ),
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
    ) {
        Entrance(index = 0, animate = animateEntrance) {
            AccountHeader(
                session = session,
                listeningSeconds = listeningSeconds,
                onLogin = onLogin,
                onLogout = onLogout,
                onOpenSettings = onOpenSettings,
            )
        }

        Entrance(index = 1, animate = animateEntrance) {
            QuickActions(
                likedCount = liked?.trackCount,
                historyCount = history.size,
                downloadedCount = downloadedCount,
                playlistCount = if (session.isLoggedIn) playlists.size else null,
                onOpenLiked = openLiked,
                onOpenHistory = onOpenHistory,
                onOpenDownloads = onOpenDownloads,
                onOpenPlaylists = if (session.isLoggedIn) onOpenPlaylists else onLogin,
            )
        }

        Entrance(index = 2, animate = animateEntrance) {
            SectionTitle(
                title = "我的歌单",
                action = if (session.isLoggedIn && playlists.size > PLAYLIST_PREVIEW) {
                    "全部 ${playlists.size}"
                } else {
                    null
                },
                onAction = onOpenPlaylists,
            )
        }

        Entrance(index = 3, animate = animateEntrance) {
            when {
                !session.isLoggedIn -> LoginCard(onLogin = onLogin)

                playlists.isEmpty() -> Text(
                    text = "还没有创建歌单，在网易云音乐创建后会同步到这里",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )

                else -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                ) {
                    // 只渲染预览条目，完整列表走「全部歌单」的 LazyColumn
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

        Spacer(modifier = Modifier.height(24.dp))
    }
}

private const val PLAYLIST_PREVIEW = 8

/**
 * 未登录时的歌单区：不再只留一行「登录后同步」，而是给一块完整的玻璃卡片和明确入口。
 * 空页面本来就缺少内容，这张卡同时补上视觉重量和唯一的下一步动作。
 */
@Composable
private fun LoginCard(onLogin: () -> Unit) {
    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text(
                text = "登录网易云音乐",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "用网易云音乐 App 扫码授权",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Box(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onLogin)
                    .padding(horizontal = 18.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "立即登录",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/** 顶部账号行：圆形头像 + 昵称 + 一行状态；右侧设置（已登录时另有「退出」）。 */
@Composable
private fun AccountHeader(
    session: NetEaseSession,
    listeningSeconds: Long,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(primary.copy(alpha = 0.14f))
                .clickable(onClick = if (session.isLoggedIn) ({}) else onLogin),
            contentAlignment = Alignment.Center,
        ) {
            if (!session.avatarUrl.isNullOrBlank()) {
                ArtworkImage(
                    url = session.avatarUrl,
                    modifier = Modifier.size(58.dp),
                    shape = CircleShape,
                    maxEdgePx = AVATAR_EDGE_PX,
                )
            } else {
                Icon(
                    imageVector = MusicIcons.Person,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = if (session.isLoggedIn) session.nickname ?: "已登录网易云" else "点击登录",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onLogin),
            )
            Text(
                text = accountSubtitle(session, listeningSeconds),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }

        if (session.isLoggedIn) {
            Text(
                text = "退出",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onLogout)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = MusicIcons.Sliders,
                contentDescription = "设置",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

/** 四个快捷入口：图标 + 名称 + 数量，横向均分，点按进入对应页面。 */
@Composable
private fun QuickActions(
    likedCount: Int?,
    historyCount: Int,
    downloadedCount: Int,
    playlistCount: Int?,
    onOpenLiked: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenPlaylists: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuickAction(MusicIcons.Heart, "我喜欢的", likedCount, onOpenLiked, Modifier.weight(1f))
        QuickAction(MusicIcons.History, "最近播放", historyCount, onOpenHistory, Modifier.weight(1f))
        QuickAction(MusicIcons.DownloadDone, "已下载", downloadedCount, onOpenDownloads, Modifier.weight(1f))
        QuickAction(MusicIcons.MusicNote, "我的歌单", playlistCount, onOpenPlaylists, Modifier.weight(1f))
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    count: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 6.dp),
            maxLines = 1,
        )
        // 数量行始终占位，四个入口的文字基线才对得齐
        Text(
            text = count?.takeIf { it > 0 }?.toString() ?: "—",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = 1.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun SectionTitle(
    title: String,
    action: String?,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Text(
                text = "$action ›",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }
    }
}

/** 网易云头像按显示尺寸解码，避免列表里拉全尺寸位图。 */
private const val AVATAR_EDGE_PX = 240

/** 账号副标题：网易云累计听歌（首）与本机收听时长（真实播放时长）拼在同一行。 */
private fun accountSubtitle(session: NetEaseSession, listeningSeconds: Long): String {
    if (!session.isLoggedIn) return "登录后同步推荐、歌单与听歌记录"
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
