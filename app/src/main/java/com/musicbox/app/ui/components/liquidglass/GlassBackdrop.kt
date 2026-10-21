package com.musicbox.app.ui.components.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * 液态玻璃背景源类型。
 *
 * 直接使用官方库 io.github.kyant0:backdrop 的 [LayerBackdrop]：
 * 它由 [rememberLayerBackdrop] 创建、由 [layerBackdrop] 持续录制背景内容，
 * 并在每次绘制时按玻璃元素的实时坐标重新计算采样偏移（isCoordinatesDependent = true）。
 */
typealias GlassBackdrop = LayerBackdrop

/**
 * 创建并持有一个背景录制层。库内部用 [androidx.compose.ui.graphics.rememberGraphicsLayer] 管理生命周期，
 * 默认 onDraw 会录制所挂载元素的绘制内容，因此滚动、拖拽、视频播放时玻璃采样都是逐帧动态的。
 */
@Composable
fun rememberGlassBackdrop(): GlassBackdrop = rememberLayerBackdrop()

/** 局部 CompositionLocal：向子树下发当前可用的背景录制层，未提供时玻璃降级为伪玻璃。 */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/**
 * 把当前元素标记为玻璃背景源：内容绘制完成后持续录制进 [GlassBackdrop]，
 * 供后续玻璃元素采样。底层实现为官方库的 Modifier.layerBackdrop。
 */
fun Modifier.glassBackdropSource(backdrop: GlassBackdrop): Modifier = this.layerBackdrop(backdrop)
