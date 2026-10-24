package com.musicbox.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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

/** 面板淡出时长；与 fadeOut 动画保持一致后再真正关闭 Dialog。 */
private const val QUEUE_FADE_OUT_MS = 180L

/**
 * 队列面板：查看 / 删除 / 切换播放模式。
 *
 * 视觉与设置页一致：窗口内自带真实背景源（视频/图片/封面），底部面板是一块液态玻璃，
 * 采样这层背景，模糊与折射都可见。打开与关闭都用淡入淡出，不做滑入滑出。
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

    LaunchedEffect(Unit) { shown = true }

    // 先播放淡出动画，再真正关闭窗口
    val requestClose: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                shown = false
                delay(QUEUE_FADE_OUT_MS)
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
            // 背景源层：只放真实背景，玻璃面板放在内容层，避免自采样鬼影
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
                    // 点击面板以外的区域关闭
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
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
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
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                                    contentPadding = PaddingValues(vertical = 8.dp),
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
