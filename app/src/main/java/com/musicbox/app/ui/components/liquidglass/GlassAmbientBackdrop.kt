package com.musicbox.app.ui.components.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musicbox.app.data.BackgroundSpec
import com.musicbox.app.ui.components.BackgroundLayer

/**
 * 页面级玻璃背景源：真实背景（视频 / 图片 / 封面）+ 遮罩，供玻璃控件逐帧采样。
 *
 * 为什么需要它：设置页、登录页以前只铺一层不透明 `surface` 作为采样源，
 * 纯色背景下模糊在数学上不可见，玻璃看起来只是半透明色块。
 * 这里改用用户选择的真实背景（视频会持续变化，封面会随歌曲切换），
 * 叠加遮罩只为保证前景文字可读；玻璃的模糊/折射因此始终有真实内容可采样。
 *
 * [coverBlurRadius] 取 20dp：页面级背景只需要一团柔和的色彩氛围，
 * 不能让封面上的文字/人脸可辨认地与正文抢注意力；同时保留大面积色块，
 * 玻璃自身的 6dp 模糊仍有可采样的边缘，不会退化成纯色。
 */
@Composable
fun GlassAmbientBackdrop(
    background: BackgroundSpec,
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    scrim: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.32f),
    coverBlurRadius: Dp = 20.dp,
) {
    // API < 31 没有 RenderEffect，玻璃只能走渐变伪玻璃，背景不会被糊开；
    // 若仍用 35% 遮罩，亮而杂的背景会让卡片文字丢掉对比度。
    // 无实时模糊时把遮罩加重到 72%，可读性优先于背景存在感。
    val effectiveScrim = if (isBackdropBlurSupported()) {
        scrim
    } else {
        scrim.copy(alpha = maxOf(scrim.alpha, 0.72f))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        BackgroundLayer(
            spec = background,
            coverUrl = artworkUrl,
            coverBlurRadius = coverBlurRadius,
            scrim = false,
            modifier = Modifier.fillMaxSize(),
        )
        Box(modifier = Modifier.fillMaxSize().background(effectiveScrim))
    }
}
