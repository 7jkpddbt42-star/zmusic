package com.musicbox.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.musicbox.app.data.BackgroundSpec
import com.musicbox.app.data.PlayerStyle
import com.musicbox.app.player.PositionSource
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
    playerStyle: PlayerStyle,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
) {
    var stylePanelVisible by remember { mutableStateOf(false) }

    // 风格面板统一由播放页管理：任何风格点「更多」都打开同一个底部面板
    val styledActions = remember(actions) {
        actions.copy(onOpenStyleSwitcher = { stylePanelVisible = true })
    }

    BackHandler {
        if (stylePanelVisible) stylePanelVisible = false else actions.onCollapse()
    }

    Crossfade(
        targetState = playerStyle,
        animationSpec = tween(durationMillis = 320),
        label = "playerStyle",
        modifier = modifier.fillMaxSize(),
    ) { style ->
        // 不在这里铺不透明底色：背景由外层播放层统一提供，风格只负责内容布局
        val styleModifier = Modifier.fillMaxSize()

        when (style) {
            PlayerStyle.MINIMAL -> MinimalPlayerStyle(
                state, source, background, modeLabel, isLiked, lyricLine, styledActions, styleModifier,
            )
            PlayerStyle.LIQUID_GLASS -> LiquidGlassPlayerStyle(
                state, source, background, modeLabel, isLiked, lyricLine, styledActions, styleModifier,
            )
            PlayerStyle.STRUCTURED -> StructuredPlayerStyle(
                state, source, background, modeLabel, isLiked, lyricLine, styledActions, styleModifier,
            )
            PlayerStyle.VINYL -> VinylPlayerStyle(
                state, source, background, modeLabel, isLiked, lyricLine, styledActions, styleModifier,
            )
            PlayerStyle.GESTURE -> GesturePlayerStyle(
                state, source, background, modeLabel, isLiked, lyricLine, styledActions, styleModifier,
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
            )
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
