package com.musicbox.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/** 顶部高光覆盖的高度占比。 */
private const val HIGHLIGHT_FRACTION = 0.35f

/**
 * 液态玻璃材质：半透明底 + 细亮边 + 顶部高光。
 *
 * - **半透明底**：以 [MaterialTheme.colorScheme.surface] 混入少量白，再按 [tintAlpha] 透明化。
 *   深色主题下是「烟熏玻璃」，浅色主题下是「磨砂白玻璃」，都不会沦为灰块。
 * - **细亮边**：1dp、白色 → 透明的垂直渐变描边（模拟玻璃受光边）。
 * - **顶部高光**：[highlight] 为真时在顶部约 [HIGHLIGHT_FRACTION] 高度叠一层极淡白色垂直渐变。
 * - 先 `.clip(shape)`，高光与描边不会溢出圆角。
 *
 * 因读取 [MaterialTheme]，本扩展为 `@Composable`；调用处（皆在 Composable 作用域内）
 * 可直接写成 `Modifier.fillMaxWidth().glass()`。
 */
@Composable
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(20.dp),
    tintAlpha: Float = 0.16f,
    borderAlpha: Float = 0.28f,
    highlight: Boolean = true,
): Modifier {
    val fill = lerp(MaterialTheme.colorScheme.surface, Color.White, 0.10f).copy(alpha = tintAlpha)
    val borderBrush = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = borderAlpha), Color.White.copy(alpha = 0f)),
    )
    return this
        .clip(shape)
        .background(fill)
        .then(
            if (highlight) {
                Modifier.drawBehind {
                    val h = size.height * HIGHLIGHT_FRACTION
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                            startY = 0f,
                            endY = h,
                        ),
                        size = Size(size.width, h),
                    )
                }
            } else {
                Modifier
            },
        )
        .border(1.dp, borderBrush, shape)
}
