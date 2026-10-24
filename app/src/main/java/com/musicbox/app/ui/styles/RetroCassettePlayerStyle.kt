package com.musicbox.app.ui.styles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicbox.app.data.BackgroundSpec
import com.musicbox.app.player.PositionSource
import com.musicbox.app.ui.components.MusicIcons
import com.musicbox.app.ui.components.player.PlayerBackdrop
import com.musicbox.app.ui.components.player.ProgressSection
import com.musicbox.app.ui.components.player.ProgressTimePlacement
import com.musicbox.app.ui.components.player.SecondaryActions
import com.musicbox.app.ui.components.player.TransportControls
import com.musicbox.app.ui.player.PlaybackUiState
import com.musicbox.app.ui.theme.rememberArtworkBitmap
import kotlin.math.cos
import kotlin.math.sin

private val CassetteCream = Color(0xFFF3E3C8)
private val CassetteInk = Color(0xFF3B2A1E)
private val CassetteAccent = Color(0xFFE07845)

/**
 * RETRO_CASSETTE：复古卡带。
 *
 * 主体是一盘拟物卡带：牛皮带身、标签纸内嵌小封面、下半部磁带窗口里两个卷轴
 * 在播放时逐帧转动（暂停即停住）。配色为暖棕 + 奶白 + 橘红，
 * 控制键做成小方块，进度条带「A 面」计数器。
 */
@Composable
fun RetroCassettePlayerStyle(
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
        scrim = 0.58f,
        particles = false,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF2A1B12).copy(alpha = 0.30f)),
        )

        Column(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlayerTopBar(
                foreground = CassetteCream,
                onCollapse = actions.onCollapse,
                onOpenQueue = actions.onOpenQueue,
            )

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CassetteCard(
                    artworkUrl = state.artworkUrl,
                    title = state.title.orEmpty(),
                    artist = state.artist.orEmpty(),
                    isPlaying = state.isPlaying,
                    isTrial = state.isTrial,
                    onArtworkLoaded = actions.onArtworkLoaded,
                    modifier = Modifier.fillMaxWidth(0.94f).aspectRatio(1.5f),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            ProgressSection(
                source = source,
                durationMs = state.durationMs,
                positionMs = state.positionMs,
                onSeek = actions.onSeek,
                foreground = CassetteAccent,
                trackColor = CassetteCream.copy(alpha = 0.22f),
                trackHeight = 3.dp,
                thumbRadius = 4.dp,
                timePlacement = ProgressTimePlacement.Above,
                timeAlpha = 0.62f,
            )

            Spacer(modifier = Modifier.height(10.dp))

            TransportControls(
                isPlaying = state.isPlaying,
                onTogglePlay = actions.onTogglePlay,
                onPrevious = actions.onPrevious,
                onNext = actions.onNext,
                foreground = CassetteCream,
                playBackground = CassetteAccent,
                playForeground = Color(0xFF2A1B12),
                playShape = RoundedCornerShape(12.dp),
                playSize = 60.dp,
                playIconSize = 28.dp,
                skipButtonSize = 46.dp,
                skipIconSize = 26.dp,
                spacing = 18.dp,
                leading = {
                    LikeButton(
                        isLiked = isLiked,
                        onToggleLike = actions.onToggleLike,
                        size = 38.dp,
                        iconSize = 20.dp,
                        idleColor = CassetteCream.copy(alpha = 0.72f),
                    )
                },
                trailing = {
                    ModeText(
                        modeLabel = modeLabel,
                        onClick = actions.onCycleMode,
                        foreground = CassetteCream.copy(alpha = 0.64f),
                    )
                },
            )

            Spacer(modifier = Modifier.height(6.dp))

            SecondaryActions(
                onOpenLyric = actions.onOpenLyric,
                onOpenQueue = actions.onOpenQueue,
                onOpenStyleSwitcher = actions.onOpenStyleSwitcher,
                onNotify = actions.onNotify,
                foreground = CassetteCream.copy(alpha = 0.78f),
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
 * 卡带本体。卷轴角度由 withFrameNanos 逐帧累加，只在绘制阶段读取，
 * 播放暂停时角度保留，恢复后从原角度继续。
 */
@Composable
private fun CassetteCard(
    artworkUrl: String?,
    title: String,
    artist: String,
    isPlaying: Boolean,
    isTrial: Boolean,
    onArtworkLoaded: (android.graphics.Bitmap) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap = rememberArtworkBitmap(artworkUrl, maxEdgePx = 256)
    LaunchedEffect(bitmap) {
        bitmap?.let(onArtworkLoaded)
    }

    var reelAngle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastFrame = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastFrame != 0L) {
                    val deltaSeconds = (now - lastFrame) / 1_000_000_000f
                    reelAngle = (reelAngle + deltaSeconds * 150f) % 360f
                }
                lastFrame = now
            }
        }
    }

    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFF2DFC1), Color(0xFFD8BE97)),
                ),
            )
            .border(2.dp, Color(0xFFB08F66), shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2B1D14)),
                contentAlignment = Alignment.Center,
            ) {
                val cover = bitmap
                if (cover != null) {
                    Image(
                        bitmap = cover.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = MusicIcons.MusicNote,
                        contentDescription = null,
                        tint = CassetteCream.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = CassetteInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = CassetteInk.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(if (isPlaying) "SIDE A · 磁带转动中" else "SIDE A · 已暂停")
                        if (isTrial) append(" · 试听")
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = CassetteAccent.copy(alpha = 0.86f),
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp),
        ) {
            val windowTop = 4.dp.toPx()
            val windowHeight = size.height - windowTop - 8.dp.toPx()
            drawRoundRect(
                color = Color(0xFF4A342A),
                topLeft = Offset(size.width * 0.10f, windowTop),
                size = Size(size.width * 0.80f, windowHeight),
                cornerRadius = CornerRadius(16.dp.toPx()),
            )
            drawRoundRect(
                color = Color(0xFF2B1D14),
                topLeft = Offset(size.width * 0.13f, windowTop + 8.dp.toPx()),
                size = Size(size.width * 0.74f, windowHeight - 16.dp.toPx()),
                cornerRadius = CornerRadius(12.dp.toPx()),
            )

            val radius = windowHeight * 0.26f
            val centerY = windowTop + windowHeight * 0.52f
            val left = Offset(size.width * 0.34f, centerY)
            val right = Offset(size.width * 0.66f, centerY)

            // 磁带带面
            drawLine(
                color = Color(0xFFC9A982),
                start = Offset(left.x, left.y - radius * 0.92f),
                end = Offset(right.x, right.y - radius * 0.92f),
                strokeWidth = 2.dp.toPx(),
            )

            drawReel(left, radius, reelAngle)
            drawReel(right, radius, reelAngle)
        }
    }
}

private fun DrawScope.drawReel(center: Offset, radius: Float, angle: Float) {
    drawCircle(color = Color(0xFFE7D3B4), radius = radius, center = center)
    drawCircle(color = Color(0xFF3A2A20), radius = radius * 0.70f, center = center)
    rotate(degrees = angle, pivot = center) {
        repeat(6) { index ->
            val radians = index * Math.PI / 3.0
            drawLine(
                color = Color(0xFFE7D3B4),
                start = center,
                end = Offset(
                    center.x + (cos(radians) * radius * 0.62f).toFloat(),
                    center.y + (sin(radians) * radius * 0.62f).toFloat(),
                ),
                strokeWidth = 3.dp.toPx(),
            )
        }
    }
    drawCircle(color = Color(0xFF8C6A47), radius = radius * 0.18f, center = center)
}
