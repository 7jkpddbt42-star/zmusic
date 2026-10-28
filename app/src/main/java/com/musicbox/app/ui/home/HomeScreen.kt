package com.musicbox.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.musicbox.app.ui.library.SongRow
import com.musicbox.app.ui.player.formatTime
import com.musicbox.app.ui.theme.ArtworkImage
import com.musicbox.app.ui.theme.THUMB_EDGE_PX
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 「发现」页（默认首页）：网易云式个人内容。
 *
 * 结构：日期问候 → 今日推荐海报（当日首曲封面 + 视差）→ 三宫格入口 →
 * 最近播放 → 我的歌单。登录入口统一收在「我的」页，首页不再单独放登录卡。
 *
 * 仪式感来自三层：
 * 1. 海报式头图：整幅封面、暗色渐变、一次性扫光；
 * 2. 入场节奏：错峰淡入 + 头图轻微上浮放大；
 * 3. 滚动视差：只在 graphicsLayer 里读取滚动值，不触发重组。
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
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val scrollState = rememberScrollState()
    val calendar = remember { Calendar.getInstance() }
    val greeting = remember(calendar) {
        when (calendar.get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "早上好"
            in 12..17 -> "下午好"
            else -> "晚上好"
        }
    }
    val dateLabel = remember(calendar) {
        SimpleDateFormat("M月d日 · EEEE", Locale.CHINA).format(calendar.time)
    }
    val dailyTarget = PlaylistTarget(
        title = "每日推荐",
        coverUrl = state.daily.firstOrNull()?.coverUrl,
        subtitle = "${state.daily.size} 首 · 每天 6:00 更新",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp),
    ) {
        Entrance(index = 0) {
            DiscoverHeader(
                session = session,
                greeting = greeting,
                dateLabel = dateLabel,
                loading = state.loading,
                onRefresh = onRefresh,
            )
        }

        Entrance(index = 1) {
            DailyHero(
                state = state,
                scrollState = scrollState,
                primary = primary,
                onOpen = { if (state.daily.isNotEmpty()) onOpenTarget(dailyTarget) },
                onPlayAll = { if (state.daily.isNotEmpty()) onPlayList(state.daily, 0) },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Entrance(index = 2) {
            // 三宫格收进同一块玻璃：少两次全幅模糊/折射，观感也更整体
            GlassPanel(modifier = Modifier.fillMaxWidth(), shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FeatureTile(
                        icon = MusicIcons.History,
                        title = "最近播放",
                        subtitle = if (history.isEmpty()) "暂无记录" else "${history.size} 首",
                        onClick = onOpenHistory,
                        modifier = Modifier.weight(1f),
                    )
                    TileDivider()
                    FeatureTile(
                        icon = MusicIcons.Play,
                        title = "私人FM",
                        subtitle = "为你播放",
                        loading = fmLoading,
                        onClick = onPlayFm,
                        modifier = Modifier.weight(1f),
                    )
                    TileDivider()
                    FeatureTile(
                        icon = if (state.liked != null || session.isLoggedIn) {
                            MusicIcons.HeartFilled
                        } else {
                            MusicIcons.Heart
                        },
                        title = "我喜欢的",
                        subtitle = when {
                            !session.isLoggedIn -> "登录同步"
                            state.liked != null -> "${state.liked.trackCount} 首"
                            state.loading -> "同步中"
                            else -> "网易云同步"
                        },
                        // 已登录就一定能点开：详情页会按 uid 重新解析 liked 歌单，
                        // 不再因为首页摘要还没到就跳去登录页或毫无反应。
                        onClick = {
                            if (!session.isLoggedIn) {
                                onLogin()
                            } else {
                                onOpenTarget(
                                    PlaylistTarget(
                                        title = "我喜欢的音乐",
                                        coverUrl = state.liked?.coverUrl,
                                        subtitle = state.liked?.let {
                                            "${it.trackCount} 首 · 网易云同步"
                                        } ?: "网易云同步",
                                        playlistId = state.liked?.id,
                                        isLiked = true,
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Entrance(index = 3) {
            SectionHeader(
                title = "最近播放",
                subtitle = when {
                    history.isEmpty() -> "播放过的歌会自动记录"
                    else -> "接着上次的听下去"
                },
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
                    description = "播放任意一首歌后，最近听过的会留在这里。",
                )
            } else {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        // 只预览最近几首，完整列表走独立的惰性加载页面
                        history.take(PREVIEW_COUNT).forEachIndexed { index, song ->
                            SongRow(
                                song = song,
                                isCurrent = song.id == nowPlayingSongId,
                                onClick = { onPlayList(history, index) },
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

        // 游客态没有个人歌单，整块隐藏，登录入口统一在「我的」页。
        if (session.isLoggedIn) {
            Entrance(index = 5) {
                SectionHeader(
                    title = "我的歌单",
                    subtitle = when {
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
                if (state.playlists.isNotEmpty()) {
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
                    LoadingPanel(if (state.loading) "正在同步你的歌单…" else "还没有创建歌单")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

private const val PREVIEW_COUNT = 5

/** 日期 + 大字问候 + 右侧刷新。 */
@Composable
private fun DiscoverHeader(
    session: NetEaseSession,
    greeting: String,
    dateLabel: String,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                color = primary.copy(alpha = 0.78f),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (session.isLoggedIn && !session.avatarUrl.isNullOrBlank()) {
                    ArtworkImage(
                        url = session.avatarUrl,
                        modifier = Modifier.size(38.dp),
                        shape = CircleShape,
                        maxEdgePx = THUMB_EDGE_PX,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 32.sp,
                        letterSpacing = (-0.6).sp,
                    ),
                    fontWeight = FontWeight.SemiBold,
                )
                // 已登录：问候语右侧补一行小号账号 ID，方便确认当前登录的是哪个账号
                if (session.isLoggedIn && session.userId > 0L) {
                    Text(
                        text = "ID ${session.userId}",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.4.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        modifier = Modifier.padding(start = 8.dp, top = 14.dp),
                    )
                }
            }
            Text(
                text = if (session.isLoggedIn) {
                    "${session.nickname ?: "音乐爱好者"} · 今天想听点什么"
                } else {
                    "游客模式 · 登录入口在「我的」"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        GlassPill(onClick = onRefresh) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 1.8.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "刷新",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 今日推荐海报：整幅封面 + 底部渐变压暗 + 一次性扫光。
 *
 * 滚动视差只在 graphicsLayer 的 lambda 里读取 [scrollState]，不参与重组；
 * 扫光动画同样只在绘制阶段读取进度，跑完一次就停止。
 */
@Composable
private fun DailyHero(
    state: HomeUiState,
    scrollState: ScrollState,
    primary: Color,
    onOpen: () -> Unit,
    onPlayAll: () -> Unit,
) {
    val song = state.daily.firstOrNull()
    val hasSong = song != null
    val shape = RoundedCornerShape(30.dp)
    val sweep = remember { Animatable(0f) }
    val settle = remember { Animatable(0f) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.986f else 1f,
        animationSpec = tween(durationMillis = 140, easing = LinearOutSlowInEasing),
        label = "heroPressScale",
    )

    // 首曲到达后再扫一次光：冷启动时先渲染占位，封面到位才有「揭幕」感
    LaunchedEffect(song?.coverUrl) {
        if (song?.coverUrl == null) return@LaunchedEffect
        sweep.snapTo(0f)
        delay(180)
        sweep.animateTo(1f, animationSpec = tween(1100, easing = LinearOutSlowInEasing))
    }

    LaunchedEffect(Unit) {
        settle.animateTo(1f, animationSpec = tween(720, easing = LinearOutSlowInEasing))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(376.dp)
            .graphicsLayer {
                // 滚动时极轻微收束，制造「头图退场」的层次；全部读取都在绘制阶段完成
                val scrollProgress = (scrollState.value / 600f).coerceIn(0f, 1f)
                val s = pressScale * (1f + 0.014f * (1f - settle.value)) *
                    (1f - 0.018f * scrollProgress)
                scaleX = s
                scaleY = s
            }
            .shadow(
                elevation = 14.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.22f),
                spotColor = Color.Black.copy(alpha = 0.30f),
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        primary.copy(alpha = 0.58f),
                        primary.copy(alpha = 0.22f),
                        MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ),
            )
            .border(0.7.dp, Color.White.copy(alpha = 0.16f), shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = hasSong,
                onClick = onOpen,
            ),
    ) {
        ArtworkImage(
            url = song?.coverUrl,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 封面比容器略大：位移上限取自身高度的 3%，始终落在放大余量内，不露底
                    val scale = 1.08f
                    scaleX = scale
                    scaleY = scale
                    translationY = (scrollState.value * 0.06f)
                        .coerceIn(0f, size.height * 0.03f)
                },
            shape = shape,
            maxEdgePx = 1080,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.42f to Color.Black.copy(alpha = 0.10f),
                            0.72f to Color.Black.copy(alpha = 0.56f),
                            1f to Color.Black.copy(alpha = 0.88f),
                        ),
                    ),
                ),
        )

        // 一次性扫光：沿对角线掠过封面，只在上场时出现一次
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    val progress = sweep.value
                    if (progress < 1f) {
                        val width = size.width
                        val startX = width * (progress * 1.8f - 1.0f)
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.16f),
                                    Color.Transparent,
                                ),
                                start = Offset(startX, 0f),
                                end = Offset(startX + width * 0.5f, size.height * 0.62f),
                            ),
                        )
                    }
                },
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroTag("今日推荐")
                Spacer(modifier = Modifier.weight(1f))
                if (state.daily.isNotEmpty()) {
                    HeroTag("共 ${state.daily.size} 首")
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "今天的第一首歌",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    color = Color.White.copy(alpha = 0.72f),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = song?.name ?: if (state.error != null) {
                        "今日推荐暂时没连上"
                    } else {
                        "正在准备今日推荐…"
                    },
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 26.sp,
                        letterSpacing = (-0.4).sp,
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = song?.artists?.joinToString(" / ")
                        ?: if (state.error != null) "稍后刷新再试" else "每天 6:00 更新",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.74f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(18.dp))
                if (hasSong) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable(onClick = onPlayAll)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = MusicIcons.Play,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = "播放全部",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroTag(text: String) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.26f))
            .border(0.8.dp, Color.White.copy(alpha = 0.20f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.88f),
        )
    }
}

/** 三宫格入口卡：圆形图标 + 标题 + 一行说明，按压轻微回缩。 */
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 130, easing = LinearOutSlowInEasing),
        label = "featureTileScale",
    )

    // 三宫格已收进同一块玻璃面板，这里不再画玻璃，只画内容并做按压缩放
    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(26.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
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

/** 三宫格之间的细分隔线。 */
@Composable
private fun TileDivider() {
    Box(
        modifier = Modifier
            .height(40.dp)
            .width(0.7.dp)
            .background(
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f),
            ),
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
