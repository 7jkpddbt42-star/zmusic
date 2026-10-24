package com.musicbox.app.ui.components.player

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow as elevationShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle

/**
 * 播放页玻璃背景源的外层包装。
 *
 * 只有本文件允许 import `com.kyant.backdrop.*`；其余风格文件只持有这个包装类型，
 * 从而保证玻璃实现只有一处，后续替换库或调整参数不需要改 9 个风格。
 */
class PlayerGlassBackdrop internal constructor(internal val layer: LayerBackdrop)

/** 创建播放页玻璃背景源。必须在页面层级创建一次，不能在每个玻璃控件里重复创建。 */
@Composable
fun rememberPlayerGlassBackdrop(): PlayerGlassBackdrop {
    val layer = rememberLayerBackdrop()
    return remember(layer) { PlayerGlassBackdrop(layer) }
}

/**
 * 把元素标记为玻璃背景源：内容绘制完成后持续录制，供 GlassSurface 采样。
 * 玻璃控件本身不要放进这个背景源层，否则会自采样产生鬼影。
 */
fun Modifier.playerGlassSource(backdrop: PlayerGlassBackdrop): Modifier =
    this.layerBackdrop(backdrop.layer)

/**
 * 播放页统一玻璃表面（仅 LIQUID_GLASS 与 FROSTED 使用）。
 *
 * - API 33+：真实 `drawBackdrop`。效果顺序固定为 blur ⇒ lens；
 *   lens 必须拿到 [CornerBasedShape]，表面色只在 `onDrawSurface` 里填充。
 * - API 32 及以下：不调用任何 `com.kyant.backdrop` API，直接返回渐变伪玻璃 Box，
 *   保证低版本不崩溃。
 * - [exportedBackdrop] 用于「玻璃叠玻璃」：必须传入**另一个独立的**背景层
 *   （例如 `rememberPlayerGlassBackdrop()` 新建的实例）。库会在绘制结束时把本玻璃的
 *   渲染结果录制进这一层，子玻璃再采样它；若传入与 [backdrop] 相同的实例，
 *   图层会自我录制形成循环，RenderThread 无限递归直接崩溃。
 */
@Composable
fun GlassSurface(
    backdrop: PlayerGlassBackdrop?,
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = RoundedCornerShape(28.dp),
    tint: Color = Color.White.copy(alpha = 0.10f),
    blurRadius: Dp = 6.dp,
    refractionHeight: Dp = 14.dp,
    refractionAmount: Dp = 22.dp,
    dispersion: Boolean = false,
    highlight: Boolean = true,
    shadow: Boolean = true,
    exportedBackdrop: PlayerGlassBackdrop? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val surfaceBrush = remember(tint) {
        Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.18f), tint),
        )
    }

    // 传入同一个图层等于自我录制，会让原生渲染线程无限递归，这里直接忽略这种误用
    val export = exportedBackdrop?.takeIf { it !== backdrop }

    // 阴影用 Compose 原生 elevation 阴影（RenderNode 直接绘制，首帧即可见），
    // 不再由 Backdrop 库在 drawBackdrop 内绘制：库阴影依赖背景图层首帧录制完成，
    // 打开新界面时会先缺一帧再出现，看起来像「卡一下才出来」。
    val shadowModifier = if (shadow) {
        Modifier.elevationShadow(
            elevation = 20.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black,
            spotColor = Color.Black,
        )
    } else {
        Modifier
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || backdrop == null) {
        // API 32-：纯渐变降级，不触碰 Backdrop 的任何 API
        Box(
            modifier = modifier
                .then(shadowModifier)
                .clip(shape)
                .background(surfaceBrush, shape)
                .padding(contentPadding),
            content = content,
        )
    } else {
        Box(
            modifier = modifier
                .then(shadowModifier)
                .drawBackdrop(
                    backdrop = backdrop.layer,
                    shape = { shape },
                    effects = {
                        // 顺序固定：先模糊，再折射；lens 内部依赖形状 SDF 计算位移
                        blur(blurRadius.toPx())
                        lens(
                            refractionHeight = refractionHeight.toPx(),
                            refractionAmount = refractionAmount.toPx(),
                            depthEffect = false,
                            chromaticAberration = dispersion,
                        )
                    },
                    highlight = if (highlight) {
                        {
                            Highlight(
                                width = 1.dp,
                                blurRadius = 0.5.dp,
                                alpha = 0.22f,
                                style = HighlightStyle.Default(
                                    color = Color.White,
                                    angle = 45f,
                                    falloff = 2f,
                                ),
                            )
                        }
                    } else {
                        null
                    },
                    shadow = null,
                    exportedBackdrop = export?.layer,
                    onDrawSurface = {
                        // 表面色只在这里填充，保持玻璃透光，不盖住折射纹理
                        drawRect(surfaceBrush)
                    },
                )
                .padding(contentPadding),
            content = content,
        )
    }
}
