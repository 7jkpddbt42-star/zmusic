package com.musicbox.app.ui.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicbox.app.data.NetEaseSession
import com.musicbox.app.model.PlaylistSummary
import com.musicbox.app.model.Song
import com.musicbox.app.ui.components.Entrance
import com.musicbox.app.ui.components.GlassPill
import com.musicbox.app.ui.components.MusicIcons
import com.musicbox.app.ui.theme.ArtworkImage
import com.musicbox.app.ui.theme.THUMB_EDGE_PX

/**
 * 「发现」页：按网易云音乐手机端首页的排布重做。
 *
 * 自上而下：搜索栏 → 每日推荐大图 → 金刚区入口 → 最近播放横滑卡片 → 我的歌单三列网格。
 * 整页是普通的竖向滚动，没有横向嵌套滚动容器。
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    session: NetEaseSession,
    history: List<Song>,
    fmLoading: Boolean,
    nowPlayingSongId: Long?,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onPlayFm: () -> Unit,
    onOpenTarget: (PlaylistTarget) -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenHistory: () -> Unit,
    onPlayList: (List<Song>, Int) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
    // 页面状态由主壳层持有：切走再切回来时滚动位置保留、入场动画不重播
    scrollState: ScrollState = rememberScrollState(),
    animateEntrance: Boolean = true,
) {
    val dailyTarget = PlaylistTarget(
        title = "每日推荐",
        coverUrl = state.daily.firstOrNull()?.coverUrl,
        subtitle = "${state.daily.size} 首",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
    ) {
        Entrance(index = 0, animate = animateEntrance) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "发现",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // 胶囊底色比正文深一层：在彩色封面背景上也能一眼找到这个操作
                    GlassPill(
                        onClick = onRefresh,
                        tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.52f),
                    ) {
                        if (state.loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 1.8.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Text(
                                text = "刷新",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                SearchBar(onClick = onOpenSearch)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Entrance(index = 1, animate = animateEntrance) {
            DailyBanner(
                state = state,
                onOpen = { if (state.daily.isNotEmpty()) onOpenTarget(dailyTarget) },
                onPlayAll = { if (state.daily.isNotEmpty()) onPlayList(state.daily, 0) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Entrance(index = 2, animate = animateEntrance) {
            QuickEntries(
                state = state,
                session = session,
                fmLoading = fmLoading,
                onPlayFm = onPlayFm,
                onOpenHistory = onOpenHistory,
                onOpenDaily = { if (state.daily.isNotEmpty()) onOpenTarget(dailyTarget) },
                onOpenLiked = {
                    if (!session.isLoggedIn) {
                        onLogin()
                    } else {
                        onOpenTarget(
                            PlaylistTarget(
                                title = "我喜欢的音乐",
                                coverUrl = state.liked?.coverUrl,
                                subtitle = state.liked?.let { "${it.trackCount} 首" } ?: "",
                                playlistId = state.liked?.id,
                                isLiked = true,
                            ),
                        )
                    }
                },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        if (history.isNotEmpty()) {
            Entrance(index = 3, animate = animateEntrance) {
                SectionTitle(
                    title = "最近播放",
                    action = "更多",
                    onAction = onOpenHistory,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Entrance(index = 4, animate = animateEntrance) {
                HistoryCards(
                    history = history,
                    nowPlayingSongId = nowPlayingSongId,
                    onPlay = { index -> onPlayList(history, index) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Spacer(modifier = Modifier.height(22.dp))
        }

        if (session.isLoggedIn && state.playlists.isNotEmpty()) {
            Entrance(index = 5, animate = animateEntrance) {
                SectionTitle(
                    title = "我的歌单",
                    action = if (state.playlists.size > GRID_PREVIEW) {
                        "全部 ${state.playlists.size}"
                    } else {
                        null
                    },
                    onAction = onOpenPlaylists,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Entrance(index = 6, animate = animateEntrance) {
                PlaylistGrid(
                    playlists = state.playlists,
                    onOpen = { playlist ->
                        onOpenTarget(
                            PlaylistTarget(
                                title = playlist.name,
                                coverUrl = playlist.coverUrl,
                                subtitle = "${playlist.trackCount} 首",
                                playlistId = playlist.id,
                            ),
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** 搜索栏：点一下直接跳到搜索页，不在首页塞任何输入框。 */
@Composable
private fun SearchBar(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            // 浅色半透明底 + 一道极细描边：在灰调背景上仍有清晰的形状边界
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.58f))
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MusicIcons.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "搜索歌曲、歌手或歌单",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 每日推荐大图：整幅封面 + 底部渐变压暗，右下角一个播放入口。 */
@Composable
private fun DailyBanner(
    state: HomeUiState,
    onOpen: () -> Unit,
    onPlayAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(184.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen),
    ) {
        ArtworkImage(
            url = state.daily.firstOrNull()?.coverUrl,
            modifier = Modifier.matchParentSize(),
            maxEdgePx = THUMB_EDGE_PX * 4,
        )
        // 封面缺失或还在加载时垫一层品牌色，避免整块发白
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    // 底部压得更重一些：白色/浅色封面下标题与副标题也要读得清。
                    // 三个停靠点都放在下半区，上半区封面保持通透。
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Black.copy(alpha = 0.06f),
                        0.72f to Color.Black.copy(alpha = 0.46f),
                        1f to Color.Black.copy(alpha = 0.78f),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 72.dp, bottom = 16.dp),
        ) {
            Text(
                text = "每日推荐",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = if (state.daily.isEmpty()) "正在为你准备" else "${state.daily.size} 首 · 每天 6:00 更新",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.84f),
                maxLines = 1,
            )
        }

        // 播放入口：右下角圆形按钮
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
                .size(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onPlayAll),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MusicIcons.Play,
                contentDescription = "播放每日推荐",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** 金刚区：四个入口，图标不加圆圈底，直接一行平铺。 */
@Composable
private fun QuickEntries(
    state: HomeUiState,
    session: NetEaseSession,
    fmLoading: Boolean,
    onPlayFm: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDaily: () -> Unit,
    onOpenLiked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        QuickEntry(
            icon = MusicIcons.MusicNote,
            label = "每日推荐",
            onClick = onOpenDaily,
            modifier = Modifier.weight(1f),
        )
        QuickEntry(
            icon = MusicIcons.Speed,
            label = "私人 FM",
            loading = fmLoading,
            onClick = onPlayFm,
            modifier = Modifier.weight(1f),
        )
        QuickEntry(
            icon = if (session.isLoggedIn) MusicIcons.HeartFilled else MusicIcons.Heart,
            label = "我喜欢的",
            onClick = onOpenLiked,
            modifier = Modifier.weight(1f),
        )
        QuickEntry(
            icon = MusicIcons.History,
            label = "最近播放",
            onClick = onOpenHistory,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickEntry(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 1.8.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 区块标题：左边粗体标题，右边「更多 ›」。 */
@Composable
private fun SectionTitle(
    title: String,
    action: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (action != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = action,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = MusicIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** 最近播放：横向滑动的封面卡，和网易云的歌单卡同一套版式。 */
@Composable
private fun HistoryCards(
    history: List<Song>,
    nowPlayingSongId: Long?,
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        history.take(CARD_PREVIEW).forEachIndexed { index, song ->
            Column(
                modifier = Modifier
                    .width(118.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPlay(index) },
            ) {
                Box(modifier = Modifier.size(118.dp)) {
                    ArtworkImage(
                        url = song.coverUrl,
                        modifier = Modifier.matchParentSize().clip(RoundedCornerShape(12.dp)),
                        maxEdgePx = THUMB_EDGE_PX,
                    )
                    if (song.id == nowPlayingSongId) {
                        Text(
                            text = "正在播放",
                            fontSize = 10.sp,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color.Black.copy(alpha = 0.56f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(7.dp))
                Text(
                    text = song.name,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artists.joinToString(" / "),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 我的歌单：三列网格，封面右下角标曲目数。 */
@Composable
private fun PlaylistGrid(
    playlists: List<PlaylistSummary>,
    onOpen: (PlaylistSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        playlists.take(GRID_PREVIEW).chunked(3).forEachIndexed { index, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { playlist ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpen(playlist) },
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                            ArtworkImage(
                                url = playlist.coverUrl,
                                modifier = Modifier.matchParentSize().clip(RoundedCornerShape(12.dp)),
                                maxEdgePx = THUMB_EDGE_PX,
                            )
                            Text(
                                text = "${playlist.trackCount} 首",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.92f),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(Color.Black.copy(alpha = 0.38f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = playlist.name,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // 最后一行不足三个时补位，保证列宽一致
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
            if (index < playlists.take(GRID_PREVIEW).chunked(3).lastIndex) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/** 最近播放横滑卡片数。 */
private const val CARD_PREVIEW = 10

/** 我的歌单网格最多展示多少张。 */
private const val GRID_PREVIEW = 9

