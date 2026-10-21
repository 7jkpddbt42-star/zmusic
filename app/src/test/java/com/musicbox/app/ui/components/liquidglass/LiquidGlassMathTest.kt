package com.musicbox.app.ui.components.liquidglass

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 液态玻璃参数映射与能力阈值的 JVM 单测。
 * 着色器实现已改为官方库 io.github.kyant0:backdrop，这里只测本项目可控的纯函数。
 */
class LiquidGlassMathTest {

    @Test
    fun `capability thresholds reject missing api level`() {
        // JVM 单测中 Build.VERSION.SDK_INT 恒为 0，低于 33/31，两个能力都应返回 false
        assertFalse(isRuntimeShaderGlassSupported())
        assertFalse(isBackdropBlurSupported())
    }

    @Test
    fun `press scale shrinks to ninety eight percent`() {
        assertEquals(1f, pressScaleForProgress(0f), 0f)
        assertEquals(0.98f, pressScaleForProgress(1f), 1e-6f)
        assertTrue(pressScaleForProgress(0.5f) > 0.98f)
    }

    @Test
    fun `press boosts refraction by twenty percent`() {
        assertEquals(100f, refractionAmountForPress(100f, 0f), 1e-6f)
        assertEquals(120f, refractionAmountForPress(100f, 1f), 1e-3f)
    }

    @Test
    fun `press boosts highlight by ten percent`() {
        assertEquals(0.18f, highlightAlphaForPress(0.18f, 0f), 1e-6f)
        assertEquals(0.198f, highlightAlphaForPress(0.18f, 1f), 1e-4f)
    }

    @Test
    fun `fallback raises blur into eight to ten dp range`() {
        // API 33+：保持 6dp 辅助模糊
        assertEquals(6.dp, blurRadiusForFallback(6.dp, runtimeShaderSupported = true))
        // API 31–32：无折射时提高到 8–10dp
        assertEquals(8.dp, blurRadiusForFallback(6.dp, runtimeShaderSupported = false))
        assertEquals(9.dp, blurRadiusForFallback(9.dp, runtimeShaderSupported = false))
        assertEquals(10.dp, blurRadiusForFallback(12.dp, runtimeShaderSupported = false))
    }
}
