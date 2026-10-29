package com.musicbox.app.ui.styles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicbox.app.data.BackgroundSpec
import com.musicbox.app.player.PositionSource
import com.musicbox.app.ui.components.player.LyricPreviewPill
import com.musicbox.app.ui.components.player.MediaHeader
import com.musicbox.app.ui.components.player.PlayerArtwork
import com.musicbox.app.ui.components.player.PlayerBackdrop
import com.musicbox.app.ui.components.player.ProgressSection
import com.musicbox.app.ui.components.player.ProgressTimePlacement
import com.musicbox.app.ui.components.player.SecondaryActions
import com.musicbox.app.ui.components.player.TransportControls
import com.musicbox.app.ui.player.PlaybackUiState

/**
 * MINIMAL：极简沉浸。
 *
 * 大封面居中、纤细进度条、控制按钮干净居中；无玻璃、无附加装饰。
 * 这是默认风格，行为与改造前的播放页保持一致。
 */
@Composable
fun MinimalPlayerStyle(
    state: PlaybackUiState,
    source: PositionSource,
    background: BackgroundSpec,
    modeLabel: String,
    isLiked: Boolean,
    lyricLine: String,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
    lyricTranslation: String = "",
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
            horizontalAlignment = Alignment.CenterHorizontally,
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
                    modifier = Modifier.fillMaxWidth(0.86f).aspectRatio(1f),
                    shape = RoundedCornerShape(32.dp),
                    haloColor = MaterialTheme.colorScheme.primary,
                    elevation = 30.dp,
                    onClick = actions.onOpenLyric,
                )
            }

            MediaHeader(
                title = state.title.orEmpty(),
                subtitle = state.artist.orEmpty(),
                foreground = Color.White,
                secondaryForeground = Color.White.copy(alpha = 0.62f),
                isTrial = state.isTrial,
                titleSize = 22.sp,
                subtitleSize = 13.sp,
            )

            Spacer(modifier = Modifier.height(14.dp))

            LyricPreviewPill(
                lyricLine = lyricLine,
                onClick = actions.onOpenLyric,
                foreground = Color.White,
                translation = lyricTranslation,
            )

            Spacer(modifier = Modifier.height(22.dp))

            ProgressSection(
                source = source,
                durationMs = state.durationMs,
                positionMs = state.positionMs,
                onSeek = actions.onSeek,
                foreground = Color.White,
                trackHeight = 3.dp,
                thumbRadius = 5.dp,
                timeStyle = MaterialTheme.typography.labelMedium,
                timeAlpha = 0.56f,
                timePlacement = ProgressTimePlacement.Inline,
            )

            Spacer(modifier = Modifier.height(16.dp))

            TransportControls(
                isPlaying = state.isPlaying,
                onTogglePlay = actions.onTogglePlay,
                onPrevious = actions.onPrevious,
                onNext = actions.onNext,
                foreground = Color.White,
                playForeground = Color.Black,
                playSize = 74.dp,
                playIconSize = 34.dp,
                skipButtonSize = 52.dp,
                skipIconSize = 32.dp,
                skipBackground = Color.White.copy(alpha = 0.06f),
                // 播放键：不是一块死白，而是带高光的浅渐变圆片 + 柔和外发光，
                // 在深色背景上更像一枚实体按键
                playContainer = { content ->
                    Box(
                        modifier = Modifier
                            .size(74.dp)
                            .shadow(
                                elevation = 20.dp,
                                shape = CircleShape,
                                ambientColor = Color.White.copy(alpha = 0.22f),
                                spotColor = Color.White.copy(alpha = 0.26f),
                            )
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color.White, Color(0xFFE6E6EC)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        content()
                    }
                },
                leading = {
                    ModeText(
                        modeLabel = modeLabel,
                        onClick = actions.onCycleMode,
                        foreground = Color.White.copy(alpha = 0.72f),
                    )
                },
                trailing = {
                    LikeButton(
                        isLiked = isLiked,
                        onToggleLike = actions.onToggleLike,
                    )
                },
            )

            Spacer(modifier = Modifier.height(10.dp))

            SecondaryActions(
                onOpenLyric = actions.onOpenLyric,
                onOpenQueue = actions.onOpenQueue,
                onOpenStyleSwitcher = actions.onOpenStyleSwitcher,
                onNotify = actions.onNotify,
                foreground = Color.White.copy(alpha = 0.82f),
                iconSize = 19.dp,
                itemSize = 40.dp,
                spacing = 16.dp,
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
