package com.musicbox.app.ui.styles

import android.graphics.Bitmap
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/** 拖过整页宽度的这个比例就提交切歌，否则回弹。 */
private const val COMMIT_FRACTION = 0.22f

/** 相邻两张唱片之间留的缝，滑动时能看清两张盘的分界。 */
private val DiscGap = 30.dp

/** 唱片回弹：略欠阻尼，停得住又有一点弹性。 */
private val SettleSpec: AnimationSpec<Float> =
    spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)

/**
 * 手势结束后要做的事。
 * 手势回调是「受限挂起作用域」，不能在里面直接跑动画，
 * 因此只记录意图，由 [LaunchedEffect] 在普通协程里执行。
 */
private sealed interface StageAction {
    data object Settle : StageAction
    data class Commit(val forward: Boolean) : StageAction
}

/**
 * VINYL：黑胶唱片。
 *
 * 旋转唱盘 + 唱臂是视觉主体，标题与进度被压缩到盘面下方，控制区只有一行小尺寸按钮。
 * 左右拖动唱片时，下一张（或上一张）唱片会跟着手指一起滑进来；松开后按位移提交切歌，
 * 提交时唱片滑到位、索引归位，全程不闪不跳。唱臂固定在右上角，不跟着盘面平移，
 * 只在拖动时轻轻抬起，播放/暂停仍有原有的落针/抬针动画。
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
                VinylStage(
                    state = state,
                    onOpenLyric = actions.onOpenLyric,
                    onNext = actions.onNext,
                    onPrevious = actions.onPrevious,
                    onArtworkLoaded = actions.onArtworkLoaded,
                    modifier = Modifier.fillMaxWidth(0.96f).aspectRatio(1f),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

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
 * 唱盘舞台：单张唱片时可点击进歌词页；左右拖动时相邻唱片随手指滑入。
 *
 * 索引处理遵循「视觉位置连续」：提交切歌先把唱片滑到 ±1 页，等播放器索引真正变化后
 * 再把基准索引归位并重置偏移，两个动作对同一条唱片在屏幕上等价，因此看不出跳变。
 */
@Composable
private fun VinylStage(
    state: PlaybackUiState,
    onOpenLyric: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onArtworkLoaded: (Bitmap) -> Unit,
    modifier: Modifier = Modifier,
) {
    val queue = state.queue
    var settledIndex by remember { mutableIntStateOf(state.currentIndex) }
    // 页偏移：-1 表示下一张滑到中间，+1 表示上一张滑到中间；只在图层阶段被读取
    var pageOffset by remember { mutableFloatStateOf(0f) }
    // 拖动/回弹/提交动画期间才组合相邻唱片，静止时只维护一张，避免多跑两份旋转时钟
    var neighboursVisible by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<StageAction?>(null) }

    // 播放器索引变化（按钮切歌 / 自动播完 / 滑动提交）→ 基准归位
    LaunchedEffect(state.currentIndex) {
        if (state.currentIndex != settledIndex) {
            settledIndex = state.currentIndex
            pageOffset = 0f
            neighboursVisible = false
        }
    }

    // 动画（回弹 / 提交切歌）在普通协程里执行，手势回调只负责记录意图
    LaunchedEffect(pending) {
        suspend fun animateOffsetTo(target: Float, spec: AnimationSpec<Float>) {
            animate(
                initialValue = pageOffset,
                targetValue = target,
                animationSpec = spec,
                block = { value, _ -> pageOffset = value },
            )
        }

        when (val action = pending) {
            StageAction.Settle -> {
                animateOffsetTo(0f, SettleSpec)
                neighboursVisible = false
                pending = null
            }

            is StageAction.Commit -> {
                animateOffsetTo(if (action.forward) -1f else 1f, tween(durationMillis = 260))
                val startIndex = settledIndex
                if (action.forward) onNext() else onPrevious()
                // 播放器索引迟迟不变（队列尽头 / 播放失败）→ 把唱片滑回原位
                val moved = withTimeoutOrNull(1200) {
                    snapshotFlow { settledIndex }.first { it != startIndex }
                } != null
                if (!moved) {
                    animateOffsetTo(0f, SettleSpec)
                    neighboursVisible = false
                }
                pending = null
            }

            null -> Unit
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .pointerInput(state.currentIndex) {
                val gapPx = DiscGap.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 抓住唱片即打断未完成的回弹/提交动画，从当前位移继续拖
                    pending = null
                    val slop = viewConfiguration.touchSlop
                    var totalX = 0f
                    var dragging = false
                    var cancelled = false
                    val pagePx = size.width.toFloat() + gapPx

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            cancelled = true
                            break
                        }
                        if (change.changedToUpIgnoreConsumed()) break
                        if (change.isConsumed && !dragging) {
                            cancelled = true
                            break
                        }

                        val dx = change.positionChange().x
                        totalX += dx
                        if (!dragging && abs(totalX) > slop) {
                            dragging = true
                            neighboursVisible = true
                        }
                        if (!dragging) continue

                        change.consume()
                        val hasNext = queue.getOrNull(settledIndex + 1) != null
                        val hasPrev = queue.getOrNull(settledIndex - 1) != null
                        val raw = pageOffset + dx / pagePx
                        // 没有相邻歌曲时仍允许轻微拖动，但拉不满整页
                        val limited = when {
                            raw < 0f && !hasNext -> raw * 0.18f
                            raw > 0f && !hasPrev -> raw * 0.18f
                            else -> raw
                        }.coerceIn(-1.1f, 1.1f)
                        pageOffset = limited
                    }

                    if (cancelled) {
                        pending = StageAction.Settle
                        return@awaitEachGesture
                    }
                    if (!dragging) {
                        onOpenLyric()
                        return@awaitEachGesture
                    }

                    val offset = pageOffset
                    val goNext = offset < -COMMIT_FRACTION && queue.getOrNull(settledIndex + 1) != null
                    val goPrev = offset > COMMIT_FRACTION && queue.getOrNull(settledIndex - 1) != null
                    pending = when {
                        goNext -> StageAction.Commit(forward = true)
                        goPrev -> StageAction.Commit(forward = false)
                        else -> StageAction.Settle
                    }
                }
            },
    ) {
        val density = LocalDensity.current
        val pagePx = with(density) { maxWidth.toPx() + DiscGap.toPx() }
        val current = settledIndex

        val indices = if (current < 0) {
            listOf(-1)
        } else {
            buildList {
                add(current)
                if (neighboursVisible) {
                    if (current - 1 >= 0) add(current - 1)
                    if (current + 1 <= queue.lastIndex) add(current + 1)
                }
            }
        }

        indices.forEach { index ->
            key(index) {
                val artwork = when {
                    index == state.currentIndex -> state.artworkUrl ?: queue.getOrNull(index)?.artworkUrl
                    else -> queue.getOrNull(index)?.artworkUrl
                }
                VinylRecord(
                    artworkUrl = artwork,
                    isPlaying = state.isPlaying,
                    onBitmap = if (index == current) onArtworkLoaded else null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize()
                        .graphicsLayer {
                            val relative = (index - current) + pageOffset
                            translationX = relative * pagePx
                            val distance = abs(relative).coerceIn(0f, 1f)
                            val scale = 1f - 0.10f * distance
                            scaleX = scale
                            scaleY = scale
                            alpha = (1f - 0.55f * distance).coerceIn(0.32f, 1f)
                        }
                        .shadow(
                            elevation = 16.dp,
                            shape = CircleShape,
                            ambientColor = Color.Black.copy(alpha = 0.26f),
                            spotColor = Color.Black.copy(alpha = 0.34f),
                        ),
                )
            }
        }

        // 唱臂固定在盘面右上角：不随唱片滑动，只在拖动时抬起
        Tonearm(
            isPlaying = state.isPlaying,
            dragProgress = { abs(pageOffset).coerceIn(0f, 1f) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(width = 94.dp, height = 146.dp),
        )
    }
}

/**
 * 唱臂：播放时向内压到唱片上（角度更小），暂停时抬起。
 * 拖动唱片时按拖动进度再抬起几度，松开自动落回；旋转只在 graphicsLayer 阶段执行。
 */
@Composable
private fun Tonearm(
    isPlaying: Boolean,
    dragProgress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val angle by animateFloatAsState(
        targetValue = if (isPlaying) 4f else 26f,
        animationSpec = tween(durationMillis = 720),
        label = "tonearm",
    )

    Canvas(
        modifier = modifier.graphicsLayer {
            rotationZ = angle + dragProgress() * 11f
            transformOrigin = TransformOrigin(0.84f, 0.12f)
        },
    ) {
        val base = Offset(size.width * 0.84f, size.height * 0.12f)
        val tip = Offset(base.x - size.width * 0.16f, size.height * 0.94f)

        // 配重块：让唱臂有机械感
        drawCircle(
            color = Color.White.copy(alpha = 0.18f),
            radius = 12.dp.toPx(),
            center = Offset(base.x + size.width * 0.13f, base.y - size.height * 0.06f),
        )
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
