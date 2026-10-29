package com.musicbox.app.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musicbox.app.data.BackgroundSpec
import com.musicbox.app.player.PositionSource
import com.musicbox.app.ui.components.BackgroundLayer
import com.musicbox.app.ui.components.BlurredBackdrop
import com.musicbox.app.ui.components.ParticleField

/**
 * 播放页背景模糊半径。由设置页「背景模糊」下发，所有播放页风格共用；
 * 默认 28dp（比旧版固定 48dp 更小，保留更多封面纹理）。
 */
val LocalPlayerBackgroundBlur = compositionLocalOf { 28.dp }

/**
 * 播放页共享背景层。
 *
 * 结构固定为：真实背景（视频/图片/封面模糊）→ 压暗 → 纵向渐变 → 粒子 → 内容。
 * 背景源层与内容层分开：只有背景层挂 [playerGlassSource]，玻璃控件放在内容层，
 * 避免自采样鬼影。每种风格通过 [scrim] / [gradient] / [particles] / [coverBlur]
 * 调整氛围，但不需要重写背景管线。
 */
@Composable
fun PlayerBackdrop(
    background: BackgroundSpec,
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    scrim: Float = 0.42f,
    gradient: Boolean = true,
    particles: Boolean = true,
    source: PositionSource? = null,
    isPlaying: Boolean = false,
    coverBlur: Boolean = false,
    glassBackdrop: PlayerGlassBackdrop? = null,
    backgroundModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val blurRadius: Dp = LocalPlayerBackgroundBlur.current
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(backgroundModifier)
                .then(
                    if (glassBackdrop != null) {
                        Modifier.playerGlassSource(glassBackdrop)
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (coverBlur) {
                BlurredBackdrop(
                    url = artworkUrl,
                    modifier = Modifier.fillMaxSize(),
                    blurRadius = blurRadius,
                )
            } else {
                BackgroundLayer(
                    spec = background,
                    coverUrl = artworkUrl,
                    coverBlurRadius = blurRadius,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrim.coerceIn(0f, 1f))),
            )

            if (gradient) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.38f),
                                0.32f to Color.Transparent,
                                0.62f to Color.Black.copy(alpha = 0.22f),
                                1f to Color.Black.copy(alpha = 0.86f),
                            ),
                        ),
                )
            }

            if (particles && source != null) {
                ParticleField(
                    source = source,
                    isPlaying = isPlaying,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        content()
    }
}
