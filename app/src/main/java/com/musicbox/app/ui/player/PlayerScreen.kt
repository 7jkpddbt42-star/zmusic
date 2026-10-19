package com.musicbox.app.ui.player

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicbox.app.player.PositionSource
import com.musicbox.app.ui.components.BlurredBackdrop
import com.musicbox.app.ui.components.GlassCard
import com.musicbox.app.ui.components.ParticleField
import com.musicbox.app.ui.components.VinylRecord
import com.musicbox.app.ui.components.WaveformSeekBar
import com.musicbox.app.ui.components.flowingLight
import com.musicbox.app.ui.components.rememberFlowProgress

/**
 * 全屏播放页。
 *
 * 视觉分层（自下而上）：玻璃背景层（封面模糊 + 遮罩）→ 粒子层 → 透明顶栏 → 旋转黑胶唱片 →
 * 信息区玻璃卡片 → 控制区玻璃卡片。
 * 唱片本身保持清晰（不参与背景模糊），[VinylRecord] 的 `onBitmap` 继续驱动封面取色。
 *
 * 进度条为 [WaveformSeekBar]（确定性伪波形，非真实频谱分析），点击/拖动可跳转。
 *
 * 沉浸模式（[immersive]）：隐藏顶栏与「歌词/收藏」小按钮行，信息区改为唱片下方的纯文本，
 * 仅保留极简控制卡片；长按播放键切换进出。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlayerScreen(
    state: PlaybackUiState,
    source: PositionSource,
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
    // 返回键：沉浸模式下先退出沉浸（与"再次长按或返回退出"一致），否则收起播放页
    BackHandler {
        if (immersive) onToggleImmersive() else onCollapse()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // 最底层：封面放大 → 模糊（API 31+ 真模糊 / 26–30 位图降采样近似）→ 遮罩
        BlurredBackdrop(url = state.artworkUrl, modifier = Modifier.fillMaxSize())

        // 粒子层：在背景之上、玻璃卡片之下。纯装饰，不拦截点击；暂停即静止
        ParticleField(
            source = source,
            isPlaying = state.isPlaying,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 顶栏：沉浸模式下隐藏（只留唱片 + 极简控制）
            if (!immersive) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onCollapse) { Text("收起") }
                    Text(
                        text = "正在播放",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOpenQueue) { Text("队列") }
                }
                Spacer(modifier = Modifier.height(24.dp))
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 旋转黑胶唱片：点封面进歌词
            VinylRecord(
                artworkUrl = state.artworkUrl,
                isPlaying = state.isPlaying,
                onBitmap = onArtworkLoaded,
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .aspectRatio(1f)
                    .clickable(onClick = onOpenLyric),
            )

            // 「歌词 / 收藏」小按钮行：沉浸模式下隐藏
            if (!immersive) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onOpenLyric) { Text("歌词") }
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite
                            else Icons.Filled.FavoriteBorder,
                            contentDescription = "收藏",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (immersive) {
                // 沉浸：唱片下方直接以较大字号纯文本显示标题 / 歌手，不用玻璃卡片
                Text(
                    text = state.title.orEmpty(),
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.artist.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // 信息区：标题 / 歌手 / 试听标注 —— 玻璃卡片
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = state.title.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    // 标题下方 3dp 流光下划线
                    FlowingUnderline(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.artist.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.isTrial) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "试听片段 · 登录解锁完整版",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 拖动预览比例：非 null 时时间行显示拖动目标，抬手后回到真实播放位置
            var scrubFraction by remember { mutableStateOf<Float?>(null) }

            // 控制区：波形进度条 + 时间 + 控制按钮行 —— 玻璃卡片
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            ) {
                WaveformSeekBar(
                    source = source,
                    songId = state.currentSongId,
                    durationMs = state.durationMs,
                    onSeek = {
                        onSeek(it)
                        scrubFraction = null
                    },
                    onScrub = { scrubFraction = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatTime(
                            scrubFraction?.let { (it * state.durationMs).toLong() } ?: state.positionMs,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(text = formatTime(state.durationMs), style = MaterialTheme.typography.labelSmall)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onCycleMode) { Text(modeLabel) }
                    TextButton(onClick = onPrevious) {
                        Text("⏮", style = MaterialTheme.typography.headlineSmall)
                    }
                    // 播放键：短按播放/暂停，长按进出沉浸模式
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 58.dp, minHeight = 48.dp)
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = LocalIndication.current,
                                onClick = onTogglePlay,
                                onLongClick = onToggleImmersive,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (state.isPlaying) "⏸" else "▶",
                            style = MaterialTheme.typography.displaySmall,
                        )
                    }
                    TextButton(onClick = onNext) {
                        Text("⏭", style = MaterialTheme.typography.headlineSmall)
                    }
                    // 占位保持左右对称，避免按钮横向跳动
                    TextButton(onClick = {}, enabled = false) { Text(" ") }
                }
            }

            if (state.isBuffering) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "缓冲中…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (immersive) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "长按播放键退出沉浸",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 流光下划线：3dp 高，读取 [rememberFlowProgress] 后交给 [flowingLight] 绘制。
 * 把进度读取收敛在本 Composable 内，避免每帧重组的范围扩散到整个播放页。
 */
@Composable
private fun FlowingUnderline(color: Color, modifier: Modifier = Modifier) {
    val progress = rememberFlowProgress()
    Box(
        modifier = modifier
            .height(3.dp)
            .flowingLight(progress = progress, colors = listOf(color, Color.Transparent)),
    )
}

/** 毫秒 → m:ss。 */
internal fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
