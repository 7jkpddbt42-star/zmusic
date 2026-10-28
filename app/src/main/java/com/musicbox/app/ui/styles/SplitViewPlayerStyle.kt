package com.musicbox.app.ui.styles

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicbox.app.data.BackgroundSpec
import com.musicbox.app.player.PositionSource
import com.musicbox.app.ui.components.MusicIcons
import com.musicbox.app.ui.components.player.MediaHeader
import com.musicbox.app.ui.components.player.PlayerArtwork
import com.musicbox.app.ui.components.player.PlayerBackdrop
import com.musicbox.app.ui.components.player.ProgressSection
import com.musicbox.app.ui.components.player.ProgressTimePlacement
import com.musicbox.app.ui.components.player.SecondaryActions
import com.musicbox.app.ui.components.player.TransportControls
import com.musicbox.app.ui.player.PlaybackUiState
import com.musicbox.app.ui.player.QueueItem

/**
 * SPLIT_VIEW：双栏队列。
 *
 * 上半屏是紧凑的播放控制（封面与标题并排、进度独立成行），
 * 下半屏是常驻的播放队列面板；上滑面板展开、下滑收起，也可点标题切换。
 * 队列项点击直接切歌，不打开额外弹层。
 */
@Composable
fun SplitViewPlayerStyle(
    state: PlaybackUiState,
    source: PositionSource,
    background: BackgroundSpec,
    modeLabel: String,
    isLiked: Boolean,
    lyricLine: String,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    PlayerBackdrop(
        background = background,
        artworkUrl = state.artworkUrl,
        source = source,
        isPlaying = state.isPlaying,
        scrim = 0.50f,
    ) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            // 展开高度按屏幕实际高度收敛：保证上方播放控制区至少留够 360dp，
            // 小屏展开队列时不会把进度条或控制键挤出屏幕。
            val collapsedHeight = maxHeight * 0.34f
            val expandedHeight =
                (maxHeight - 360.dp).coerceIn(collapsedHeight, maxHeight * 0.56f)
            val queueHeight by animateDpAsState(
                targetValue = if (expanded) expandedHeight else collapsedHeight,
                animationSpec = tween(durationMillis = 320),
                label = "splitQueueHeight",
            )

            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp),
                ) {
                    PlayerTopBar(
                        foreground = Color.White,
                        onCollapse = actions.onCollapse,
                        onOpenQueue = actions.onOpenQueue,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerArtwork(
                            artworkUrl = state.artworkUrl,
                            onArtworkLoaded = actions.onArtworkLoaded,
                            modifier = Modifier.fillMaxWidth(0.36f).aspectRatio(1f),
                            shape = RoundedCornerShape(16.dp),
                            showHalo = false,
                            elevation = 14.dp,
                            onClick = actions.onOpenLyric,
                        )
                        Spacer(modifier = Modifier.size(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            MediaHeader(
                                title = state.title.orEmpty(),
                                subtitle = state.artist.orEmpty(),
                                foreground = Color.White,
                                isTrial = state.isTrial,
                                titleSize = 20.sp,
                                subtitleSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                                horizontalAlignment = Alignment.Start,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ModeText(
                                    modeLabel = modeLabel,
                                    onClick = actions.onCycleMode,
                                    foreground = Color.White.copy(alpha = 0.66f),
                                )
                                Spacer(modifier = Modifier.size(10.dp))
                                LikeButton(
                                    isLiked = isLiked,
                                    onToggleLike = actions.onToggleLike,
                                    size = 34.dp,
                                    iconSize = 18.dp,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    ProgressSection(
                        source = source,
                        durationMs = state.durationMs,
                        positionMs = state.positionMs,
                        onSeek = actions.onSeek,
                        foreground = Color.White,
                        trackHeight = 2.5.dp,
                        thumbRadius = 5.dp,
                        timePlacement = ProgressTimePlacement.Above,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    TransportControls(
                        isPlaying = state.isPlaying,
                        onTogglePlay = actions.onTogglePlay,
                        onPrevious = actions.onPrevious,
                        onNext = actions.onNext,
                        foreground = Color.White,
                        playBackground = Color.White,
                        playForeground = Color.Black,
                        playSize = 62.dp,
                        playIconSize = 28.dp,
                        skipButtonSize = 48.dp,
                        skipIconSize = 28.dp,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        SecondaryActions(
                            onOpenLyric = actions.onOpenLyric,
                            onOpenQueue = actions.onOpenQueue,
                            onOpenStyleSwitcher = actions.onOpenStyleSwitcher,
                            onNotify = actions.onNotify,
                            foreground = Color.White.copy(alpha = 0.80f),
                            iconSize = 17.dp,
                            itemSize = 36.dp,
                            spacing = 12.dp,
                            showSpeed = false,
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                QueuePanel(
                    queue = state.queue,
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    onSelect = actions.onSelectQueueItem,
                    modifier = Modifier.fillMaxWidth().height(queueHeight),
                )
            }
        }
    }
}

@Composable
private fun QueuePanel(
    queue: List<QueueItem>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    Column(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.09f))
            // 面板贴着屏幕底部：列表内容避让系统导航栏，最后一项不会被盖住
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount < -14f) onExpandedChange(true)
                        if (dragAmount > 14f) onExpandedChange(false)
                    }
                }
                .clickable { onExpandedChange(!expanded) }
                .padding(top = 8.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.34f)),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "播放队列",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${queue.size} 首",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.56f),
                )
                Icon(
                    imageVector = MusicIcons.ChevronDown,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.66f),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(16.dp)
                        .graphicsLayer { rotationZ = if (expanded) 180f else 0f },
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 8.dp),
        ) {
            items(items = queue, key = { it.index }) { item ->
                QueueRow(item = item, onClick = { onSelect(item.index) })
            }
        }
    }
}

@Composable
private fun QueueRow(item: QueueItem, onClick: () -> Unit) {
    val current = item.isCurrent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (current) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "%02d".format(item.index + 1),
            style = MaterialTheme.typography.labelSmall,
            color = if (current) Color.White else Color.White.copy(alpha = 0.42f),
            modifier = Modifier.size(width = 30.dp, height = 18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                color = if (current) Color.White else Color.White.copy(alpha = 0.84f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.artist,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.46f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (current) {
            Text(
                text = "播放中",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
