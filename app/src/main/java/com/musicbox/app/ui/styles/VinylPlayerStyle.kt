package com.musicbox.app.ui.styles

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicbox.app.data.BackgroundSpec
import com.musicbox.app.player.PositionSource
import com.musicbox.app.ui.components.VinylRecord
import com.musicbox.app.ui.components.player.MediaHeader
import com.musicbox.app.ui.components.player.PlayerBackdrop
import com.musicbox.app.ui.components.player.ProgressSection
import com.musicbox.app.ui.components.player.ProgressTimePlacement
import com.musicbox.app.ui.components.player.SecondaryActions
import com.musicbox.app.ui.components.player.TransportControls
import com.musicbox.app.ui.player.PlaybackUiState

/**
 * VINYL：黑胶唱片。
 *
 * 旋转唱盘 + 唱臂是视觉主体，标题与进度被压缩到盘面下方，控制区只有一行小尺寸按钮。
 * 左右拖动唱片可切歌（累计位移超过阈值才触发，避免误触）。
 */
@Composable
fun VinylPlayerStyle(
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
        scrim = 0.54f,
        particles = false,
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
                Box(
                    modifier = Modifier.fillMaxWidth(0.9f).aspectRatio(1f),
                ) {
                    VinylRecord(
                        artworkUrl = state.artworkUrl,
                        isPlaying = state.isPlaying,
                        onBitmap = actions.onArtworkLoaded,
                        modifier = Modifier
                            .fillMaxSize(0.88f)
                            .align(Alignment.Center)
                            .clickable(onClick = actions.onOpenLyric)
                            .pointerInput(actions.onNext, actions.onPrevious) {
                                var total = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { total = 0f },
                                    onDragCancel = { total = 0f },
                                    onDragEnd = {
                                        val threshold = 64.dp.toPx()
                                        if (total < -threshold) actions.onNext()
                                        if (total > threshold) actions.onPrevious()
                                        total = 0f
                                    },
                                ) { _, dragAmount ->
                                    total += dragAmount
                                }
                            },
                    )

                    Tonearm(
                        isPlaying = state.isPlaying,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 2.dp)
                            .size(width = 96.dp, height = 148.dp),
                    )
                }
            }

            Text(
                text = "黑胶唱片 · 左右滑动切歌",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = Color.White.copy(alpha = 0.46f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(10.dp))

            MediaHeader(
                title = state.title.orEmpty(),
                subtitle = state.artist.orEmpty(),
                foreground = Color.White,
                isTrial = state.isTrial,
                titleSize = 19.sp,
                subtitleSize = 13.sp,
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProgressSection(
                source = source,
                durationMs = state.durationMs,
                positionMs = state.positionMs,
                onSeek = actions.onSeek,
                foreground = Color.White,
                trackHeight = 2.dp,
                thumbRadius = 3.dp,
                timePlacement = ProgressTimePlacement.Below,
            )

            Spacer(modifier = Modifier.height(8.dp))

            TransportControls(
                isPlaying = state.isPlaying,
                onTogglePlay = actions.onTogglePlay,
                onPrevious = actions.onPrevious,
                onNext = actions.onNext,
                foreground = Color.White,
                playBackground = Color.White.copy(alpha = 0.92f),
                playForeground = Color.Black,
                playSize = 68.dp,
                playIconSize = 31.dp,
                skipButtonSize = 50.dp,
                skipIconSize = 28.dp,
                skipBackground = Color.White.copy(alpha = 0.07f),
                spacing = 18.dp,
                // 播放键用竖向白色渐变 + 细描边替代纯色块，尺寸放大后更有质感
                playContainer = { content ->
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.White, Color.White.copy(alpha = 0.82f)),
                                ),
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.24f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { content() }
                },
                leading = {
                    LikeButton(
                        isLiked = isLiked,
                        onToggleLike = actions.onToggleLike,
                        size = 40.dp,
                        iconSize = 20.dp,
                    )
                },
                trailing = {
                    ModeText(
                        modeLabel = modeLabel,
                        onClick = actions.onCycleMode,
                        foreground = Color.White.copy(alpha = 0.70f),
                    )
                },
            )

            Spacer(modifier = Modifier.height(4.dp))

            SecondaryActions(
                onOpenLyric = actions.onOpenLyric,
                onOpenQueue = actions.onOpenQueue,
                onOpenStyleSwitcher = actions.onOpenStyleSwitcher,
                onNotify = actions.onNotify,
                foreground = Color.White.copy(alpha = 0.78f),
                iconSize = 17.dp,
                itemSize = 36.dp,
                spacing = 14.dp,
                showSpeed = false,
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * 唱臂：播放时向内压到唱片上（角度更小），暂停时抬起。
 * 旋转只在 graphicsLayer 阶段执行，不触发重组。
 */
@Composable
private fun Tonearm(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val angle by animateFloatAsState(
        targetValue = if (isPlaying) 4f else 26f,
        animationSpec = tween(durationMillis = 720),
        label = "tonearm",
    )

    Canvas(
        modifier = modifier.graphicsLayer {
            rotationZ = angle
            transformOrigin = TransformOrigin(0.84f, 0.12f)
        },
    ) {
        val base = Offset(size.width * 0.84f, size.height * 0.12f)
        val tip = Offset(base.x - size.width * 0.16f, size.height * 0.94f)

        drawCircle(
            color = Color.White.copy(alpha = 0.30f),
            radius = 10.dp.toPx(),
            center = base,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.72f),
            radius = 4.dp.toPx(),
            center = base,
        )
        drawLine(
            color = Color.White.copy(alpha = 0.58f),
            start = base,
            end = tip,
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White.copy(alpha = 0.72f),
            start = Offset(tip.x + 2.dp.toPx(), tip.y - 16.dp.toPx()),
            end = tip,
            strokeWidth = 7.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
