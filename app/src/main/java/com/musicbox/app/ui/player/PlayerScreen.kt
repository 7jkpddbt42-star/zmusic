package com.musicbox.app.ui.player

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicbox.app.data.BackgroundSpec
import com.musicbox.app.player.PositionSource
import com.musicbox.app.ui.components.BackgroundLayer
import com.musicbox.app.ui.components.ParticleField
import com.musicbox.app.ui.components.VinylRecord
import com.musicbox.app.ui.components.WaveformSeekBar
import com.musicbox.app.ui.components.flowingLight
import com.musicbox.app.ui.components.liquidglass.glassBackdropSource
import com.musicbox.app.ui.components.liquidglass.liquidGlass
import com.musicbox.app.ui.components.liquidglass.rememberGlassBackdrop
import com.musicbox.app.ui.components.rememberFlowProgress
import com.musicbox.app.ui.lyric.rememberFramePosition
import com.musicbox.app.ui.theme.ArtworkImage

/** 播放页上所有文字/图标的基准色：背景始终压了一层暗遮罩，因此固定用白色，不随主题变化。 */
private val Ink = Color.White

/**
 * 全屏播放页。
 *
 * 视觉分层（自下而上）：背景（视频 / 图片 / 封面模糊）→ 暗色渐变遮罩 → 粒子层 → 内容。
 * 遮罩保证任何背景上白色文字都可读（亮色视频背景也不例外），因此本页文字固定用白色，
 * 不受浅色/深色主题影响。
 *
 * 布局参考 InnerTune / Metrolist 等开源播放器：大尺寸方形封面作为唯一视觉焦点，
 * 标题/歌手直接压在遮罩上（不再套玻璃卡片），细线进度条 + 居中大播放键。
 * 进入沉浸模式（长按播放键）后改为黑胶唱片 + 大字号文本 + 波形进度条。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlayerScreen(
    state: PlaybackUiState,
    source: PositionSource,
    background: BackgroundSpec,
    modeLabel: String,
    isFavorite: Boolean,
    immersive: Boolean,
    onToggleImmersive: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCollapse: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleMode: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyric: () -> Unit,
    onArtworkLoaded: (Bitmap) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 返回键：沉浸模式下先退出沉浸，否则收起播放页
    BackHandler {
        if (immersive) onToggleImmersive() else onCollapse()
    }

    // 拖动预览比例：非 null 时时间行显示拖动目标，抬手后回到真实播放位置
    var scrubFraction by remember { mutableStateOf<Float?>(null) }

    // 播放页自有玻璃背景源：背景 / 压暗 / 渐变 / 粒子整体记录，供页面玻璃控件实时采样
    val backdrop = rememberGlassBackdrop()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // 背景源层：只包含要被玻璃折射的内容（玻璃元素都放在它之外，避免自采样鬼影）
        Box(modifier = Modifier.fillMaxSize().glassBackdropSource(backdrop)) {
            // 最底层：自定义背景（视频 > 图片 > 封面模糊），失败自动回退封面模糊
            BackgroundLayer(
                spec = background,
                coverUrl = state.artworkUrl,
                modifier = Modifier.fillMaxSize(),
            )

            // 整层压暗：视频/图片背景往往又亮又花，先统一降亮度，白字才有足够对比
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)))

            // 暗色渐变遮罩：顶部压暗保证顶栏可读，中部留出封面，底部加重保证控制区可读。
            // 这是"任意背景下文字都清晰"的唯一保证，不要删除。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.55f),
                            0.25f to Color.Black.copy(alpha = 0.10f),
                            0.52f to Color.Black.copy(alpha = 0.50f),
                            0.75f to Color.Black.copy(alpha = 0.80f),
                            1f to Color.Black.copy(alpha = 0.95f),
                        ),
                    ),
            )

            // 粒子层：在遮罩之上、内容之下。纯装饰，不拦截点击；暂停即静止
            ParticleField(
                source = source,
                isPlaying = state.isPlaying,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!immersive) {
                // 顶部栏整行包进一块玻璃胶囊（采样的是压暗后的播放页背景，白字仍清晰）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .liquidGlass(
                            backdrop = backdrop,
                            shape = RoundedCornerShape(24.dp),
                            blurRadius = 18.dp,
                            tint = Color.White.copy(alpha = 0.10f),
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onCollapse) {
                        Text("收起", color = Ink.copy(alpha = 0.85f))
                    }
                    Text(
                        text = "正在播放",
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink.copy(alpha = 0.90f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onOpenQueue) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "队列", tint = Ink.copy(alpha = 0.85f))
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 封面区：占据剩余空间，让封面成为唯一视觉焦点
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (immersive) {
                    // 沉浸：保留旋转黑胶（点封面进歌词）
                    VinylRecord(
                        artworkUrl = state.artworkUrl,
                        isPlaying = state.isPlaying,
                        onBitmap = onArtworkLoaded,
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .aspectRatio(1f)
                            .clickable(onClick = onOpenLyric),
                    )
                } else {
                    // 常规：12dp 圆角大方形封面，切歌时 350ms 交叉淡入
                    Crossfade(
                        targetState = state.artworkUrl,
                        animationSpec = tween(durationMillis = 350),
                        label = "cover",
                    ) { url ->
                        ArtworkImage(
                            url = url,
                            shape = RoundedCornerShape(12.dp),
                            onBitmap = onArtworkLoaded,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable(onClick = onOpenLyric),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 标题 / 歌手：直接压在遮罩上，不套卡片
            Text(
                text = state.title.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowingUnderline(color = Ink.copy(alpha = 0.45f))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.artist.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = Ink.copy(alpha = 0.76f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.isTrial) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "试听片段 · 登录解锁完整版",
                    style = MaterialTheme.typography.labelMedium,
                    color = Ink.copy(alpha = 0.62f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 进度条：常规为细线；沉浸模式用波形（视觉焦点更集中）
            if (immersive) {
                WaveformSeekBar(
                    source = source,
                    songId = state.currentSongId,
                    durationMs = state.durationMs,
                    onSeek = {
                        onSeek(it)
                        scrubFraction = null
                    },
                    onScrub = { scrubFraction = it },
                    playedColor = Ink,
                    idleColor = Ink.copy(alpha = 0.28f),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                ThinSeekBar(
                    source = source,
                    durationMs = state.durationMs,
                    onSeek = {
                        onSeek(it)
                        scrubFraction = null
                    },
                    onScrub = { scrubFraction = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatTime(
                        scrubFraction?.let { (it * state.durationMs).toLong() } ?: state.positionMs,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink.copy(alpha = 0.70f),
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatTime(state.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink.copy(alpha = 0.70f),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 控制区：模式 / 上一首 / 播放 / 下一首 / 收藏，整体包进一块玻璃；
            // 播放键等自身内容画在玻璃层之上，不会被玻璃染色（常规与沉浸模式共用本行）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(32.dp),
                        blurRadius = 22.dp,
                        tint = Color.White.copy(alpha = 0.12f),
                    )
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左右两个 weight 容器保证播放键严格居中
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    TextButton(onClick = onCycleMode, contentPadding = PaddingValues(0.dp)) {
                        Text(
                            text = modeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = Ink.copy(alpha = 0.75f),
                        )
                    }
                }
                IconButton(onClick = onPrevious, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "上一首", tint = Ink, modifier = Modifier.size(34.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                // 播放键：72dp 白色圆形，短按播放/暂停，长按进出沉浸
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Ink)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current,
                            onClick = onTogglePlay,
                            onLongClick = onToggleImmersive,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        tint = Color.Black,
                        modifier = Modifier.size(34.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "下一首", tint = Ink, modifier = Modifier.size(34.dp))
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(52.dp)) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "收藏",
                            tint = if (isFavorite) Color(0xFFFF6B8A) else Ink.copy(alpha = 0.80f),
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }

            if (!immersive) {
                Spacer(modifier = Modifier.height(4.dp))
                // 底部歌词按钮：小玻璃胶囊
                TextButton(
                    onClick = onOpenLyric,
                    modifier = Modifier.liquidGlass(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(24.dp),
                        blurRadius = 18.dp,
                        tint = Color.White.copy(alpha = 0.12f),
                    ),
                ) {
                    Icon(
                        Icons.Filled.Lyrics,
                        contentDescription = null,
                        tint = Ink.copy(alpha = 0.80f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "歌词",
                        style = MaterialTheme.typography.labelLarge,
                        color = Ink.copy(alpha = 0.80f),
                    )
                }
            }

            if (state.isBuffering) {
                Text(
                    text = "缓冲中…",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink.copy(alpha = 0.60f),
                )
            }

            if (immersive) {
                Text(
                    text = "长按播放键退出沉浸",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink.copy(alpha = 0.45f),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * 细线进度条：3dp 圆角轨道 + 6dp 白色圆点滑块。
 *
 * 逐帧位置只在 Canvas 的 draw lambda 里读取（只失效 draw 阶段，不触发每帧重组）；
 * 拖动预览比例同样只在 draw lambda 里读。点击/拖动均可跳转。
 */
@Composable
private fun ThinSeekBar(
    source: PositionSource,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onScrub: ((Float?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val framePosition = rememberFramePosition(source)
    var dragFraction by remember { mutableStateOf<Float?>(null) }

    Canvas(
        modifier = modifier
            .height(28.dp)
            .pointerInput(durationMs) {
                awaitEachGesture {
                    val widthPx = size.width.toFloat().coerceAtLeast(1f)
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var fraction = (down.position.x / widthPx).coerceIn(0f, 1f)
                    // 按下即跳转；时长非法时不 seek
                    if (durationMs > 0L) onSeek((fraction * durationMs).toLong())
                    dragFraction = fraction
                    onScrub?.invoke(fraction)

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        fraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                        dragFraction = fraction
                        onScrub?.invoke(fraction)
                        change.consume()
                    }

                    if (durationMs > 0L) onSeek((fraction * durationMs).toLong())
                    dragFraction = null
                    onScrub?.invoke(null)
                }
            },
    ) {
        val playingFraction =
            if (durationMs > 0L) (framePosition.value.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        val fraction = dragFraction ?: playingFraction

        val trackHeight = 3.dp.toPx()
        val thumbRadius = 6.dp.toPx()
        val centerY = size.height / 2f
        val startX = thumbRadius
        val endX = (size.width - thumbRadius).coerceAtLeast(startX + 1f)
        val barWidth = endX - startX

        drawLine(
            color = Ink.copy(alpha = 0.28f),
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = trackHeight,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Ink,
            start = Offset(startX, centerY),
            end = Offset(startX + barWidth * fraction, centerY),
            strokeWidth = trackHeight,
            cap = StrokeCap.Round,
        )
        drawCircle(color = Ink, radius = thumbRadius, center = Offset(startX + barWidth * fraction, centerY))
    }
}

/**
 * 流光短线：位于标题下方，2dp 高、48dp 宽，读取 [rememberFlowProgress] 后交给 [flowingLight] 绘制。
 * 把进度读取收敛在本 Composable 内，避免每帧重组的范围扩散到整个播放页。
 */
@Composable
private fun FlowingUnderline(color: Color, modifier: Modifier = Modifier) {
    val progress = rememberFlowProgress()
    Box(
        modifier = modifier
            .size(width = 48.dp, height = 2.dp)
            .flowingLight(progress = progress, colors = listOf(color, Color.Transparent)),
    )
}

/** 毫秒 → m:ss。 */
internal fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
