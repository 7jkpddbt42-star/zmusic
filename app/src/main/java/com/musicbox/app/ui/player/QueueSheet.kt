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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.musicbox.app.data.BackgroundSpec
import com.musicbox.app.ui.components.GlassPanel
import com.musicbox.app.ui.components.GlassPill
import com.musicbox.app.ui.components.MusicIcons
import com.musicbox.app.ui.components.liquidglass.GlassAmbientBackdrop
import com.musicbox.app.ui.components.liquidglass.LocalGlassBackdrop
import com.musicbox.app.ui.components.liquidglass.glassBackdropSource
import com.musicbox.app.ui.components.liquidglass.rememberGlassBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 抽屉打开：自下而上滑入。 */
private const val QUEUE_SLIDE_IN_MS = 340

/** 抽屉收起：下滑离场，比进场略快。 */
private const val QUEUE_SLIDE_OUT_MS = 260

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
    onCycleMode: () -> Unit,
) {
    // Dialog 是独立窗口，无法采样播放页的背景图层，这里在窗口内自建一份背景源
    val backdrop = rememberGlassBackdrop()
    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
                                        Text(
                                            text = "模式：$modeLabel",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }

                                LazyColumn(
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
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    if (item.isCurrent) primary.copy(alpha = 0.10f)
                                                    else Color.Transparent,
                                                )
                                                .clickable { onSelect(item.index) }
                                                .padding(
                                                    start = 12.dp,
                                                    top = 10.dp,
                                                    bottom = 10.dp,
                                                    end = 4.dp,
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
                                            IconButton(
                                                onClick = { onRemove(item.index) },
                                                modifier = Modifier.size(40.dp),
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
