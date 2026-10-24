package com.musicbox.app.ui.styles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
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
import com.musicbox.app.ui.player.PlaybackUiState

/**
 * STRUCTURED：结构三栏。
 *
 * 封面缩小偏上、标题左对齐；进度条独立成行（时间在条上方）；
 * 控制区是一组严格对称的三栏：左栏「喜欢 + 上一首」竖排，中栏固定大播放键，
 * 右栏「模式 + 下一首」竖排。与 MINIMAL 的单行控制结构明显不同。
 */
@Composable
fun StructuredPlayerStyle(
    state: PlaybackUiState,
    source: PositionSource,
    background: BackgroundSpec,
    modeLabel: String,
    isLiked: Boolean,
    lyricLine: String,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
) {
    PlayerBackdrop(
        background = background,
        artworkUrl = state.artworkUrl,
        source = source,
        isPlaying = state.isPlaying,
        scrim = 0.46f,
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            PlayerTopBar(
                foreground = Color.White,
                onCollapse = actions.onCollapse,
                onOpenQueue = actions.onOpenQueue,
            )

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                PlayerArtwork(
                    artworkUrl = state.artworkUrl,
                    onArtworkLoaded = actions.onArtworkLoaded,
                    modifier = Modifier.fillMaxWidth(0.68f).aspectRatio(1f),
                    shape = RoundedCornerShape(18.dp),
                    showHalo = false,
                    elevation = 16.dp,
                    onClick = actions.onOpenLyric,
                )
            }

            MediaHeader(
                title = state.title.orEmpty(),
                subtitle = state.artist.orEmpty(),
                foreground = Color.White,
                isTrial = state.isTrial,
                titleSize = 22.sp,
                subtitleSize = 13.sp,
                textAlign = TextAlign.Start,
                horizontalAlignment = Alignment.Start,
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 进度独立成行：时间在轨道上方两端
            ProgressSection(
                source = source,
                durationMs = state.durationMs,
                positionMs = state.positionMs,
                onSeek = actions.onSeek,
                foreground = Color.White,
                trackHeight = 2.dp,
                showThumb = false,
                timePlacement = ProgressTimePlacement.Above,
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LikeButton(
                        isLiked = isLiked,
                        onToggleLike = actions.onToggleLike,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SkipCircle(
                        icon = MusicIcons.SkipPrevious,
                        description = "上一首",
                        onClick = actions.onPrevious,
                    )
                }

                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.White)
                        .clickable(onClick = actions.onTogglePlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) MusicIcons.Pause else MusicIcons.Play,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        tint = Color.Black,
                        modifier = Modifier.size(38.dp),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ModeText(
                        modeLabel = modeLabel,
                        onClick = actions.onCycleMode,
                        foreground = Color.White.copy(alpha = 0.76f),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SkipCircle(
                        icon = MusicIcons.SkipNext,
                        description = "下一首",
                        onClick = actions.onNext,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SecondaryActions(
                    onOpenLyric = actions.onOpenLyric,
                    onOpenQueue = actions.onOpenQueue,
                    onOpenStyleSwitcher = actions.onOpenStyleSwitcher,
                    onNotify = actions.onNotify,
                    foreground = Color.White.copy(alpha = 0.86f),
                    iconSize = 19.dp,
                    itemSize = 40.dp,
                    spacing = 14.dp,
                )
            }

            if (state.isBuffering) {
                Text4Buffer()
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SkipCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    iconSize: androidx.compose.ui.unit.Dp = 28.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun Text4Buffer() {
    androidx.compose.material3.Text(
        text = "缓冲中…",
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.60f),
    )
}
