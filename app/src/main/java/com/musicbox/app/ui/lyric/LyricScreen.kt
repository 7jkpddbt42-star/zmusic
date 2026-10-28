package com.musicbox.app.ui.lyric

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicbox.app.data.BackgroundSpec
import com.musicbox.app.data.LyricStyle
import com.musicbox.app.lyric.LyricLine
import com.musicbox.app.lyric.LyricWord
import com.musicbox.app.lyric.wordProgress
import com.musicbox.app.player.PositionSource
import com.musicbox.app.ui.components.MusicIcons
import com.musicbox.app.ui.components.liquidglass.GlassAmbientBackdrop
import com.musicbox.app.ui.components.liquidglass.glassBackdropSource
import com.musicbox.app.ui.components.liquidglass.liquidGlass
import com.musicbox.app.ui.components.liquidglass.rememberGlassBackdrop
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

/** 歌词页文字基准色：页面使用与播放页一致的真实背景 + 压暗遮罩，文字固定白色系。 */
private val Ink = Color.White

/** 聚焦样式：所有行统一按当前行字号排版，非当前行整体缩小（缩放只走图层，不重排文字）。 */
private val FocusLineStyle = TextStyle(
    fontSize = 23.sp,
    lineHeight = 33.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 0.3.sp,
)

private val FocusTranslationStyle = TextStyle(fontSize = 13.sp, lineHeight = 20.sp)

/**
 * 逐字歌词页。动画由 [rememberFramePosition] 的逐帧时钟驱动（设计文档 §5.2），
 * 每个字的"填充进度"每一帧重算一次，因此跟得上、不顿。
 *
 * 提供两种视觉样式（设置页可切换）：
 * - [LyricStyle.Focus]：当前行居中大字高亮、其余行按距离缩小淡出，滚动到视口上方约 40% 处；
 * - [LyricStyle.Classic]：统一字号左对齐列表，当前行轻微放大。
 *
 * 60fps 关键：
 * - 逐帧位置只在 LazyColumn 的条目 lambda 与活动行的绘制 lambda 中读取，页面顶层不读，
 *   时钟每帧跳动不会重组整页；
 * - 当前行索引用 `derivedStateOf` 收敛，只有索引真正变化时才通知可见行重组；
 * - 缩放/透明度动画都在 graphicsLayer 阶段执行，不触发每帧重组，也不重排文字。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LyricScreen(
    state: LyricUiState,
    source: PositionSource,
    title: String?,
    artist: String?,
    background: BackgroundSpec,
    artworkUrl: String?,
    showTranslation: Boolean,
    showRomaji: Boolean,
    lyricStyle: LyricStyle,
    onToggleTranslation: () -> Unit,
    onToggleRomaji: () -> Unit,
    onSeek: (Long) -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onCollapse)

    // 页面自有玻璃背景源：与播放页一致的真实背景（视频/图片/封面），顶部玻璃胶囊逐帧采样
    val backdrop = rememberGlassBackdrop()

    // 空白处轻触 → 淡出返回播放页；歌词行、顶栏按钮各自的点击仍然优先
    val collapseInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = collapseInteraction,
                indication = null,
                onClick = onCollapse,
            ),
    ) {
        // 背景源层：真实背景 + 压暗遮罩，视频播放时玻璃模糊逐帧更新；
        // 不再使用纯渐变，否则模糊与折射不可见
        GlassAmbientBackdrop(
            background = background,
            artworkUrl = artworkUrl,
            scrim = Color.Black.copy(alpha = 0.62f),
            coverBlurRadius = 3.dp,
            modifier = Modifier
                .fillMaxSize()
                .glassBackdropSource(backdrop),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // 顶部返回/歌名/译音开关：整行包进一块玻璃胶囊
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    // 顶栏空白处吞掉点击，避免误触整页的「返回」手势
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .liquidGlass(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(28.dp),
                        blurRadius = 6.dp,
                        tint = Color.White.copy(alpha = 0.08f),
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        imageVector = MusicIcons.ChevronDown,
                        contentDescription = "返回",
                        tint = Ink.copy(alpha = 0.88f),
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink.copy(alpha = 0.92f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = artist.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Ink.copy(alpha = 0.60f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(onClick = onToggleTranslation) {
                    Text(
                        text = "译",
                        color = if (showTranslation) Ink else Ink.copy(alpha = 0.45f),
                    )
                }
                TextButton(onClick = onToggleRomaji) {
                    Text(
                        text = "音",
                        color = if (showRomaji) Ink else Ink.copy(alpha = 0.45f),
                    )
                }
            }

            when (val s = state) {
                is LyricUiState.Ready -> when (lyricStyle) {
                    LyricStyle.Focus -> FocusedLyricList(
                        lines = s.timeline.lines,
                        source = source,
                        showTranslation = showTranslation,
                        showRomaji = showRomaji,
                        onLineClick = onSeek,
                        modifier = Modifier.weight(1f),
                    )

                    LyricStyle.Classic -> ClassicLyricList(
                        lines = s.timeline.lines,
                        source = source,
                        showTranslation = showTranslation,
                        showRomaji = showRomaji,
                        onLineClick = onSeek,
                        modifier = Modifier.weight(1f),
                    )
                }

                else -> Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = when (s) {
                            LyricUiState.Loading -> "歌词加载中…"
                            LyricUiState.Failed -> "歌词获取失败"
                            else -> "暂无歌词"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink.copy(alpha = 0.60f),
                    )
                }
            }
        }
    }
}

/** 二分查找当前行索引；返回 -1 表示还没到第一行。避免逐帧线性扫描整份歌词。 */
private fun lyricIndexAt(lines: List<LyricLine>, positionMs: Long): Int {
    var low = 0
    var high = lines.lastIndex
    var result = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (lines[mid].startMs <= positionMs) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}

/**
 * 聚焦样式：当前行居中大字，其余行按与当前行的距离缩小、淡出。
 *
 * 滚动：新歌词直接跳到当前行（不做跨全曲的长距离动画），之后每次换行用
 * `animateScrollToItem` 平滑滚动，让当前行停在视口上方约 36% 处；
 * 该动画在 LazyList 的滚动通道执行，不产生逐帧重组。
 */
@Composable
private fun FocusedLyricList(
    lines: List<LyricLine>,
    source: PositionSource,
    showTranslation: Boolean,
    showRomaji: Boolean,
    onLineClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // 逐帧位置：只在本组件里保留，不在外层读取 value（避免每帧重组）
    val position = rememberFramePosition(source)
    // 当前行索引：只有索引真正变化才会让读取它的可见行重组
    val activeIndex = remember(lines) {
        derivedStateOf { lyricIndexAt(lines, position.value) }
    }

    LaunchedEffect(listState, lines) {
        // 切歌/进入页面：先瞬间对齐当前行，避免从第 0 行一路滚动过来
        val first = activeIndex.value
        if (first >= 0) listState.scrollToItem(first)
        // 之后换行：平滑滚动跟随（collectLatest 会取消上一段未完成的滚动）
        snapshotFlow { activeIndex.value }
            .distinctUntilChanged()
            .collectLatest { index ->
                if (index >= 0) listState.animateScrollToItem(index)
            }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // 当前行定位在视口上方约 36%：上面是已唱、下方留出足够待唱
        val topPadding = maxHeight * 0.36f
        val bottomPadding = maxHeight * 0.58f
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp),
        ) {
            itemsIndexed(lines) { index, line ->
                val active = activeIndex.value
                val distance = if (active < 0) 3 else abs(index - active)
                FocusedLyricRow(
                    line = line,
                    isActive = active >= 0 && index == active,
                    isPast = active >= 0 && index < active,
                    distance = distance,
                    livePosition = if (index == active) position else null,
                    showTranslation = showTranslation,
                    showRomaji = showRomaji,
                    onClick = { onLineClick(line.startMs) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusedLyricRow(
    line: LyricLine,
    isActive: Boolean,
    isPast: Boolean,
    distance: Int,
    livePosition: State<Long>?,
    showTranslation: Boolean,
    showRomaji: Boolean,
    onClick: () -> Unit,
) {
    // 目标缩放：当前行 1.0（23sp），相邻行约 16.5sp，更远行再略小
    val targetScale = when {
        isActive -> 1f
        distance == 1 -> 0.72f
        else -> 0.68f
    }
    val targetAlpha = when {
        isActive -> 1f
        distance == 1 -> 0.52f
        distance == 2 -> 0.30f
        else -> 0.16f
    }
    // 动画只在图层/绘制阶段读取，不触发每帧重组，也不重排文字
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 420),
        label = "lyricScale",
    )
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 420),
        label = "lyricAlpha",
    )

    // 当前行：已唱部分纯白、未唱部分半透明白；非当前行统一白（整行透明度由图层控制）
    val baseColor = if (isActive) Ink.copy(alpha = 0.36f) else Ink
    val fillColor = Ink

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (line.words.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                line.words.forEach { word ->
                    KaraokeWord(
                        word = word,
                        staticProgress = if (isPast) 1f else 0f,
                        livePosition = livePosition,
                        baseColor = baseColor,
                        fillColor = fillColor,
                        style = FocusLineStyle,
                        alpha = 1f,
                    )
                }
            }
        } else {
            // LRC 降级：整行高亮（设计文档 §5.3）
            Text(
                text = line.text,
                style = FocusLineStyle,
                color = if (isActive) Ink else Ink.copy(alpha = 0.92f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (showTranslation && !line.translation.isNullOrBlank()) {
            Text(
                text = line.translation!!,
                style = FocusTranslationStyle,
                color = Ink.copy(alpha = 0.70f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }
        if (showRomaji && !line.romaji.isNullOrBlank()) {
            Text(
                text = line.romaji!!,
                style = FocusTranslationStyle,
                color = Ink.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
            )
        }
    }
}

/** 经典样式：统一字号左对齐列表，当前行轻微放大并滚到视口约 1/3 处。 */
@Composable
private fun ClassicLyricList(
    lines: List<LyricLine>,
    source: PositionSource,
    showTranslation: Boolean,
    showRomaji: Boolean,
    onLineClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // 逐帧位置：只在本组件里创建；不在这里读取 value，避免每帧重组
    val position = rememberFramePosition(source)
    // 当前行索引：只有索引真正变化时才通知列表重组
    val activeIndex by remember(lines) {
        derivedStateOf { lyricIndexAt(lines, position.value) }
    }

    // 当前行 → 每次变化把该行滚到视口约 1/3 高处（负 offset 把 item 往下压）
    LaunchedEffect(activeIndex, lines) {
        if (activeIndex < 0) return@LaunchedEffect
        val viewportHeight = listState.layoutInfo.viewportSize.height
        val offset = if (viewportHeight > 0) -(viewportHeight / 3) else 0
        listState.animateScrollToItem(activeIndex, offset)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
    ) {
        item { Spacer(modifier = Modifier.height(160.dp)) }
        itemsIndexed(lines) { index, line ->
            val isActive = index == activeIndex
            ClassicLyricLineRow(
                line = line,
                isActive = isActive,
                isPast = index < activeIndex,
                livePosition = if (isActive) position else null,
                showTranslation = showTranslation,
                showRomaji = showRomaji,
                onClick = { onLineClick(line.startMs) },
            )
        }
        item { Spacer(modifier = Modifier.height(240.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassicLyricLineRow(
    line: LyricLine,
    isActive: Boolean,
    isPast: Boolean,
    livePosition: State<Long>?,
    showTranslation: Boolean,
    showRomaji: Boolean,
    onClick: () -> Unit,
) {
    // 深色渐变底上固定白系：当前行未唱部分淡、非当前行更灰，填充色仍用主题色
    val onSurface = Ink
    val dim = Ink.copy(alpha = 0.72f)

    // 当前行的"已唱"部分用主题色，未唱部分用半透明 onSurface；非当前行整体暗
    val baseColor = if (isActive) onSurface.copy(alpha = 0.82f) else dim
    val fillColor = if (isActive) MaterialTheme.colorScheme.primary else dim

    val alpha by animateFloatAsState(targetValue = if (isActive) 1f else 0.9f, label = "lineAlpha")
    // 当前行整体轻微放大；行级过渡允许用 animateFloatAsState（非逐帧）
    val scale by animateFloatAsState(targetValue = if (isActive) 1.02f else 1f, label = "lineScale")
    val style: TextStyle = if (isActive) {
        MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 32.sp)
    } else {
        MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 26.sp)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        if (line.words.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.Start) {
                line.words.forEach { word ->
                    KaraokeWord(
                        word = word,
                        staticProgress = if (isPast) 1f else 0f,
                        livePosition = livePosition,
                        baseColor = baseColor,
                        fillColor = fillColor,
                        style = style,
                        alpha = alpha,
                    )
                }
            }
        } else {
            // LRC 降级：整行高亮（设计文档 §5.3）
            Text(
                text = line.text,
                style = style,
                color = if (isActive) onSurface else dim,
            )
        }

        if (showTranslation && !line.translation.isNullOrBlank()) {
            Text(
                text = line.translation!!,
                style = MaterialTheme.typography.bodySmall,
                color = Ink.copy(alpha = if (isActive) 0.9f else 0.5f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (showRomaji && !line.romaji.isNullOrBlank()) {
            Text(
                text = line.romaji!!,
                style = MaterialTheme.typography.labelSmall,
                color = Ink.copy(alpha = if (isActive) 0.75f else 0.4f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * 一个词的逐字填充：暗底 + 亮面（亮面按进度裁剪）。
 *
 * 当前行（[livePosition] 非空）的 progress 在 graphicsLayer / drawWithContent 的
 * lambda 里逐帧读取——这两个阶段失效不会触发重组，只重绘当前词；
 * 非当前行使用调用方给定的静态进度（已唱完 = 1，未唱 = 0），完全不参与逐帧刷新。
 *
 * 当前行额外做逐字"上浮 + 放大"——同样由 progress 每帧驱动，
 * 严禁使用 animateFloatAsState（会丢毫秒精度）。变换加在包住
 * 「暗底 + 亮面」两层文字的外层 Box 上，保证两层同步位移、不错位。
 */
@Composable
private fun KaraokeWord(
    word: LyricWord,
    staticProgress: Float,
    livePosition: State<Long>?,
    baseColor: Color,
    fillColor: Color,
    style: TextStyle,
    alpha: Float,
) {
    // 上浮幅度约 0.25em：style.fontSize.value 为 sp 数值
    val density = LocalDensity.current
    val risePx = style.fontSize.value * density.density * density.fontScale * 0.25f

    Box(
        modifier = if (livePosition != null) {
            Modifier.graphicsLayer {
                val progress = wordProgress(livePosition.value, word)
                translationY = -risePx * progress
                val s = 1f + 0.06f * progress
                scaleX = s
                scaleY = s
            }
        } else {
            Modifier
        },
    ) {
        Text(
            text = word.text,
            style = style,
            color = baseColor.copy(alpha = baseColor.alpha * alpha),
            softWrap = false,
        )
        if (livePosition != null || staticProgress > 0f) {
            Text(
                text = word.text,
                style = style,
                color = fillColor.copy(alpha = fillColor.alpha * alpha),
                softWrap = false,
                modifier = Modifier.drawWithContent {
                    val progress = if (livePosition != null) {
                        wordProgress(livePosition.value, word)
                    } else {
                        staticProgress
                    }
                    if (progress > 0f) {
                        clipRect(right = size.width * progress) {
                            this@drawWithContent.drawContent()
                        }
                    }
                },
            )
        }
    }
}
