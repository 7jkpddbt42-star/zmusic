package com.musicbox.app.ui.styles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
 * LIQUID_GLASS：液态玻璃。
 *
 * 三段式布局：顶部悬浮玻璃胶囊、中部垂直居中的浮空封面（宽度与高度双重收敛，
 * 避免在长屏上占满半屏）、底部一整块悬浮 GlassSurface 承载进度、控制与次级动作。
 * 播放键容器同样用 GlassSurface 的 lens，并通过 exportedBackdrop 处理玻璃叠玻璃。
 */
@Composable
fun LiquidGlassPlayerStyle(
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
    // 底部控制条玻璃的导出层：播放键采样它实现玻璃叠玻璃。
    // 必须是独立实例，复用 backdrop 会让图层自我录制导致原生渲染线程崩溃。
    val controlBarGlass = rememberPlayerGlassBackdrop()

    PlayerBackdrop(
        background = background,
        artworkUrl = state.artworkUrl,
        source = source,
        isPlaying = state.isPlaying,
        scrim = 0.30f,
        glassBackdrop = backdrop,
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            // 封面下方压暗：保证标题与玻璃条在亮封面上依然清晰
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.34f to Color.Transparent,
                            0.58f to Color.Black.copy(alpha = 0.55f),
                            1f to Color.Black.copy(alpha = 0.88f),
                        ),
                    ),
            )

            Column(modifier = Modifier.fillMaxSize()) {
                GlassSurface(
                    backdrop = backdrop,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    tint = Color.White.copy(alpha = 0.10f),
                ) {
                    PlayerTopBar(
                        foreground = Color.White,
                        onCollapse = actions.onCollapse,
                        onOpenQueue = actions.onOpenQueue,
                    )
                }

                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 扣掉标题、歌词胶囊与间距后按剩余高度收敛，小屏也不会溢出
                        val coverSize = (maxWidth * 0.72f)
                            .coerceAtMost(maxHeight - 150.dp)
                            .coerceAtMost(320.dp)

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PlayerArtwork(
                                artworkUrl = state.artworkUrl,
                                onArtworkLoaded = actions.onArtworkLoaded,
                                modifier = Modifier.size(coverSize),
                                shape = RoundedCornerShape(32.dp),
                                haloColor = MaterialTheme.colorScheme.primary,
                                elevation = 20.dp,
                                onClick = actions.onOpenLyric,
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            MediaHeader(
                                title = state.title.orEmpty(),
                                subtitle = state.artist.orEmpty(),
                                foreground = Color.White,
                                isTrial = state.isTrial,
                                titleSize = 21.sp,
                                subtitleSize = 14.sp,
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            LyricPreviewPill(
                                lyricLine = lyricLine,
                                onClick = actions.onOpenLyric,
                                foreground = Color.White,
                            )
                        }
                    }
                }

                GlassSurface(
                    backdrop = backdrop,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    tint = Color.White.copy(alpha = 0.12f),
                    exportedBackdrop = controlBarGlass,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ProgressSection(
                            source = source,
                            durationMs = state.durationMs,
                            positionMs = state.positionMs,
                            onSeek = actions.onSeek,
                            foreground = Color.White,
                            trackHeight = 3.dp,
                            thumbRadius = 5.dp,
                            timePlacement = ProgressTimePlacement.Above,
                            timeAlpha = 0.70f,
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        TransportControls(
                            isPlaying = state.isPlaying,
                            onTogglePlay = actions.onTogglePlay,
                            onPrevious = actions.onPrevious,
                            onNext = actions.onNext,
                            foreground = Color.White,
                            playBackground = Color.Transparent,
                            playForeground = Color.White,
                            playSize = 64.dp,
                            playIconSize = 30.dp,
                            skipButtonSize = 46.dp,
                            skipIconSize = 28.dp,
                            spacing = 18.dp,
                            leading = {
                                ModeText(
                                    modeLabel = modeLabel,
                                    onClick = actions.onCycleMode,
                                    foreground = Color.White.copy(alpha = 0.78f),
                                )
                            },
                            trailing = {
                                LikeButton(
                                    isLiked = isLiked,
                                    onToggleLike = actions.onToggleLike,
                                    size = 38.dp,
                                    iconSize = 20.dp,
                                )
                            },
                            playContainer = { content ->
                                // 采样父玻璃的导出层：玻璃叠玻璃，不重新采样整页背景
                                GlassSurface(
                                    backdrop = controlBarGlass,
                                    modifier = Modifier.fillMaxSize(),
                                    shape = CircleShape,
                                    tint = Color.White.copy(alpha = 0.18f),
                                    blurRadius = 4.dp,
                                    shadow = false,
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        content()
                                    }
                                }
                            },
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        SecondaryActions(
                            onOpenLyric = actions.onOpenLyric,
                            onOpenQueue = actions.onOpenQueue,
                            onOpenStyleSwitcher = actions.onOpenStyleSwitcher,
                            onNotify = actions.onNotify,
                            foreground = Color.White.copy(alpha = 0.84f),
                            iconSize = 18.dp,
                            itemSize = 38.dp,
                            spacing = 10.dp,
                        )
                    }
                }
            }
        }
    }
}
