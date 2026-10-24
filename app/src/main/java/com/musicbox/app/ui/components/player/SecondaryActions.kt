package com.musicbox.app.ui.components.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicbox.app.ui.components.MusicIcons

/**
 * 次级动作：歌词、播放列表、倍速、更多。
 *
 * - 歌词 / 播放列表走真实页面跳转；
 * - 倍速当前没有接入播放核心（不修改现有播放逻辑），点击给出明确提示；
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
            SecondaryActionItem(
                icon = MusicIcons.Speed,
                label = "倍速",
                foreground = foreground,
                iconSize = iconSize,
                itemSize = itemSize,
                showLabel = showLabels,
                onClick = { onNotify("倍速调节暂未开放") },
            )
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
