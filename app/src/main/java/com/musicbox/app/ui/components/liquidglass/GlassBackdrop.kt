package com.musicbox.app.ui.components.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * 背景捕获层（简化自 Kyant0/AndroidLiquidGlass 的 LayerBackdrop）。
 *
 * 背景元素通过 [Modifier.glassBackdropSource] 把自身内容持续记录到 [graphicsLayer]；
 * 玻璃元素（[Modifier.liquidGlass]）在绘制时从该层采样，配合折射着色器形成「透过玻璃看背景」的效果。
 *
 * [sourceCoordinates] 为背景源元素的全局坐标，用于计算玻璃元素相对背景的偏移。
 */
class GlassBackdrop internal constructor(val graphicsLayer: GraphicsLayer) {

    var sourceCoordinates: LayoutCoordinates? by mutableStateOf(null)
}

/**
 * 创建并持有一个 [GlassBackdrop]；其内部 [GraphicsLayer] 由 Compose 管理生命周期。
 */
@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val graphicsLayer = rememberGraphicsLayer()
    return remember { GlassBackdrop(graphicsLayer) }
}

/** 局部CompositionLocal：向子树下发当前可用的背景捕获层，未提供时为 null（玻璃降级为伪玻璃）。 */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/**
 * 把当前元素标记为玻璃背景源：每次布局更新坐标，每次绘制先把内容记录进
 * [GlassBackdrop.graphicsLayer]（尺寸取整到像素），再正常绘制自身。
 */
fun Modifier.glassBackdropSource(backdrop: GlassBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.sourceCoordinates = it }
    .drawWithContent {
        // 先记录，再绘制：记录与绘制使用同一份内容，保证玻璃采样与所见一致
        backdrop.graphicsLayer.record(IntSize(size.width.roundToInt(), size.height.roundToInt())) {
            this@drawWithContent.drawContent()
        }
        drawContent()
    }

/**
 * 计算 [coordinates]（通常是玻璃元素）相对背景源左上角的位移。
 * 背景源尚未布局或坐标不可比（非兄弟/祖先关系异常）时返回 [Offset.Zero]。
 */
fun GlassBackdrop.offsetFor(coordinates: LayoutCoordinates): Offset {
    val source = sourceCoordinates ?: return Offset.Zero
    return try {
        source.localPositionOf(coordinates, Offset.Zero)
    } catch (_: IllegalStateException) {
        Offset.Zero
    }
}
