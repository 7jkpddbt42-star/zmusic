package com.musicbox.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.musicbox.app.data.BackgroundSpec
import com.musicbox.app.model.PlayMode
import com.musicbox.app.player.SLEEP_AFTER_CURRENT
import com.musicbox.app.ui.components.GlassPanel
import com.musicbox.app.ui.components.GlassPill
import com.musicbox.app.ui.components.MusicIcons
import com.musicbox.app.ui.components.liquidglass.GlassAmbientBackdrop
import com.musicbox.app.ui.components.liquidglass.LocalGlassBackdrop
import com.musicbox.app.ui.components.liquidglass.glassBackdropSource
import com.musicbox.app.ui.components.liquidglass.rememberGlassBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 抽屉打开：自下而上滑入。 */
private const val QUEUE_SLIDE_IN_MS = 340

/** 抽屉收起：下滑离场，比进场略快。 */
private const val QUEUE_SLIDE_OUT_MS = 260

/** 睡眠定时档位：文案 → 分钟数（0 关闭，-1 播完当前这首）。 */
private val SLEEP_OPTIONS = listOf(
    "关闭" to 0,
    "15 分钟" to 15,
    "30 分钟" to 30,
    "60 分钟" to 60,
    "90 分钟" to 90,
    "播完当前" to SLEEP_AFTER_CURRENT,
)

/** 抽屉占屏高度比例，接近主流播放器的队列抽屉。 */
private const val QUEUE_SHEET_HEIGHT_FRACTION = 0.72f

/** 抽屉打开前的预热帧数：先让背景层录几帧，玻璃滑入时折射立刻可见。 */
private const val QUEUE_BACKDROP_WARMUP_FRAMES = 2

/**
 * 队列抽屉：查看 / 删除 / 切换播放模式。
 *
 * 交互是自下而上的抽屉：底部滑入、点击遮罩或返回键收起。
 *
 * 液态玻璃仍然是实时渲染：窗口内自带真实背景源（视频/图片/封面）并持续录制，
 * 抽屉玻璃每帧采样这层背景，拖动队列时折射与模糊跟手变化。
 * 打开前先空跑几帧让背景源完成首帧录制（预热），因此滑入的第一帧就有玻璃质感，
 * 不会出现「先是一块纯色、过一帧才变玻璃」的闪变。
 */
@Composable
internal fun QueueSheet(
    state: PlaybackUiState,
    modeLabel: String,
    background: BackgroundSpec,
    artworkUrl: String?,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onPlayNext: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onCycleMode: () -> Unit,
    sleepTimerLabel: String?,
    onSleepTimer: (Int) -> Unit,
) {
    // Dialog 是独立窗口，无法采样播放页的背景图层，这里在窗口内自建一份背景源
    val backdrop = rememberGlassBackdrop()
    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 拖动排序：只记录「哪一行在拖」与累计位移，松手时才真正重排。
    // 如果拖动过程中逐行移动，列表 key 会跟着变，这一行的 pointerInput 会被重建、手势中断。
    val listState = rememberLazyListState()
    var dragPos by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeightPx by remember { mutableFloatStateOf(0f) }
    val fallbackRowHeightPx = with(LocalDensity.current) { 62.dp.toPx() }

    // 遮罩随抽屉一起过渡：进场 340ms、退场 260ms，与抽屉滑出同帧结束，
    // 避免抽屉已滑走、遮罩还剩一点暗色导致关闭瞬间闪一下。
    val scrimAlpha by animateFloatAsState(
        targetValue = if (shown) 0.46f else 0f,
        animationSpec = tween(if (shown) QUEUE_SLIDE_IN_MS else QUEUE_SLIDE_OUT_MS),
        label = "queueScrim",
    )

    // 预热：先让背景源绘制几帧再触发滑入，玻璃首帧就是真实折射
    LaunchedEffect(Unit) {
        repeat(QUEUE_BACKDROP_WARMUP_FRAMES) { withFrameNanos { } }
        shown = true
    }

    // 先播放收起动画，再真正关闭窗口
    val requestClose: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                shown = false
                delay(QUEUE_SLIDE_OUT_MS.toLong())
                onDismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = requestClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 背景源层：只放真实背景，玻璃抽屉放在内容层，避免自采样鬼影
            GlassAmbientBackdrop(
                background = background,
                artworkUrl = artworkUrl,
                scrim = MaterialTheme.colorScheme.surface.copy(alpha = scrimAlpha),
                modifier = Modifier.fillMaxSize().glassBackdropSource(backdrop),
            )

            AnimatedVisibility(
                visible = shown,
                enter = slideInVertically(
                    initialOffsetY = { height -> height },
                    animationSpec = tween(QUEUE_SLIDE_IN_MS, easing = FastOutSlowInEasing),
                ),
                exit = slideOutVertically(
                    targetOffsetY = { height -> height },
                    animationSpec = tween(QUEUE_SLIDE_OUT_MS, easing = FastOutLinearInEasing),
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 点击抽屉以外的区域关闭
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = requestClose,
                            ),
                    )

                    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                        GlassPanel(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .fillMaxHeight(QUEUE_SHEET_HEIGHT_FRACTION),
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .navigationBarsPadding(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // 抽屉把手
                                Box(
                                    modifier = Modifier
                                        .padding(top = 10.dp)
                                        .size(width = 40.dp, height = 4.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                .copy(alpha = 0.35f),
                                        ),
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "播放队列",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = "${state.queue.size} 首 · 点击切换",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    GlassPill(onClick = onCycleMode) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                        ) {
                                            Icon(
                                                imageVector = when (PlayMode.fromLabel(modeLabel)) {
                                                    PlayMode.ORDER -> MusicIcons.Repeat
                                                    PlayMode.REPEAT_ONE -> MusicIcons.RepeatOne
                                                    PlayMode.SHUFFLE -> MusicIcons.Shuffle
                                                },
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = "模式：$modeLabel",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                }

                                // 睡眠定时入口放在队列抽屉里：9 种播放风格共用这一处，
                                // 不必为每种风格的按钮排布再各加一个图标。
                                var sleepMenuOpen by remember { mutableStateOf(false) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 20.dp, end = 16.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box {
                                        GlassPill(onClick = { sleepMenuOpen = true }) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center,
                                            ) {
                                                Icon(
                                                    imageVector = MusicIcons.Timer,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.size(14.dp),
                                                )
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = sleepTimerLabel?.let { "定时：$it" }
                                                        ?: "睡眠定时",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                        }
                                        DropdownMenu(
                                            expanded = sleepMenuOpen,
                                            onDismissRequest = { sleepMenuOpen = false },
                                        ) {
                                            SLEEP_OPTIONS.forEach { (label, minutes) ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = label,
                                                            color = if (
                                                                minutes == 0 && sleepTimerLabel == null
                                                            ) {
                                                                MaterialTheme.colorScheme.primary
                                                            } else {
                                                                MaterialTheme.colorScheme.onSurface
                                                            },
                                                        )
                                                    },
                                                    onClick = {
                                                        sleepMenuOpen = false
                                                        onSleepTimer(minutes)
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }

                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                    contentPadding = PaddingValues(
                                        start = 12.dp,
                                        end = 12.dp,
                                        top = 8.dp,
                                        bottom = 12.dp,
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    items(state.queue, key = { it.index }) { item ->
                                        val primary = MaterialTheme.colorScheme.primary
                                        val dragging = dragPos == item.index
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .zIndex(if (dragging) 1f else 0f)
                                                .graphicsLayer {
                                                    translationY = if (dragging) dragOffset else 0f
                                                    val s = if (dragging) 1.02f else 1f
                                                    scaleX = s
                                                    scaleY = s
                                                }
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    when {
                                                        dragging -> primary.copy(alpha = 0.18f)
                                                        item.isCurrent -> primary.copy(alpha = 0.10f)
                                                        else -> Color.Transparent
                                                    },
                                                )
                                                .clickable { onSelect(item.index) }
                                                .padding(
                                                    start = 12.dp,
                                                    top = 10.dp,
                                                    bottom = 10.dp,
                                                    end = 0.dp,
                                                ),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (item.isCurrent) {
                                                        FontWeight.SemiBold
                                                    } else {
                                                        FontWeight.Normal
                                                    },
                                                    color = if (item.isCurrent) {
                                                        primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    },
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = item.artist,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            // 下一首播放：把这首插到当前曲目后面
                                            IconButton(
                                                onClick = { onPlayNext(item.index) },
                                                modifier = Modifier.size(48.dp),
                                                enabled = !item.isCurrent,
                                            ) {
                                                Icon(
                                                    imageVector = MusicIcons.SkipNext,
                                                    contentDescription = "下一首播放",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        .copy(alpha = if (item.isCurrent) 0.3f else 0.8f),
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                            // 拖动手柄：按住上下拖动，松手后落到新位置
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .pointerInput(item.index, state.queue.size) {
                                                        detectDragGestures(
                                                            onDragStart = {
                                                                dragPos = item.index
                                                                dragOffset = 0f
                                                                rowHeightPx =
                                                                    listState.layoutInfo
                                                                        .visibleItemsInfo
                                                                        .firstOrNull { info ->
                                                                            info.index == item.index
                                                                        }?.size?.toFloat()
                                                                        ?: fallbackRowHeightPx
                                                            },
                                                            onDragEnd = {
                                                                val from = dragPos
                                                                if (from != null) {
                                                                    val steps = if (rowHeightPx > 0) {
                                                                        (dragOffset / rowHeightPx)
                                                                            .roundToInt()
                                                                    } else {
                                                                        0
                                                                    }
                                                                    val to = (from + steps)
                                                                        .coerceIn(0, state.queue.size - 1)
                                                                    if (to != from) onMove(from, to)
                                                                }
                                                                dragPos = null
                                                                dragOffset = 0f
                                                            },
                                                            onDragCancel = {
                                                                dragPos = null
                                                                dragOffset = 0f
                                                            },
                                                            onDrag = { change, amount ->
                                                                change.consume()
                                                                dragOffset += amount.y
                                                            },
                                                        )
                                                    },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    imageVector = MusicIcons.DragHandle,
                                                    contentDescription = "拖动排序",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        .copy(alpha = 0.6f),
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                            IconButton(
                                                onClick = { onRemove(item.index) },
                                                modifier = Modifier.size(48.dp),
                                            ) {
                                                Icon(
                                                    imageVector = MusicIcons.Trash,
                                                    contentDescription = "移除",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        .copy(alpha = 0.7f),
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
