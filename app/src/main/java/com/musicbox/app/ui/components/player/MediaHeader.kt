package com.musicbox.app.ui.components.player

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicbox.app.ui.components.MusicIcons
import com.musicbox.app.ui.theme.ArtworkImage

/**
 * 播放页封面：占位、圆角/圆形裁剪、可选光晕、切歌交叉淡入、点击进歌词。
 *
 * 尺寸完全由调用方的 modifier 决定（例如 `.fillMaxWidth(0.94f).aspectRatio(1f)`），
 * 内部只负责绘制，不绑定任何风格。ToolMinimal 可关掉光晕与阴影。
 */
@Composable
fun PlayerArtwork(
    artworkUrl: String?,
    onArtworkLoaded: (Bitmap) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    maxEdgePx: Int = 1080,
    showHalo: Boolean = true,
    haloColor: Color = Color.White,
    elevation: Dp = 24.dp,
    onClick: () -> Unit = {},
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (showHalo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    haloColor.copy(alpha = 0.34f),
                                    Color.Transparent,
                                ),
                                center = center,
                                radius = size.minDimension * 0.68f,
                            ),
                        )
                    },
            )
        }
        Crossfade(
            targetState = artworkUrl,
            animationSpec = tween(durationMillis = 360),
            label = "playerArtwork",
        ) { url ->
            ArtworkImage(
                url = url,
                shape = shape,
                onBitmap = onArtworkLoaded,
                maxEdgePx = maxEdgePx,
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(
                        elevation = elevation,
                        shape = shape,
                        ambientColor = Color.Black,
                        spotColor = Color.Black,
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
                    .clickable(onClick = onClick),
            )
        }
    }
}

/**
 * 标题 / 副标题 / 试听提示。文字对齐与字号可配，布局始终为纵向。
 */
@Composable
fun MediaHeader(
    title: String,
    subtitle: String,
    foreground: Color,
    modifier: Modifier = Modifier,
    secondaryForeground: Color = foreground.copy(alpha = 0.68f),
    isTrial: Boolean = false,
    titleSize: TextUnit = 20.sp,
    subtitleSize: TextUnit = 14.sp,
    titleWeight: FontWeight = FontWeight.SemiBold,
    textAlign: TextAlign = TextAlign.Center,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
) {
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = titleSize),
            fontWeight = titleWeight,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = subtitleSize),
            color = secondaryForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        if (isTrial) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "试听片段 · 登录解锁完整版",
                style = MaterialTheme.typography.labelMedium,
                color = foreground.copy(alpha = 0.62f),
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 歌词预览：实时显示当前歌词行，换行淡入淡出，点按进入歌词页。
 *
 * 不做任何容器包裹（无背景、无圆角胶囊、无玻璃），只有歌词图标 + 文本直接浮在页面上；
 * 文本带一层很轻的投影，保证在亮背景上仍可读。
 */
@Composable
fun LyricPreviewPill(
    lyricLine: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    foreground: Color = Color.White,
    maxWidth: Dp = 320.dp,
) {
    val empty = lyricLine.isBlank()
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .widthIn(min = 148.dp, max = maxWidth)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = MusicIcons.Lyrics,
            contentDescription = null,
            tint = foreground.copy(alpha = if (empty) 0.45f else 0.70f),
            modifier = Modifier.size(15.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        AnimatedContent(
            targetState = lyricLine.ifBlank { "轻触查看歌词" },
            transitionSpec = { fadeIn(tween(360)) togetherWith fadeOut(tween(240)) },
            label = "playerLyricPreview",
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.42f),
                        offset = Offset(0f, 1.5f),
                        blurRadius = 6f,
                    ),
                ),
                color = foreground.copy(alpha = if (empty) 0.55f else 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
