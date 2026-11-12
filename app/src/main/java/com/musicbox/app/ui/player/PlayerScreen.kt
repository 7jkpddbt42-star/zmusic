package com.musicbox.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.musicbox.app.data.BackgroundSpec
import com.musicbox.app.data.PlayerStyle
import com.musicbox.app.player.PositionSource
import com.musicbox.app.ui.components.player.DownloadAction
import com.musicbox.app.ui.components.player.LocalDownloadAction
import com.musicbox.app.ui.components.player.LocalOpenPlayerMore
import com.musicbox.app.ui.components.player.PlayerMoreSheet
import com.musicbox.app.ui.components.player.StyleSwitcher
import com.musicbox.app.ui.styles.FrostedPlayerStyle
import com.musicbox.app.ui.styles.GesturePlayerStyle
import com.musicbox.app.ui.styles.LiquidGlassPlayerStyle
import com.musicbox.app.ui.styles.MinimalPlayerStyle
import com.musicbox.app.ui.styles.PlayerActions
import com.musicbox.app.ui.styles.RetroCassettePlayerStyle
import com.musicbox.app.ui.styles.SplitViewPlayerStyle
import com.musicbox.app.ui.styles.StructuredPlayerStyle
import com.musicbox.app.ui.styles.ToolMinimalPlayerStyle
import com.musicbox.app.ui.styles.VinylPlayerStyle
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 全屏播放页：只做风格路由。
 *
 *  - 播放状态与动作由上层（MainActivity）下发，本页不持有任何播放逻辑；
 *  - 切换风格只是 [Crossfade] 换一套布局，`PositionSource` 与播放进度都不受影响；
 *  - 「更多」打开风格面板，9 种风格点击立即生效。
 */
@Composable
internal fun PlayerScreen(
    state: PlaybackUiState,
    source: PositionSource,
    background: BackgroundSpec,
    modeLabel: String,
    isLiked: Boolean,
    lyricLine: String,
    lyricTranslation: String,
    playerStyle: PlayerStyle,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
    /** 当前歌曲的下载入口；null 时不显示下载按钮。 */
    download: DownloadAction? = null,
) {
    var stylePanelVisible by remember { mutableStateOf(false) }
    var morePanelVisible by remember { mutableStateOf(false) }

    // 风格面板统一由播放页管理：任何风格点「更多」都打开同一个底部面板
    val styledActions = remember(actions) {
        actions.copy(onOpenStyleSwitcher = { stylePanelVisible = true })
    }

    BackHandler {
        if (stylePanelVisible) stylePanelVisible = false else actions.onCollapse()
    }

    // 手势：下滑收起、左右滑动切歌——播放页没有可滚动内容，整页都能接住
    val gestureScope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }
    var pageHeight by remember { mutableFloatStateOf(0f) }
    val dismissThresholdPx = with(LocalDensity.current) { 96.dp.toPx() }
    val songSwipeThresholdPx = with(LocalDensity.current) { 88.dp.toPx() }

    // 收起时保留了手指拖出来的偏移；重新展开是新的组合实例，先把位移归零
    LaunchedEffect(Unit) {
        dragX.snapTo(0f)
        dragY.snapTo(0f)
    }

    CompositionLocalProvider(
        LocalDownloadAction provides download,
        LocalOpenPlayerMore provides { morePanelVisible = true },
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .onSizeChanged { pageHeight = it.height.toFloat() }
                .graphicsLayer {
                    translationX = dragX.value
                    translationY = dragY.value
                    // 下滑越多，整页越淡、越小，跟手给出"要被收起"的反馈
                    val progress = if (pageHeight > 0f) {
                        (dragY.value / (pageHeight * 0.5f)).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    // 只留一点透明和缩放：足够表达「要被收起」，又不会让下面的
                    // 主界面透上来把整页洗灰
                    alpha = 1f - 0.16f * progress
                    val scale = 1f - 0.03f * progress
                    scaleX = scale
                    scaleY = scale
                }
                .pointerInput(Unit) {
                    var totalX = 0f
                    var totalY = 0f
                    var axis = 0 // 0 未定，1 横向，2 纵向
                    detectDragGestures(
                        onDragStart = {
                            totalX = 0f
                            totalY = 0f
                            axis = 0
                        },
                        onDrag = { change, amount ->
                            if (axis == 0) {
                                axis = when {
                                    abs(amount.x) > abs(amount.y) -> 1
                                    abs(amount.y) > abs(amount.x) -> 2
                                    else -> 0
                                }
                            }
                            when (axis) {
                                1 -> {
                                    change.consume()
                                    totalX += amount.x
                                    gestureScope.launch { dragX.snapTo(totalX) }
                                }
                                2 -> {
                                    totalY += amount.y
                                    // 只跟手下滑；上滑不做任何事，避免误触
                                    if (totalY > 0f) {
                                        change.consume()
                                        gestureScope.launch { dragY.snapTo(totalY) }
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            when (axis) {
                                2 -> {
                                    if (totalY > dismissThresholdPx) {
                                        // 只发起收起，不再自己把 dragY 推到屏幕外：
                                        // 外层播放层已经在做整层下滑，两段位移叠加会让页面
                                        // 以两倍速度闪走，还来不及保持不透明。
                                        actions.onCollapse()
                                    } else {
                                        gestureScope.launch {
                                            dragY.animateTo(0f, tween(260))
                                        }
                                    }
                                }
                                1 -> {
                                    if (totalX < -songSwipeThresholdPx) {
                                        actions.onNext()
                                    } else if (totalX > songSwipeThresholdPx) {
                                        actions.onPrevious()
                                    }
                                    gestureScope.launch {
                                        dragX.animateTo(0f, tween(260))
                                    }
                                }
                            }
                            totalX = 0f
                            totalY = 0f
                            axis = 0
                        },
                        onDragCancel = {
                            gestureScope.launch {
                                dragX.animateTo(0f, tween(260))
                                dragY.animateTo(0f, tween(260))
                            }
                            totalX = 0f
                            totalY = 0f
                            axis = 0
                        },
                    )
                },
        ) {
            Crossfade(
                targetState = playerStyle,
                animationSpec = tween(durationMillis = 320),
                label = "playerStyle",
                modifier = Modifier.fillMaxSize(),
            ) { style ->
                // 不在这里铺不透明底色：背景由外层播放层统一提供，风格只负责内容布局
                val styleModifier = Modifier.fillMaxSize()

                when (style) {
                    PlayerStyle.MINIMAL -> MinimalPlayerStyle(
                        state, source, background, modeLabel, isLiked, lyricLine, styledActions, styleModifier,
                        lyricTranslation,
                    )
                    PlayerStyle.LIQUID_GLASS -> LiquidGlassPlayerStyle(
                        state, source, background, modeLabel, isLiked, lyricLine, styledActions, styleModifier,
                        lyricTranslation,
                    )
                    PlayerStyle.STRUCTURED -> StructuredPlayerStyle(
                        state, source, background, modeLabel, isLiked, lyricLine, styledActions, styleModifier,
                    )
                    PlayerStyle.VINYL -> VinylPlayerStyle(
                        state, source, background, modeLabel, isLiked, lyricLine, styledActions, styleModifier,
                    )
                    PlayerStyle.GESTURE -> GesturePlayerStyle(
                        state, source, background, modeLabel, isLiked, lyricLine, styledActions, styleModifier,
                        lyricTranslation,
                    )
                    PlayerStyle.SPLIT_VIEW -> SplitViewPlayerStyle(
                        state, source, background, modeLabel, isLiked, lyricLine, styledActions, styleModifier,
                    )
                    PlayerStyle.RETRO_CASSETTE -> RetroCassettePlayerStyle(
                        state, source, background, modeLabel, isLiked, lyricLine, styledActions, styleModifier,
                    )
                    PlayerStyle.TOOL_MINIMAL -> ToolMinimalPlayerStyle(
                        state, source, background, modeLabel, isLiked, lyricLine, styledActions, styleModifier,
                    )
                    PlayerStyle.FROSTED -> FrostedPlayerStyle(
                        state, source, background, modeLabel, isLiked, lyricLine, styledActions, styleModifier,
                        lyricTranslation,
                    )
                }
            }

            // 菜单与队列抽屉共用同一套底部玻璃面板，滑入/收起动画一致
            if (morePanelVisible) {
                PlayerMoreSheet(
                    speed = state.speed,
                    background = background,
                    artworkUrl = state.artworkUrl,
                    onSpeedChange = actions.onSetSpeed,
                    onOpenStyleSwitcher = styledActions.onOpenStyleSwitcher,
                    onDismiss = { morePanelVisible = false },
                )
            }
        }
    }

    StyleSwitcher(
        visible = stylePanelVisible,
        current = playerStyle,
        background = background,
        artworkUrl = state.artworkUrl,
        onSelect = actions.onSelectStyle,
        onDismiss = { stylePanelVisible = false },
    )
}

/** 毫秒 → m:ss。列表页（首页 / 歌单详情 / 最近播放）共用的时长格式。 */
internal fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
