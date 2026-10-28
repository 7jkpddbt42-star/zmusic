package com.musicbox.app.ui.styles

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicbox.app.data.BackgroundSpec
import com.musicbox.app.player.PositionSource
import com.musicbox.app.ui.components.player.LyricPreviewPill
import com.musicbox.app.ui.components.player.MediaHeader
import com.musicbox.app.ui.components.player.PlayerArtwork
import com.musicbox.app.ui.components.player.PlayerBackdrop
import com.musicbox.app.ui.components.player.ProgressSection
import com.musicbox.app.ui.components.player.ProgressTimePlacement
import com.musicbox.app.ui.components.player.SecondaryActions
import com.musicbox.app.ui.components.player.TransportControls
import com.musicbox.app.ui.components.player.formatProgressTime
import com.musicbox.app.ui.player.PlaybackUiState
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * GESTURE：手势优先。
 *
 * 页面默认只留封面与标题，轻触封面显隐控制栏（淡入淡出）；
 * 左半屏纵向拖动调亮度、右半屏纵向拖动调音量，封面横向拖动预览并跳转进度。
 * 三种手势都会在顶部弹出即时数值提示。
 */
@Composable
fun GesturePlayerStyle(
    state: PlaybackUiState,
    source: PositionSource,
    background: BackgroundSpec,
    modeLabel: String,
    isLiked: Boolean,
    lyricLine: String,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
    lyricTranslation: String = "",
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val maxVolume = remember(audioManager) {
        audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var hint by remember { mutableStateOf<String?>(null) }
    var brightness by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it > 0f } ?: 0.5f,
        )
    }

    // 拖动开始时重新读取系统当前值，避免用物理键调过音量后手势从旧值起跳。
    val readBrightness: () -> Float = {
        activity?.window?.attributes?.screenBrightness?.takeIf { it > 0f } ?: brightness
    }
    val readVolume: () -> Float = {
        (audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0).toFloat() / maxVolume
    }
    val applyBrightness: (Float) -> Unit = { next ->
        brightness = next
        activity?.window?.let { window ->
            val attrs = window.attributes
            attrs.screenBrightness = next
            window.attributes = attrs
        }
    }
    val applyVolume: (Float) -> Unit = { next ->
        audioManager?.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            (next * maxVolume).roundToInt(),
            0,
        )
    }

    // 空白区域：轻触显隐控制栏，拖动按方向锁定为进度 / 亮度 / 音量。
    val levelGestures: Modifier = remember(state.durationMs) {
        Modifier.seekLevelAndTapGestures(
            durationMs = state.durationMs,
            basePositionMs = { source.positionMs() },
            startBrightness = readBrightness,
            startVolume = readVolume,
            onBrightness = applyBrightness,
            onVolume = applyVolume,
            onSeekCommit = { actions.onSeek(it) },
            onHint = { hint = it },
            onTap = { controlsVisible = !controlsVisible },
        )
    }
    // 封面区域：同样的三向拖动；轻触交给封面自身的 onClick，避免二次切换。
    val coverGestures: Modifier = remember(state.durationMs) {
        Modifier.seekLevelAndTapGestures(
            durationMs = state.durationMs,
            basePositionMs = { source.positionMs() },
            startBrightness = readBrightness,
            startVolume = readVolume,
            onBrightness = applyBrightness,
            onVolume = applyVolume,
            onSeekCommit = { actions.onSeek(it) },
            onHint = { hint = it },
            onTap = {},
        )
    }

    LaunchedEffect(hint) {
        if (hint != null) {
            kotlinx.coroutines.delay(1000)
            hint = null
        }
    }

    PlayerBackdrop(
        background = background,
        artworkUrl = state.artworkUrl,
        source = source,
        isPlaying = state.isPlaying,
        scrim = 0.52f,
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            // 底层手势区：空白处轻触显隐控制栏，横向拖进度、左半屏竖向调亮度、右半屏竖向调音量。
            // 放在内容之下，按钮等可点击元素仍然优先响应。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(levelGestures),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(tween(240)),
                    exit = fadeOut(tween(180)),
                ) {
                    PlayerTopBar(
                        foreground = Color.White,
                        onCollapse = actions.onCollapse,
                        onOpenQueue = actions.onOpenQueue,
                    )
                }

                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.82f)
                            .aspectRatio(1f)
                            .then(coverGestures),
                    ) {
                        PlayerArtwork(
                            artworkUrl = state.artworkUrl,
                            onArtworkLoaded = actions.onArtworkLoaded,
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(28.dp),
                            haloColor = MaterialTheme.colorScheme.primary,
                            onClick = { controlsVisible = !controlsVisible },
                        )
                    }
                }

                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(tween(280)),
                    exit = fadeOut(tween(200)),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MediaHeader(
                            title = state.title.orEmpty(),
                            subtitle = state.artist.orEmpty(),
                            foreground = Color.White,
                            isTrial = state.isTrial,
                            titleSize = 19.sp,
                            subtitleSize = 13.sp,
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        LyricPreviewPill(
                            lyricLine = lyricLine,
                            onClick = actions.onOpenLyric,
                            foreground = Color.White,
                            translation = lyricTranslation,
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        ProgressSection(
                            source = source,
                            durationMs = state.durationMs,
                            positionMs = state.positionMs,
                            onSeek = actions.onSeek,
                            foreground = Color.White,
                            trackHeight = 2.dp,
                            thumbRadius = 4.dp,
                            timePlacement = ProgressTimePlacement.Inline,
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        TransportControls(
                            isPlaying = state.isPlaying,
                            onTogglePlay = actions.onTogglePlay,
                            onPrevious = actions.onPrevious,
                            onNext = actions.onNext,
                            foreground = Color.White,
                            playBackground = Color.White,
                            playForeground = Color.Black,
                            playSize = 64.dp,
                            playIconSize = 30.dp,
                            skipButtonSize = 48.dp,
                            skipIconSize = 28.dp,
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
                            foreground = Color.White.copy(alpha = 0.82f),
                            iconSize = 18.dp,
                            itemSize = 38.dp,
                            spacing = 12.dp,
                            showSpeed = false,
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            AnimatedVisibility(
                visible = hint != null,
                enter = fadeIn(tween(140)),
                exit = fadeOut(tween(180)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp),
            ) {
                Text(
                    text = hint.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * 单一手势检测器同时处理点击与三种拖动：
 * 未超过触摸阈值就抬手 → 点击；超过阈值后按首次位移方向锁定，
 * 横向 → 预览/提交进度，竖向且落点在左半屏 → 亮度，右半屏 → 音量。
 * 不能把 `detectTapGestures` 与 `detectDragGestures` 串在同一节点链上，
 * 点击检测器会吞掉按压事件，导致竖向拖动永远不触发。
 */
private fun Modifier.seekLevelAndTapGestures(
    durationMs: Long,
    basePositionMs: () -> Long,
    startBrightness: () -> Float,
    startVolume: () -> Float,
    onBrightness: (Float) -> Unit,
    onVolume: (Float) -> Unit,
    onSeekCommit: (Long) -> Unit,
    onHint: (String?) -> Unit,
    onTap: () -> Unit,
): Modifier = pointerInput(durationMs) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // 子节点（封面点击）已处理按压时，不再重复触发点击
        val downHandled = down.isConsumed
        val slop = viewConfiguration.touchSlop
        var mode = 0 // 0 未锁定，1 进度，2 亮度，3 音量
        var accX = 0f
        var accY = 0f
        var dragging = false
        var preview = -1L
        val base = basePositionMs()
        val startBri = startBrightness()
        val startVol = startVolume()

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break

            if (change.changedToUpIgnoreConsumed()) {
                if (mode == 1 && preview >= 0L) onSeekCommit(preview)
                if (!dragging && !downHandled) onTap()
                // 提示保留 1s 由 LaunchedEffect 自动淡出，抬手时不立刻清空
                break
            }
            // 拖动开始后被其他手势接管则放弃
            if (!dragging && change.isConsumed) break

            accX += change.positionChange().x
            accY += change.positionChange().y

            if (!dragging && (abs(accX) > slop || abs(accY) > slop)) {
                dragging = true
                mode = when {
                    abs(accX) >= abs(accY) -> 1
                    down.position.x < size.width / 2f -> 2
                    else -> 3
                }
            }
            if (!dragging) continue

            change.consume()
            when (mode) {
                1 -> if (durationMs > 0L) {
                    preview = (base + (accX / size.width * durationMs).toLong())
                        .coerceIn(0L, durationMs)
                    onHint("进度 ${formatProgressTime(preview)}")
                }
                2 -> {
                    val next = (startBri - accY / size.height).coerceIn(0.05f, 1f)
                    onBrightness(next)
                    onHint("亮度 ${(next * 100).roundToInt()}%")
                }
                3 -> {
                    val next = (startVol - accY / size.height).coerceIn(0f, 1f)
                    onVolume(next)
                    onHint("音量 ${(next * 100).roundToInt()}%")
                }
            }
        }
    }
}
