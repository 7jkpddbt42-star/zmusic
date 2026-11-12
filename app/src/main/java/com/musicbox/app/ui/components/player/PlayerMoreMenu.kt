package com.musicbox.app.ui.components.player

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.musicbox.app.data.BackgroundSpec
import com.musicbox.app.player.DownloadState
import com.musicbox.app.ui.components.GlassPanel
import com.musicbox.app.ui.components.MusicIcons
import com.musicbox.app.ui.components.liquidglass.GlassAmbientBackdrop
import com.musicbox.app.ui.components.liquidglass.LocalGlassBackdrop
import com.musicbox.app.ui.components.liquidglass.glassBackdropSource
import com.musicbox.app.ui.components.liquidglass.rememberGlassBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 面板滑入时长，与队列抽屉一致。 */
private const val MORE_SLIDE_IN_MS = 340

/** 面板收起时长：比进场略快，和队列抽屉一致。 */
private const val MORE_SLIDE_OUT_MS = 260

/** 打开前的预热帧数：先让背景源录几帧，玻璃滑入的第一帧就有折射。 */
private const val MORE_BACKDROP_WARMUP_FRAMES = 2

/** 当前歌曲的下载入口：[state] 来自 [com.musicbox.app.player.DownloadStates]，[onClick] 开始 / 取消下载。 */
data class DownloadAction(
    val state: StateFlow<DownloadState?>,
    val onClick: () -> Unit,
)

/** 下载按钮的状态由播放页统一注入，[PlayerMoreSheet] 直接读取，不需要每个风格各自接线。 */
val LocalDownloadAction = staticCompositionLocalOf<DownloadAction?> { null }

/** 打开「更多」面板的入口；由 [com.musicbox.app.ui.player.PlayerScreen] 注入。 */
val LocalOpenPlayerMore = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * 播放页右上角的「•••」。
 *
 * 只负责打开面板，面板本体由播放页宿主统一渲染，所有风格共用同一个面板。
 */
@Composable
fun PlayerMoreMenu(
    foreground: Color,
    modifier: Modifier = Modifier,
) {
    val open = LocalOpenPlayerMore.current ?: return
    IconButton(onClick = open, modifier = modifier.size(48.dp)) {
        Icon(
            imageVector = MusicIcons.More,
            contentDescription = "更多",
            tint = foreground.copy(alpha = 0.86f),
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * 「更多」面板：下载、倍速、播放页样式。
 *
 * 与队列抽屉同一种液态玻璃底部面板：自下而上滑入、点击遮罩收起，
 * 打开前空跑两帧让背景源完成首帧录制，玻璃进场的第一帧就有真实折射。
 */
@Composable
fun PlayerMoreSheet(
    speed: Float,
    background: BackgroundSpec,
    artworkUrl: String?,
    onSpeedChange: (Float) -> Unit,
    onOpenStyleSwitcher: () -> Unit,
    onDismiss: () -> Unit,
) {
    val download = LocalDownloadAction.current
    val fallbackState = remember { MutableStateFlow<DownloadState?>(null) }
    val downloadState by (download?.state ?: fallbackState).collectAsState()
    val currentPercent = (speed * 100).roundToInt()

    // Dialog 是独立窗口，采样不到播放页图层，这里在窗口内重建一份背景源
    val backdrop = rememberGlassBackdrop()
    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 遮罩与面板同时进场、同时结束，收起瞬间不会留下一点暗色
    val scrimAlpha by animateFloatAsState(
        targetValue = if (shown) 0.50f else 0f,
        animationSpec = tween(if (shown) MORE_SLIDE_IN_MS else MORE_SLIDE_OUT_MS),
        label = "moreScrim",
    )

    LaunchedEffect(Unit) {
        repeat(MORE_BACKDROP_WARMUP_FRAMES) { withFrameNanos { } }
        shown = true
    }

    // 先播收起动画，再关闭窗口；需要接着做的事（下载、换风格）等面板落下后再执行，
    // 避免两个窗口叠在一起闪一下。
    val requestClose: (() -> Unit) -> Unit = { action ->
        if (!closing) {
            closing = true
            scope.launch {
                shown = false
                delay(MORE_SLIDE_OUT_MS.toLong())
                onDismiss()
                action()
            }
        }
    }

    Dialog(
        onDismissRequest = { requestClose {} },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 背景源层：真实背景 + 遮罩，供底部玻璃面板逐帧采样
            GlassAmbientBackdrop(
                background = background,
                artworkUrl = artworkUrl,
                // 黑色遮罩：压暗模糊封面，白色状态栏图标可读，浅色面板成为视觉焦点
                scrim = Color.Black.copy(alpha = scrimAlpha),
                modifier = Modifier.fillMaxSize().glassBackdropSource(backdrop),
            )

            AnimatedVisibility(
                visible = shown,
                enter = slideInVertically(
                    initialOffsetY = { height -> height },
                    animationSpec = tween(MORE_SLIDE_IN_MS, easing = FastOutSlowInEasing),
                ),
                exit = slideOutVertically(
                    targetOffsetY = { height -> height },
                    animationSpec = tween(MORE_SLIDE_OUT_MS, easing = FastOutLinearInEasing),
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 点击面板以外的区域关闭
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { requestClose {} },
                            ),
                    )

                    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                        GlassPanel(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                            // 面板比背景明确亮一层：模糊封面下也有「一张纸」的实体感，
                            // 8% 纯白在灰调背景上会和背景糊在一起
                            tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding(),
                            ) {
                                // 抽屉把手
                                Box(
                                    modifier = Modifier
                                        .padding(top = 10.dp)
                                        .align(Alignment.CenterHorizontally)
                                        .size(width = 40.dp, height = 4.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                .copy(alpha = 0.35f),
                                        ),
                                )

                                Text(
                                    text = "更多",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 20.dp, top = 14.dp),
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                if (download != null) {
                                    MoreRow(
                                        icon = if (downloadState == DownloadState.Done) {
                                            MusicIcons.DownloadDone
                                        } else {
                                            MusicIcons.Download
                                        },
                                        label = downloadMenuLabel(downloadState),
                                        onClick = { requestClose(download.onClick) },
                                    )
                                }

                                Text(
                                    text = "倍速",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 8.dp),
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                        .padding(3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    SPEED_OPTIONS.forEach { option ->
                                        val selected = (option * 100).roundToInt() == currentPercent
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(11.dp))
                                                .background(
                                                    if (selected) {
                                                        MaterialTheme.colorScheme.primary
                                                            .copy(alpha = 0.16f)
                                                    } else {
                                                        Color.Transparent
                                                    },
                                                )
                                                .clickable(
                                                    interactionSource = remember {
                                                        MutableInteractionSource()
                                                    },
                                                    indication = null,
                                                    onClick = {
                                                        requestClose {
                                                            onSpeedChange(option)
                                                        }
                                                    },
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = speedLabel(option),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (selected) {
                                                    FontWeight.SemiBold
                                                } else {
                                                    FontWeight.Normal
                                                },
                                                color = if (selected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                MoreRow(
                                    label = "播放页样式",
                                    trailing = true,
                                    onClick = { requestClose(onOpenStyleSwitcher) },
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 面板里的一行：可选前置图标 + 文案，整行可点；[trailing] 为 true 时右侧带箭头。 */
@Composable
private fun MoreRow(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    trailing: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (trailing) {
            Icon(
                imageVector = MusicIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 倍速展示文案：1.0 →「1x」，1.25 →「1.25x」。 */
private fun speedLabel(speed: Float): String {
    val text = "%.2f".format(speed).trimEnd('0').trimEnd('.')
    return "${text}x"
}

/** 下载项文案：同一套说法贯穿菜单与下载列表。 */
private fun downloadMenuLabel(state: DownloadState?): String = when (state) {
    is DownloadState.InProgress -> "下载中"
    is DownloadState.Paused -> "继续下载"
    DownloadState.Done -> "已下载"
    is DownloadState.Failed -> "重试下载"
    null -> "下载到本机"
}

/** 可选倍速档位。 */
private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
