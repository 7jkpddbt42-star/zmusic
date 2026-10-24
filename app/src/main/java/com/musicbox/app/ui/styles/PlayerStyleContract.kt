package com.musicbox.app.ui.styles

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicbox.app.data.PlayerStyle
import com.musicbox.app.ui.components.MusicIcons

/**
 * 播放页对播放能力的全部依赖，由 MainActivity 构造后下发。
 * 风格布局只调用这些动作，不直接接触 MediaController / PlaybackService。
 */
data class PlayerActions(
    val onCollapse: () -> Unit,
    val onTogglePlay: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onCycleMode: () -> Unit,
    val onOpenQueue: () -> Unit,
    val onOpenLyric: () -> Unit,
    val onToggleLike: () -> Unit,
    val onSelectQueueItem: (Int) -> Unit,
    val onOpenStyleSwitcher: () -> Unit,
    val onSelectStyle: (PlayerStyle) -> Unit,
    val onNotify: (String) -> Unit,
    val onArtworkLoaded: (Bitmap) -> Unit,
)

/**
 * 视觉令牌：只描述尺寸、颜色、字重，不包含布局结构。
 * 9 种风格通过覆盖这些值表达差异，共享组件不自行决定风格。
 */
@Immutable
data class PlayerTokens(
    val foreground: Color = Color.White,
    val secondaryForeground: Color = Color.White.copy(alpha = 0.68f),
    val accent: Color = Color.White,
    val titleSize: TextUnit = 20.sp,
    val subtitleSize: TextUnit = 14.sp,
    val titleWeight: FontWeight = FontWeight.SemiBold,
    val horizontalPadding: Dp = 24.dp,
    val coverCornerRadius: Dp = 22.dp,
    val progressTrackHeight: Dp = 3.dp,
    val progressThumbRadius: Dp = 6.dp,
    val playSize: Dp = 72.dp,
    val playIconSize: Dp = 34.dp,
    val skipButtonSize: Dp = 52.dp,
    val skipIconSize: Dp = 32.dp,
    val controlSpacing: Dp = 12.dp,
)

/**
 * 共享顶部栏：收起 + 标题 + 队列。
 * 本组件不含玻璃，玻璃容器由 LIQUID_GLASS / FROSTED 风格自行包裹。
 */
@Composable
fun PlayerTopBar(
    foreground: Color,
    onCollapse: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "正在播放",
    titleSize: TextUnit = 15.sp,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCollapse) {
            Icon(
                imageVector = MusicIcons.ChevronDown,
                contentDescription = "收起播放页",
                tint = foreground.copy(alpha = 0.90f),
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = titleSize),
            color = foreground.copy(alpha = 0.90f),
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenQueue) {
            Icon(
                imageVector = MusicIcons.Queue,
                contentDescription = "播放队列",
                tint = foreground.copy(alpha = 0.86f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** 无涟漪点击：播放页的图标/文字按钮统一用它，避免水波纹破坏整页质感。 */
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
}

/** 喜欢按钮：实心粉红为已喜欢，描边为未喜欢；各风格共用。 */
@Composable
fun LikeButton(
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    idleColor: Color = Color.White.copy(alpha = 0.80f),
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickableNoRipple(onToggleLike),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isLiked) MusicIcons.HeartFilled else MusicIcons.Heart,
            contentDescription = if (isLiked) "取消喜欢" else "喜欢",
            tint = if (isLiked) Color(0xFFFF6B8A) else idleColor,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** 播放模式文字按钮：点击循环切换顺序/单曲/随机。 */
@Composable
fun ModeText(
    modeLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    foreground: Color = Color.White.copy(alpha = 0.72f),
    fontSize: TextUnit = 12.sp,
) {
    Text(
        text = modeLabel,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = fontSize),
        color = foreground,
        maxLines = 1,
        modifier = modifier
            .clip(CircleShape)
            .clickableNoRipple(onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}
