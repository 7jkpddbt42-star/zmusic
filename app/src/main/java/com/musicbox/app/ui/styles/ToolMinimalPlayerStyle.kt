package com.musicbox.app.ui.styles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicbox.app.data.BackgroundSpec
import com.musicbox.app.player.PositionSource
import com.musicbox.app.ui.components.player.MediaHeader
import com.musicbox.app.ui.components.player.PlayerBackdrop
import com.musicbox.app.ui.components.player.ProgressSection
import com.musicbox.app.ui.components.player.ProgressTimePlacement
import com.musicbox.app.ui.components.player.SecondaryActions
import com.musicbox.app.ui.components.player.TransportControls
import com.musicbox.app.ui.components.player.formatProgressTime
import com.musicbox.app.ui.player.PlaybackUiState
import com.musicbox.app.ui.theme.ArtworkImage

private val ToolFg = Color(0xFFE4EBF2)
private val ToolMuted = Color(0xFF8FA3B5)
private val ToolAccent = Color(0xFF7FA9C9)

/**
 * TOOL_MINIMAL：工具极简。
 *
 * 低饱和灰蓝配色、高密度信息：小方封面与标题并排，下面是「时长 / 队列 / 模式」
 * 三格统计，无边框、无光晕、无卡片描边；控制键为弱强调的小尺寸布局。
 */
@Composable
fun ToolMinimalPlayerStyle(
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
        scrim = 0.62f,
        particles = false,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0C1116).copy(alpha = 0.42f)),
        )

        Column(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            PlayerTopBar(
                foreground = ToolFg,
                onCollapse = actions.onCollapse,
                onOpenQueue = actions.onOpenQueue,
                title = "播放器",
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkImage(
                    url = state.artworkUrl,
                    shape = RoundedCornerShape(12.dp),
                    onBitmap = actions.onArtworkLoaded,
                    modifier = Modifier.size(92.dp),
                    maxEdgePx = 320,
                )
                Spacer(modifier = Modifier.size(14.dp))
                MediaHeader(
                    title = state.title.orEmpty(),
                    subtitle = state.artist.orEmpty(),
                    foreground = ToolFg,
                    secondaryForeground = ToolMuted,
                    isTrial = state.isTrial,
                    titleSize = 18.sp,
                    subtitleSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (state.queue.isEmpty()) {
                        "—"
                    } else {
                        "%02d/%02d".format(state.currentIndex + 1, state.queue.size)
                    },
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    color = ToolMuted,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatCell(
                    label = "时长",
                    value = if (state.durationMs > 0L) formatProgressTime(state.durationMs) else "--:--",
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    label = "队列",
                    value = if (state.queue.isEmpty()) "—" else "${state.queue.size} 首",
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    label = "模式",
                    value = modeLabel,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ToolFg.copy(alpha = 0.08f)),
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "进度",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = ToolMuted,
            )
            Spacer(modifier = Modifier.height(2.dp))
            ProgressSection(
                source = source,
                durationMs = state.durationMs,
                positionMs = state.positionMs,
                onSeek = actions.onSeek,
                foreground = ToolAccent,
                trackColor = ToolFg.copy(alpha = 0.16f),
                trackHeight = 2.dp,
                thumbRadius = 4.dp,
                timePlacement = ProgressTimePlacement.Below,
                timeStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                timeAlpha = 0.58f,
            )

            Spacer(modifier = Modifier.height(12.dp))

            TransportControls(
                isPlaying = state.isPlaying,
                onTogglePlay = actions.onTogglePlay,
                onPrevious = actions.onPrevious,
                onNext = actions.onNext,
                foreground = ToolFg,
                playBackground = ToolAccent,
                playForeground = Color(0xFF0C1116),
                playShape = RoundedCornerShape(16.dp),
                playSize = 58.dp,
                playIconSize = 26.dp,
                skipButtonSize = 44.dp,
                skipIconSize = 26.dp,
                spacing = 18.dp,
                leading = {
                    LikeButton(
                        isLiked = isLiked,
                        onToggleLike = actions.onToggleLike,
                        size = 36.dp,
                        iconSize = 18.dp,
                        idleColor = ToolMuted,
                    )
                },
                trailing = {
                    ModeText(
                        modeLabel = modeLabel,
                        onClick = actions.onCycleMode,
                        foreground = ToolMuted,
                    )
                },
            )

            Spacer(modifier = Modifier.height(6.dp))

            SecondaryActions(
                onOpenLyric = actions.onOpenLyric,
                onOpenQueue = actions.onOpenQueue,
                onOpenStyleSwitcher = actions.onOpenStyleSwitcher,
                onNotify = actions.onNotify,
                foreground = ToolMuted,
                iconSize = 17.dp,
                itemSize = 44.dp,
                spacing = 10.dp,
                showLabels = true,
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ToolFg.copy(alpha = 0.04f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = ToolMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            fontWeight = FontWeight.Medium,
            color = ToolFg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
