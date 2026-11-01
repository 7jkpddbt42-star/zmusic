package com.musicbox.app.ui.components.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicbox.app.ui.components.MusicIcons
import kotlin.math.roundToInt

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
