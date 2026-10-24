package com.musicbox.app.ui.components.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.musicbox.app.data.BackgroundSpec
import com.musicbox.app.data.PlayerStyle
import com.musicbox.app.ui.components.GlassPanel
import com.musicbox.app.ui.components.liquidglass.GlassAmbientBackdrop
import com.musicbox.app.ui.components.liquidglass.LocalGlassBackdrop
import com.musicbox.app.ui.components.liquidglass.glassBackdropSource
import com.musicbox.app.ui.components.liquidglass.rememberGlassBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 面板淡出时长；与 fadeOut 动画保持一致后再真正关闭 Dialog。 */
private const val STYLE_FADE_OUT_MS = 180L

/**
 * 播放页风格切换面板（底部浮层，淡入淡出）。
 *
 * 与其他新增浮层统一为液态玻璃：Dialog 窗口内自建真实背景源（封面/图片/视频），
 * 底部面板采样这层背景，模糊与折射可见。点击某项立即回调 [onSelect]，
 * 由上层写入 SettingsRepository 并触发重组；不重建 Activity、不打断播放。
 */
@Composable
fun StyleSwitcher(
    visible: Boolean,
    current: PlayerStyle,
    background: BackgroundSpec,
    artworkUrl: String?,
    onSelect: (PlayerStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    // Dialog 独立窗口：自建背景源，避免采样不到播放页的图层
    val backdrop = rememberGlassBackdrop()
    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { shown = true }

    val requestClose: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                shown = false
                delay(STYLE_FADE_OUT_MS)
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
            // 背景源层：真实背景 + 遮罩，供底部玻璃面板逐帧采样
            GlassAmbientBackdrop(
                background = background,
                artworkUrl = artworkUrl,
                scrim = MaterialTheme.colorScheme.surface.copy(alpha = 0.46f),
                modifier = Modifier.fillMaxSize().glassBackdropSource(backdrop),
            )

            AnimatedVisibility(
                visible = shown,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(180)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
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
                                .navigationBarsPadding()
                                .padding(bottom = 10.dp),
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp, bottom = 10.dp),
                            ) {
                                // 顶部拖动提示条（仅视觉暗示，面板本身不做滑入滑出）
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .size(width = 36.dp, height = 4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)),
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "播放页风格",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                )
                                Text(
                                    text = "切换立即生效，不会中断播放或丢失进度",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                ) {
                                    items(PlayerStyle.values().toList()) { style ->
                                        StyleRow(
                                            style = style,
                                            selected = style == current,
                                            // 选中即收起面板：切换后立刻能看到整套布局，不用再手动关
                                            onSelect = {
                                                onSelect(style)
                                                requestClose()
                                            },
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

@Composable
private fun StyleRow(
    style: PlayerStyle,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) primary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = style.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = style.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (selected) {
            Text(
                text = "当前",
                style = MaterialTheme.typography.labelMedium,
                color = primary,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}
