package com.musicbox.app.ui.components.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicbox.app.player.DownloadState
import com.musicbox.app.ui.components.MusicIcons
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/** 当前歌曲的下载入口：[state] 来自 [com.musicbox.app.player.DownloadStates]，[onClick] 开始 / 取消下载。 */
data class DownloadAction(
    val state: StateFlow<DownloadState?>,
    val onClick: () -> Unit,
)

/**
 * 下载按钮的状态由播放页统一注入，9 种风格里的 [SecondaryActions] 直接读取，
 * 不需要每个风格各自接线（风格文件只关心布局，不关心下载这类业务动作）。
 */
val LocalDownloadAction = staticCompositionLocalOf<DownloadAction?> { null }

/**
 * 次级动作：歌词、播放列表、倍速、更多。
 *
 * - 歌词 / 播放列表走真实页面跳转；
 * - 倍速点开一个下拉菜单，选择立即作用到播放器，并回写偏好（重启后沿用）；
 * - 更多打开播放页风格面板（StyleSwitcher）。
 */
@Composable
fun SecondaryActions(
    onOpenLyric: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenStyleSwitcher: () -> Unit,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier,
    foreground: Color = Color.White,
    iconSize: Dp = 20.dp,
    itemSize: Dp = 44.dp,
    spacing: Dp = 6.dp,
    showLabels: Boolean = false,
    showSpeed: Boolean = true,
    showMore: Boolean = true,
    speed: Float = 1f,
    onSpeedChange: (Float) -> Unit = {},
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SecondaryActionItem(
            icon = MusicIcons.Lyrics,
            label = "歌词",
            foreground = foreground,
            iconSize = iconSize,
            itemSize = itemSize,
            showLabel = showLabels,
            onClick = onOpenLyric,
        )
        SecondaryActionItem(
            icon = MusicIcons.Queue,
            label = "队列",
            foreground = foreground,
            iconSize = iconSize,
            itemSize = itemSize,
            showLabel = showLabels,
            onClick = onOpenQueue,
        )
        // 下载按钮只在播放页注入了动作时出现（播放页之外的复用场景不显示）
        LocalDownloadAction.current?.let { download ->
            DownloadItem(
                action = download,
                foreground = foreground,
                iconSize = iconSize,
                itemSize = itemSize,
                showLabel = showLabels,
            )
        }
        if (showSpeed) {
            // 倍速菜单挂在图标下方：不新建窗口，也就不需要额外的玻璃背景源
            var menuOpen by remember { mutableStateOf(false) }
            val currentPercent = (speed * 100).roundToInt()
            Box {
                SecondaryActionItem(
                    icon = MusicIcons.Speed,
                    label = if (currentPercent == 100) "倍速" else speedLabel(speed),
                    foreground = foreground,
                    iconSize = iconSize,
                    itemSize = itemSize,
                    showLabel = showLabels,
                    onClick = { menuOpen = true },
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    SPEED_OPTIONS.forEach { option ->
                        val selected = (option * 100).roundToInt() == currentPercent
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (option == 1f) "1x · 原速" else speedLabel(option),
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onSpeedChange(option)
                            },
                        )
                    }
                }
            }
        }
        if (showMore) {
            SecondaryActionItem(
                icon = MusicIcons.More,
                label = "更多",
                foreground = foreground,
                iconSize = iconSize,
                itemSize = itemSize,
                showLabel = showLabels,
                onClick = onOpenStyleSwitcher,
            )
        }
    }
}

/** 倍速展示文案：1.0 →「1x」，1.25 →「1.25x」。 */
private fun speedLabel(speed: Float): String {
    val text = "%.2f".format(speed).trimEnd('0').trimEnd('.')
    return "${text}x"
}

/** 可选倍速档位。 */
private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** 下载按钮：下载中显示环形进度，已下载显示对勾，失败可重试。 */
@Composable
private fun DownloadItem(
    action: DownloadAction,
    foreground: Color,
    iconSize: Dp,
    itemSize: Dp,
    showLabel: Boolean,
) {
    val state by action.state.collectAsState()
    val label = when (state) {
        is DownloadState.InProgress -> "下载中"
        is DownloadState.Paused -> "继续下载"
        DownloadState.Done -> "已下载"
        is DownloadState.Failed -> "重试"
        null -> "下载"
    }
    val icon = if (state == DownloadState.Done) MusicIcons.DownloadDone else MusicIcons.Download
    val tint = when (state) {
        DownloadState.Done -> Color(0xFF6FD8A6)
        is DownloadState.Failed -> Color(0xFFFF8A8A)
        is DownloadState.Paused -> Color(0xFFFFC66D)
        else -> foreground.copy(alpha = 0.82f)
    }
    // 暂停时保留进度环，并在底部加一条短横线表示「已停住」
    val ringProgress = when (val current = state) {
        is DownloadState.InProgress -> current.progress
        is DownloadState.Paused -> current.progress
        else -> null
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(itemSize)
                .clip(CircleShape)
                .clickable(onClick = action.onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(iconSize),
            )
            if (ringProgress != null) {
                // 进度环压在图标外圈，既不挡住图标，也能表达具体进度
                CircularProgressIndicator(
                    progress = { if (ringProgress < 0f) 0f else ringProgress },
                    modifier = Modifier.size(itemSize - 4.dp),
                    strokeWidth = 1.6.dp,
                    color = tint.copy(alpha = 0.75f),
                    trackColor = Color.Transparent,
                )
                if (ringProgress < 0f) {
                    // 拿不到总长度时退回不确定进度（会自己转）
                    CircularProgressIndicator(
                        modifier = Modifier.size(itemSize - 4.dp),
                        strokeWidth = 1.6.dp,
                        color = foreground.copy(alpha = 0.55f),
                    )
                }
            }
        }
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = foreground.copy(alpha = 0.62f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SecondaryActionItem(
    icon: ImageVector,
    label: String,
    foreground: Color,
    iconSize: Dp,
    itemSize: Dp,
    showLabel: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(itemSize)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = foreground.copy(alpha = 0.82f),
                modifier = Modifier.size(iconSize),
            )
        }
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = foreground.copy(alpha = 0.62f),
                maxLines = 1,
            )
        }
    }
}
