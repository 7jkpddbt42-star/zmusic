package com.musicbox.app.ui.components.liquidglass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.roundToInt

/** 高光光照角度：45°（弧度）。画布 y 轴向下，法线指向右下，受光边为左上。 */
private val HIGHLIGHT_ANGLE_RAD = (45.0 * PI / 180.0).toFloat()

/** 白色渐变描边的不透明度上限（沿用旧 glass() 的描边思路）。 */
private const val BORDER_ALPHA = 0.28f

/** 降级路径中近似高光覆盖的高度占比。 */
private const val HIGHLIGHT_FRACTION = 1f / 3f

/**
 * 液态玻璃修饰符：实时采样 [GlassBackdrop] 记录的背景内容，按圆角矩形 SDF 做边缘折射，
 * 叠加模糊、着色与高光。算法改编自 Kyant0/AndroidLiquidGlass（Apache License 2.0）。
 *
 * 三档能力降级：
 * - **T1**（API 33+ 且提供 [backdrop]）：模糊 + AGSL 折射着色器（[dispersion] 时叠加 7 采样色散），
 *   高光用 [HIGHLIGHT_SHADER] 着色器笔刷。
 * - **T2**（API 31+ 且提供 [backdrop]）：仅 RenderEffect 模糊（无 RuntimeShader），
 *   高光退化为顶部垂直渐变，[dispersion] 被忽略。
 * - **T3**（其余 / [backdrop] 为 null）：不采样背景，用半透明渐变底 + 高光 + 描边近似。
 *
 * 与旧 `Modifier.glass()` 的伪玻璃不同，T1/T2 通过 [androidx.compose.ui.graphics.layer.GraphicsLayer]
 * 离屏记录真实背景，折射与模糊都是「看得到」的——背景滚动/播放视频时玻璃内容实时变化。
 *
 * 注意：扩展为 `@Composable`（与 `glass()` 一致），调用处需在 Composable 作用域内。
 */
@Composable
fun Modifier.liquidGlass(
    backdrop: GlassBackdrop?,
    shape: Shape = RoundedCornerShape(20.dp),
    blurRadius: Dp = 22.dp,
    refractionHeight: Dp = 6.dp,
    refractionAmount: Float = 1.4f,
    tint: Color = Color.White.copy(alpha = 0.12f),
    dispersion: Boolean = false,
    highlight: Boolean = true,
): Modifier = when {
    backdrop != null && isRuntimeShaderGlassSupported() ->
        runtimeShaderGlass(backdrop, shape, blurRadius, refractionHeight, refractionAmount, tint, dispersion, highlight)

    backdrop != null && isBackdropBlurSupported() ->
        blurGlass(backdrop, shape, blurRadius, tint, highlight)

    else -> fallbackGlass(shape, tint, highlight)
}

/**
 * T1：模糊 + RuntimeShader 折射（API 33+）。
 *
 * 实现要点：
 * - 背景采样带通过录制 [GlassBackdrop.graphicsLayer] 时平移 `-backdropOffset` 对齐到玻璃局部原点，
 *   保证折射带与模糊邻域都落在玻璃尺寸的离屏缓冲区内；因此 shader 的 offset uniform 恒传 0
 *   （与「shader 内 content.eval(coord + offset)」在数学上等效，但不受缓冲区尺寸限制）。
 * - RenderEffect 链：先 MIRROR 平铺模糊（避免边缘发黑），后折射（[Shader.TileMode.MIRROR]）。
 * - 折射位移向内（refractionAmount 取负），不会采样玻璃范围之外的背景。
 */
@Composable
private fun Modifier.runtimeShaderGlass(
    backdrop: GlassBackdrop,
    shape: Shape,
    blurRadius: Dp,
    refractionHeight: Dp,
    refractionAmount: Float,
    tint: Color,
    dispersion: Boolean,
    highlight: Boolean,
): Modifier {
    val selfCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
    val glassLayer = rememberGraphicsLayer()
    // RuntimeShader 编译开销大，随组合创建一次，每帧只更新 uniforms
    val refractionShader = remember(dispersion) {
        RuntimeShader(if (dispersion) REFRACTION_DISPERSION_SHADER else REFRACTION_SHADER)
    }
    val highlightShader = remember { RuntimeShader(HIGHLIGHT_SHADER) }
    val borderBrush = remember {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = BORDER_ALPHA), Color.White.copy(alpha = 0f)))
    }

    return this
        .onGloballyPositioned { selfCoordinates.value = it }
        .clip(shape)
        .drawWithContent {
            val outline = shape.createOutline(size, layoutDirection, this)
            val cornerRadii = outlineCornerRadii(outline, size.minDimension / 2f)
            val coordinates = selfCoordinates.value
            if (coordinates != null && size.minDimension >= 1f) {
                val backdropOffset = backdrop.offsetFor(coordinates)
                val blurPx = blurRadius.toPx()
                refractionShader.setFloatUniform("size", size.width, size.height)
                // 采样偏移由录制平移承担，offset 恒为 0（见函数注释）
                refractionShader.setFloatUniform("offset", 0f, 0f)
                refractionShader.setFloatUniform("cornerRadii", cornerRadii[0], cornerRadii[1], cornerRadii[2], cornerRadii[3])
                refractionShader.setFloatUniform("refractionHeight", refractionHeight.toPx())
                refractionShader.setFloatUniform("refractionAmount", -refractionAmount)
                refractionShader.setFloatUniform("depthEffect", 0f)
                if (dispersion) refractionShader.setFloatUniform("chromaticAberration", 1.2f)

                // 先模糊后折射：链式 RenderEffect，外层作用在内层的输出上
                val blurEffect = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.MIRROR)
                val shaderEffect = RenderEffect.createRuntimeShaderEffect(refractionShader, "content")
                val chain = RenderEffect.createChainEffect(shaderEffect, blurEffect)
                // 把背景层中玻璃对应区域平移到局部原点，供着色器采样；
                // 模糊/折射链作为图层属性在合成该层时生效
                glassLayer.record(IntSize(size.width.roundToInt(), size.height.roundToInt())) {
                    translate(-backdropOffset.x, -backdropOffset.y) {
                        drawLayer(backdrop.graphicsLayer)
                    }
                }
                glassLayer.renderEffect = chain.asComposeRenderEffect()
            }

            // 合成：折射玻璃 → 着色 → 高光 → 描边 → 元素自身内容
            drawLayer(glassLayer)
            drawRect(color = tint)
            if (highlight) {
                highlightShader.setFloatUniform("size", size.width, size.height)
                highlightShader.setFloatUniform("cornerRadii", cornerRadii[0], cornerRadii[1], cornerRadii[2], cornerRadii[3])
                highlightShader.setColorUniform("color", Color.White.toArgb())
                highlightShader.setFloatUniform("angle", HIGHLIGHT_ANGLE_RAD)
                highlightShader.setFloatUniform("falloff", 1f)
                drawRect(brush = ShaderBrush(highlightShader), blendMode = BlendMode.Plus)
            }
            drawOutline(outline, borderBrush, style = Stroke(width = 1.dp.toPx()))
            drawContent()
        }
}

/**
 * T2：仅 RenderEffect 模糊（API 31–32，无 RuntimeShader / RuntimeShaderBrush）。
 * 录制方式与 T1 相同；高光用顶部 1/3 高度的白色垂直渐变近似。
 */
@Composable
private fun Modifier.blurGlass(
    backdrop: GlassBackdrop,
    shape: Shape,
    blurRadius: Dp,
    tint: Color,
    highlight: Boolean,
): Modifier {
    val selfCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
    val glassLayer = rememberGraphicsLayer()
    val borderBrush = remember {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = BORDER_ALPHA), Color.White.copy(alpha = 0f)))
    }

    return this
        .onGloballyPositioned { selfCoordinates.value = it }
        .clip(shape)
        .drawWithContent {
            val outline = shape.createOutline(size, layoutDirection, this)
            val coordinates = selfCoordinates.value
            if (coordinates != null && size.minDimension >= 1f) {
                val backdropOffset = backdrop.offsetFor(coordinates)
                val blurPx = blurRadius.toPx()
                val blurEffect = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.MIRROR)
                glassLayer.record(IntSize(size.width.roundToInt(), size.height.roundToInt())) {
                    translate(-backdropOffset.x, -backdropOffset.y) {
                        drawLayer(backdrop.graphicsLayer)
                    }
                }
                glassLayer.renderEffect = blurEffect.asComposeRenderEffect()
            }

            drawLayer(glassLayer)
            drawRect(color = tint)
            if (highlight) {
                val h = size.height * HIGHLIGHT_FRACTION
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
                        startY = 0f,
                        endY = h,
                    ),
                    size = Size(size.width, h),
                )
            }
            drawOutline(outline, borderBrush, style = Stroke(width = 1.dp.toPx()))
            drawContent()
        }
}

/**
 * T3：无背景采样（背景层缺失或 API < 31）。半透明渐变底 + 顶部高光 + 1dp 白色渐变描边，
 * 视觉接近旧 glass() 但更亮更脆。
 */
@Composable
private fun Modifier.fallbackGlass(shape: Shape, tint: Color, highlight: Boolean): Modifier {
    val fillBrush = remember(tint) {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), tint))
    }
    val borderBrush = remember {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = BORDER_ALPHA), Color.White.copy(alpha = 0f)))
    }
    return this
        .clip(shape)
        .background(fillBrush, shape)
        .then(
            if (highlight) {
                Modifier.drawBehind {
                    val h = size.height * HIGHLIGHT_FRACTION
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

/**
 * 从 [Outline] 提取四角圆角半径（左上/右上/右下/左下，px）并钳制到 [maxRadius]；
 * 非圆角轮廓（矩形等）返回全 0。
 */
private fun outlineCornerRadii(outline: Outline, maxRadius: Float): FloatArray = when (outline) {
    is Outline.Rounded -> {
        val rect = outline.roundRect
        cornerRadiiFloatArray(
            topLeftPx = rect.topLeftCornerRadius.x,
            topRightPx = rect.topRightCornerRadius.x,
            bottomRightPx = rect.bottomRightCornerRadius.x,
            bottomLeftPx = rect.bottomLeftCornerRadius.x,
            maxRadius = maxRadius,
        )
    }

    else -> cornerRadiiFloatArray(0f, 0f, 0f, 0f, maxRadius)
}
