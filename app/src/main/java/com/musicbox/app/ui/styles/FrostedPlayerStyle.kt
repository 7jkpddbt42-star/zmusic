package com.musicbox.app.ui.styles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicbox.app.data.BackgroundSpec
import com.musicbox.app.player.PositionSource
import com.musicbox.app.ui.components.player.GlassSurface
import com.musicbox.app.ui.components.player.LyricPreviewPill
import com.musicbox.app.ui.components.player.MediaHeader
import com.musicbox.app.ui.components.player.PlayerArtwork
import com.musicbox.app.ui.components.player.PlayerBackdrop
import com.musicbox.app.ui.components.player.ProgressSection
import com.musicbox.app.ui.components.player.ProgressTimePlacement
import com.musicbox.app.ui.components.player.SecondaryActions
import com.musicbox.app.ui.components.player.TransportControls
import com.musicbox.app.ui.components.player.rememberPlayerGlassBackdrop
import com.musicbox.app.ui.player.PlaybackUiState

/**
 * FROSTED：毛玻璃氛围。
 *
 * 整页背景是重度模糊的封面（[PlayerBackdrop] 的 coverBlur），
 * 所有内容收进一块居中的大毛玻璃卡片：封面、标题、歌词胶囊、进度、
 * 控制键、次级动作依次纵排。与 LIQUID_GLASS 的「贴顶封面 + 底部悬浮条」结构完全不同。
 */
@Composable
fun FrostedPlayerStyle(
    state: PlaybackUiState,
    source: PositionSource,
    background: BackgroundSpec,
    modeLabel: String,
    isLiked: Boolean,
    lyricLine: String,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
) {
    val backdrop = rememberPlayerGlassBackdrop()

    PlayerBackdrop(
        background = background,
        artworkUrl = state.artworkUrl,
        source = source,
        isPlaying = state.isPlaying,
        scrim = 0.52f,
        coverBlur = true,
        glassBackdrop = backdrop,
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
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
                GlassSurface(
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth(0.96f),
                    shape = RoundedCornerShape(36.dp),
                    tint = Color.White.copy(alpha = 0.14f),
                    blurRadius = 8.dp,
                    refractionHeight = 18.dp,
                    refractionAmount = 26.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PlayerArtwork(
                            artworkUrl = state.artworkUrl,
                            onArtworkLoaded = actions.onArtworkLoaded,
                            modifier = Modifier.fillMaxWidth(0.62f).aspectRatio(1f),
                            shape = RoundedCornerShape(24.dp),
                            elevation = 14.dp,
                            onClick = actions.onOpenLyric,
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        MediaHeader(
                            title = state.title.orEmpty(),
                            subtitle = state.artist.orEmpty(),
                            foreground = Color.White,
                            isTrial = state.isTrial,
                            titleSize = 20.sp,
                            subtitleSize = 13.sp,
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        LyricPreviewPill(
                            lyricLine = lyricLine,
                            onClick = actions.onOpenLyric,
                            foreground = Color.White,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        ProgressSection(
                            source = source,
                            durationMs = state.durationMs,
                            positionMs = state.positionMs,
                            onSeek = actions.onSeek,
                            foreground = Color.White,
                            trackColor = Color.White.copy(alpha = 0.24f),
                            trackHeight = 3.dp,
                            thumbRadius = 5.dp,
                            timePlacement = ProgressTimePlacement.Above,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        TransportControls(
                            isPlaying = state.isPlaying,
                            onTogglePlay = actions.onTogglePlay,
                            onPrevious = actions.onPrevious,
                            onNext = actions.onNext,
                            foreground = Color.White,
                            playBackground = Color.White.copy(alpha = 0.94f),
                            playForeground = Color.Black,
                            playSize = 64.dp,
                            playIconSize = 30.dp,
                            skipButtonSize = 48.dp,
                            skipIconSize = 28.dp,
                            spacing = 16.dp,
                            leading = {
                                LikeButton(
                                    isLiked = isLiked,
                                    onToggleLike = actions.onToggleLike,
                                    size = 38.dp,
                                    iconSize = 20.dp,
                                )
                            },
                            trailing = {
                                ModeText(
                                    modeLabel = modeLabel,
                                    onClick = actions.onCycleMode,
                                    foreground = Color.White.copy(alpha = 0.74f),
                                )
                            },
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        SecondaryActions(
                            onOpenLyric = actions.onOpenLyric,
                            onOpenQueue = actions.onOpenQueue,
                            onOpenStyleSwitcher = actions.onOpenStyleSwitcher,
                            onNotify = actions.onNotify,
                            foreground = Color.White.copy(alpha = 0.84f),
                            iconSize = 18.dp,
                            itemSize = 38.dp,
                            spacing = 12.dp,
                            showSpeed = false,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
