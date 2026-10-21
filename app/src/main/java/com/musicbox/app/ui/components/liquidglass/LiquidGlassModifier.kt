package com.musicbox.app.ui.components.liquidglass

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow

/** 液化玻璃高光角度（度）：受光边为左上。官方库的 HighlightStyle.Default.angle 量纲为度。 */
private const val HIGHLIGHT_ANGLE_DEG = 45f

/** 高光衰减指数：2 表示高光紧贴受光边缘，不向表面内部扩散。 */
private const val HIGHLIGHT_FALLOFF = 2f

/** 边缘高光基础强度（官方库 Highlight.alpha，0–1）。 */
private const val HIGHLIGHT_ALPHA = 0.18f

/** 边缘高光宽度（官方库 Highlight.width）。 */
private val HIGHLIGHT_WIDTH = 1.dp

/** 投影：黑色 10% alpha、模糊 20dp、向下偏移 6dp（符合 iOS 26 的轻量悬浮感）。 */
private val GLASS_SHADOW = Shadow(
    radius = 20.dp,
    offset = DpOffset(0.dp, 6.dp),
    color = Color.Black.copy(alpha = 0.10f),
)

/** 降级路径中近似高光覆盖的高度占比。 */
private const val FALLBACK_HIGHLIGHT_FRACTION = 1f / 3f

/** 白色渐变描边的不透明度上限（降级路径用）。 */
private const val FALLBACK_BORDER_ALPHA = 0.28f

/**
 * API 能力阈值：RenderEffect 模糊自 API 31（S）起可用。
 * 低于此版本时官方库的 blur/lens 都会静默跳过，因此直接走本地伪玻璃降级。
 */
@ChecksSdkIntAtLeast(Build.VERSION_CODES.S)
fun isBackdropBlurSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** 折射依赖 AGSL RuntimeShader，自 API 33（Tiramisu）起可用；官方库在更低版本会跳过 lens。 */
@ChecksSdkIntAtLeast(Build.VERSION_CODES.TIRAMISU)
fun isRuntimeShaderGlassSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** 按压时整体缩放目标：1 → 0.98（progress 0–1）。 */
internal fun pressScaleForProgress(progress: Float): Float = 1f - 0.02f * progress

/** 按压时折射强度提升：基础值 ×（1 + 20% × progress）。 */
internal fun refractionAmountForPress(basePx: Float, progress: Float): Float =
    basePx * (1f + 0.20f * progress)

/** 按压时高光强度提升：基础值 ×（1 + 10% × progress）。 */
internal fun highlightAlphaForPress(baseAlpha: Float, progress: Float): Float =
    baseAlpha * (1f + 0.10f * progress)

/**
 * API 31–32 的降级模糊：无 RuntimeShader（折射不可用），把模糊提高到 8–10dp
 * 以弥补缺少折射带来的「玻璃感」缺失；API 33+ 保持原值（4–8dp，推荐 6dp）。
 */
internal fun blurRadiusForFallback(base: Dp, runtimeShaderSupported: Boolean): Dp =
    if (runtimeShaderSupported) base else base.coerceIn(8.dp, 10.dp)

/**
 * 液态玻璃修饰符，底层直接使用 Kyant0/AndroidLiquidGlass 官方库
 * `io.github.kyant0:backdrop:2.0.1`（Apache-2.0）。
 *
 * 参数映射（库参数名 / 量纲 / 默认值）：
 * - 模糊：`BackdropEffectScope.blur(radius: Float)` / px / 6dp
 * - 折射：`BackdropEffectScope.lens(refractionHeight: Float, refractionAmount: Float,
 *   depthEffect: Boolean = false, chromaticAberration: Boolean = false)` / px / 12dp、24dp
 * - 色散：`chromaticAberration` 在库中只有开关（Boolean），开启即按着色器内固定强度叠加 7 采样色散，
 *   无法传 0.06–0.12 的标量；本项目开启（等同把目标强度取为最小可见值）
 * - 色调：`drawBackdrop(onDrawSurface)` 内 `drawRect(tint)` / 白色 alpha 0.08
 * - 高光：`Highlight(width: Dp, blurRadius: Dp, alpha: Float, style: HighlightStyle)` /
 *   width 1dp、alpha 0.18、Default(angle 45°, falloff 2)
 * - 阴影：`Shadow(radius: Dp, offset: DpOffset, color: Color)` / 20dp、y 6dp、黑色 10%
 * - 按压：`InteractionSource` 按下时整体缩放 0.98、折射 +20%、高光 +10%，spring 动画
 *
 * 降级（本项目 minSdk 26）：
 * - API 33+：blur + lens + 色散 + 高光全管线；
 * - API 31–32：库内 blur 生效并提升到 8–10dp，lens/色散因无 RuntimeShader 自动跳过，
 *   高光退化为纯白描边；
 * - API 26–30 或无背景源：本地渐变底 + 顶部高光 + 1dp 描边伪玻璃。
 */
@Composable
fun Modifier.liquidGlass(
    backdrop: GlassBackdrop?,
    shape: Shape = RoundedCornerShape(28.dp),
    blurRadius: Dp = 6.dp,
    refractionHeight: Dp = 12.dp,
    refractionAmount: Dp = 24.dp,
    tint: Color = Color.White.copy(alpha = 0.08f),
    dispersion: Boolean = true,
    highlight: Boolean = true,
    interactionSource: InteractionSource? = null,
): Modifier {
    // 按压进度：0 → 1 的 spring 过渡，驱动缩放、折射与高光同步变化
    val pressed = interactionSource?.let { it.collectIsPressedAsState().value } ?: false
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioMediumBouncy,
        ),
        label = "liquidGlassPress",
    )

    if (backdrop == null || !isBackdropBlurSupported()) {
        return fallbackGlass(shape, tint, highlight)
    }

    val scale = pressScaleForProgress(pressProgress)
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                // 模糊只做辅助：API 33+ 为 6dp，保留背景轮廓，避免把纹理抹平
                blur(blurRadiusForFallback(blurRadius, isRuntimeShaderGlassSupported()).toPx())
                // 折射主导：位移量随按压 +20%
                lens(
                    refractionHeight = refractionHeight.toPx(),
                    refractionAmount = refractionAmountForPress(refractionAmount.toPx(), pressProgress),
                    depthEffect = false,
                    chromaticAberration = dispersion,
                )
            },
            highlight = if (highlight) {
                {
                    Highlight(
                        width = HIGHLIGHT_WIDTH,
                        blurRadius = HIGHLIGHT_WIDTH / 2f,
                        alpha = highlightAlphaForPress(HIGHLIGHT_ALPHA, pressProgress),
                        style = HighlightStyle.Default(
                            color = Color.White,
                            angle = HIGHLIGHT_ANGLE_DEG,
                            falloff = HIGHLIGHT_FALLOFF,
                        ),
                    )
                }
            } else {
                null
            },
            shadow = { GLASS_SHADOW },
            onDrawSurface = {
                // 低 alpha 白色着色：只提亮，不变成半透明白色塑料板
                drawRect(tint)
            },
        )
}

/**
 * API < 31 / 缺少背景源时的伪玻璃：半透明渐变底 + 顶部高光 + 1dp 白色渐变描边。
 * 不采样背景（不具备 RenderEffect），但保证视觉仍是玻璃而非纯色块。
 */
@Composable
private fun Modifier.fallbackGlass(shape: Shape, tint: Color, highlight: Boolean): Modifier {
    val fillBrush = remember(tint) {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), tint))
    }
    val borderBrush = remember {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = FALLBACK_BORDER_ALPHA), Color.White.copy(alpha = 0f)),
        )
    }
    return this
        .background(fillBrush, shape)
        .then(
            if (highlight) {
                Modifier.drawBehind {
                    val h = size.height * FALLBACK_HIGHLIGHT_FRACTION
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
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
