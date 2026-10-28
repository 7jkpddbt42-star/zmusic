package com.musicbox.app.ui.mine

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicbox.app.data.NetEaseSession
import com.musicbox.app.model.PlaylistSummary
import com.musicbox.app.model.Song
import com.musicbox.app.ui.components.Entrance
import com.musicbox.app.ui.components.GlassEmptyState
import com.musicbox.app.ui.components.GlassPanel
import com.musicbox.app.ui.components.GlassPill
import com.musicbox.app.ui.components.MusicIcons
import com.musicbox.app.ui.components.PlaylistRow
import com.musicbox.app.ui.home.PlaylistTarget

/**
 * 「我的」页：账号与个人音乐的唯一入口。
 *
 * 结构：页头 → 账号卡（游客态即登录入口）→ 数据概览（我喜欢的 / 歌单 / 最近播放）→
 * 我的歌单 → 设置。首页不再放登录卡，登录入口全部收敛到这里。
 */
@Composable
fun MineScreen(
    session: NetEaseSession,
    history: List<Song>,
    liked: PlaylistSummary?,
    playlists: List<PlaylistSummary>,
    /** 本机累计收听秒数，来自 [com.musicbox.app.data.ListeningStatsStore]。 */
    listeningSeconds: Long,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTarget: (PlaylistTarget) -> Unit,
    onOpenPlaylists: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Entrance(index = 0) {
            MineHeader(session)
        }

        Entrance(index = 1) {
            if (session.isLoggedIn) {
                AccountCard(
                    session = session,
                    listeningSeconds = listeningSeconds,
                    onLogout = onLogout,
                )
            } else {
                LoginEntryCard(onLogin = onLogin)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Entrance(index = 2) {
            // 三格统计共用一块玻璃：减少两次背景采样，视觉上也更像一张数据卡
            GlassPanel(modifier = Modifier.fillMaxWidth(), shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatCard(
                        value = liked?.trackCount?.toString()
                            ?: if (session.isLoggedIn) "—" else "未登录",
                        label = "我喜欢的",
                        onClick = openLiked,
                        modifier = Modifier.weight(1f),
                    )
                    TileDivider()
                    StatCard(
                        value = if (session.isLoggedIn) playlists.size.toString() else "—",
                        label = "歌单",
                        onClick = if (session.isLoggedIn) onOpenPlaylists else onLogin,
                        modifier = Modifier.weight(1f),
                    )
                    TileDivider()
                    StatCard(
                        value = history.size.toString(),
                        label = "最近播放",
                        onClick = onOpenHistory,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (session.isLoggedIn) {
            Entrance(index = 3) {
                SectionHeader(
                    title = "我的歌单",
                    subtitle = when {
                        playlists.isEmpty() -> "还没有创建歌单"
                        else -> "${playlists.size} 个歌单 · 网易云同步"
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

            Entrance(index = 4) {
                if (playlists.isEmpty()) {
                    GlassEmptyState(
                        icon = MusicIcons.MusicNote,
                        title = "还没有创建歌单",
                        description = "在网易云音乐创建的歌单会同步到这里。",
                    )
                } else {
                    GlassPanel(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
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
            }
        }

        Entrance(index = if (session.isLoggedIn) 5 else 3) {
            SettingsCard(onOpenSettings = onOpenSettings)
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "z-music · 让每一次播放都算数",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

private const val PLAYLIST_PREVIEW = 5

/** 页头：小字标签 + 大标题 + 一行状态说明。 */
@Composable
private fun MineHeader(session: NetEaseSession) {
    val primary = MaterialTheme.colorScheme.primary
    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 14.dp)) {
        Text(
            text = "我的音乐",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 4.sp),
            color = primary.copy(alpha = 0.78f),
        )
        Text(
            text = "我的",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 32.sp,
                letterSpacing = (-0.6).sp,
            ),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = if (session.isLoggedIn) "账号、收藏与本地记录" else "游客也可以完整收听、搜索与收藏到本机",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** 已登录账号卡：大头像 + 昵称 + 累计收听，右侧退出。 */
@Composable
private fun AccountCard(
    session: NetEaseSession,
    listeningSeconds: Long,
    onLogout: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .border(1.2.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (!session.avatarUrl.isNullOrBlank()) {
                    com.musicbox.app.ui.theme.ArtworkImage(
                        url = session.avatarUrl,
                        modifier = Modifier.size(66.dp),
                        shape = CircleShape,
                        maxEdgePx = AVATAR_EDGE_PX,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .background(primary.copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = MusicIcons.Person,
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(
                    text = session.nickname ?: "已登录网易云",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = accountSubtitle(session, listeningSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            GlassPill(onClick = onLogout) {
                Text(
                    text = "退出",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 游客态登录卡：整个「我的」页唯一的登录入口。
 * 按压时轻微回缩，点击进入扫码登录。
 */
@Composable
private fun LoginEntryCard(onLogin: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 140, easing = LinearOutSlowInEasing),
        label = "loginEntryScale",
    )

    GlassPanel(
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale },
        tint = primary.copy(alpha = 0.10f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onLogin,
                )
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .background(primary.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MusicIcons.Person,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(30.dp),
                )
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(
                    text = "登录网易云音乐",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "同步每日推荐、我喜欢的音乐与个人歌单",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Icon(
                imageVector = MusicIcons.ChevronRight,
                contentDescription = null,
                tint = primary.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 数据概览小格：数字 + 名称（放在共享玻璃面板内）。 */
@Composable
private fun StatCard(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (value == "—" || value == "未登录") {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                primary
            },
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp),
            maxLines = 1,
        )
    }
}

/** 统计格之间的细分隔线。 */
@Composable
private fun TileDivider() {
    Box(
        modifier = Modifier
            .height(40.dp)
            .width(0.7.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f)),
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

@Composable
private fun SettingsCard(onOpenSettings: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .clickable(onClick = onOpenSettings)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(primary.copy(alpha = 0.12f), RoundedCornerShape(13.dp)),
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
}

/** 网易云头像按显示尺寸解码，避免列表里拉全尺寸位图。 */
private const val AVATAR_EDGE_PX = 240

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
