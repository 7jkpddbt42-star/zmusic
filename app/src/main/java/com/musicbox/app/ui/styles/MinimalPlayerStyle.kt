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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicbox.app.data.BackgroundSpec
import com.musicbox.app.player.PositionSource
import com.musicbox.app.ui.components.player.LyricPreviewPill
import com.musicbox.app.ui.components.player.MediaHeader
import com.musicbox.app.ui.components.player.PlayerArtwork
import com.musicbox.app.ui.components.player.PlayerBackdrop
import com.musicbox.app.ui.components.player.PlayerMoreMenu
import com.musicbox.app.ui.components.player.ProgressSection
import com.musicbox.app.ui.components.player.ProgressTimePlacement
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
                actions = {
                    PlayerMoreMenu(
                        foreground = Color.White,
                    )
                },
            )

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                PlayerArtwork(
                    artworkUrl = state.artworkUrl,
                    onArtworkLoaded = actions.onArtworkLoaded,
                    modifier = Modifier.fillMaxWidth(0.86f).aspectRatio(1f),
                    shape = RoundedCornerShape(28.dp),
                    showHalo = false,
                    elevation = 0.dp,
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
                playForeground = Color.White,
                playSize = 64.dp,
                playIconSize = 42.dp,
                skipButtonSize = 48.dp,
                skipIconSize = 28.dp,
                skipBackground = Color.Transparent,
                spacing = 24.dp,
                // 纯图标、无圆片、无阴影：与系统播放器一致的扁平控制条
                playContainer = { content -> content() },
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

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
