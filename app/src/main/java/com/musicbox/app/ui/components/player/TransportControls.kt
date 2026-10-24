package com.musicbox.app.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musicbox.app.ui.components.MusicIcons

/**
 * 上一首 / 播放暂停 / 下一首。
 *
 * 尺寸、间距、颜色、播放按钮容器全部由参数控制：
 * - 普通风格用默认圆形白色播放键；
 * - LIQUID_GLASS 通过 [playContainer] 传入 GlassSurface，播放键容器使用 lens；
 * - VINYL / RETRO_CASSETTE 可缩小尺寸并下移位置。
 *
 * 左右两侧的空白 weight 保证播放键严格居中，不随 leading/trailing 宽度偏移。
 */
@Composable
fun TransportControls(
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    foreground: Color = Color.White,
    playBackground: Color = Color.White,
    playForeground: Color = Color.Black,
    playShape: Shape = CircleShape,
    playSize: Dp = 72.dp,
    playIconSize: Dp = 34.dp,
    skipButtonSize: Dp = 52.dp,
    skipIconSize: Dp = 32.dp,
    skipBackground: Color = Color.Transparent,
    spacing: Dp = 12.dp,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    playContainer: @Composable (content: @Composable () -> Unit) -> Unit = { content ->
        Box(
            modifier = Modifier
                .size(playSize)
                .clip(playShape)
                .background(playBackground),
            contentAlignment = Alignment.Center,
            content = { content() },
        )
    },
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (leading != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    content = leading,
                )
            }
        }

        Box(
            modifier = Modifier
                .size(skipButtonSize)
                .clip(CircleShape)
                .background(skipBackground, CircleShape)
                .clickable(onClick = onPrevious),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MusicIcons.SkipPrevious,
                contentDescription = "上一首",
                tint = foreground,
                modifier = Modifier.size(skipIconSize),
            )
        }

        Spacer(modifier = Modifier.width(spacing))

        Box(
            modifier = Modifier
                .size(playSize)
                .clip(playShape)
                .clickable(onClick = onTogglePlay),
            contentAlignment = Alignment.Center,
        ) {
            playContainer {
                Icon(
                    imageVector = if (isPlaying) MusicIcons.Pause else MusicIcons.Play,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = playForeground,
                    modifier = Modifier.size(playIconSize),
                )
            }
        }

        Spacer(modifier = Modifier.width(spacing))

        Box(
            modifier = Modifier
                .size(skipButtonSize)
                .clip(CircleShape)
                .background(skipBackground, CircleShape)
                .clickable(onClick = onNext),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MusicIcons.SkipNext,
                contentDescription = "下一首",
                tint = foreground,
                modifier = Modifier.size(skipIconSize),
            )
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (trailing != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    content = trailing,
                )
            }
        }
    }
}
