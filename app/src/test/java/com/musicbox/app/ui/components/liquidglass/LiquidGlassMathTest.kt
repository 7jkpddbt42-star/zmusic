package com.musicbox.app.ui.components.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 液态玻璃纯函数部分的 JVM 单测：
 * 圆角半径钳制、AGSL 着色器字符串的关键 uniform 契约、API 能力阈值。
 */
class LiquidGlassMathTest {

    @Test
    fun `corner radii keep values within max radius`() {
        val radii = cornerRadiiFloatArray(20f, 12f, 8f, 0f, 100f)
        assertTrue(radii.contentEquals(floatArrayOf(20f, 12f, 8f, 0f)))
    }

    @Test
    fun `corner radii are clamped to max radius`() {
        // 任一角超过 maxRadius 都被钳制，其余角保持原值
        val radii = cornerRadiiFloatArray(150f, 100f, 30f, 200f, 100f)
        assertTrue(radii.contentEquals(floatArrayOf(100f, 100f, 30f, 100f)))
    }

    @Test
    fun `corner radii order is top-left top-right bottom-right bottom-left`() {
        val radii = cornerRadiiFloatArray(1f, 2f, 3f, 4f, 50f)
        assertEquals(1f, radii[0], 0f)
        assertEquals(2f, radii[1], 0f)
        assertEquals(3f, radii[2], 0f)
        assertEquals(4f, radii[3], 0f)
    }

    @Test
    fun `all zero radii stay zero`() {
        val radii = cornerRadiiFloatArray(0f, 0f, 0f, 0f, 50f)
        assertEquals(4, radii.size)
        assertTrue(radii.contentEquals(floatArrayOf(0f, 0f, 0f, 0f)))
    }

    @Test
    fun `zero max radius clamps everything to zero`() {
        val radii = cornerRadiiFloatArray(1f, 2f, 3f, 4f, 0f)
        assertEquals(4, radii.size)
        assertTrue(radii.contentEquals(floatArrayOf(0f, 0f, 0f, 0f)))
    }

    @Test
    fun `refraction shader keeps required uniforms`() {
        // 防止手滑改坏着色器契约：uniform 声明必须齐全
        assertTrue(REFRACTION_SHADER.contains("uniform shader content"))
        assertTrue(REFRACTION_SHADER.contains("uniform float2 size"))
        assertTrue(REFRACTION_SHADER.contains("uniform float2 offset"))
        assertTrue(REFRACTION_SHADER.contains("uniform float4 cornerRadii"))
        assertTrue(REFRACTION_SHADER.contains("uniform float refractionHeight"))
        assertTrue(REFRACTION_SHADER.contains("uniform float refractionAmount"))
        assertTrue(REFRACTION_SHADER.contains("uniform float depthEffect"))
        assertTrue(REFRACTION_SHADER.contains("content.eval"))
        // SDF 关键函数不可缺失
        assertTrue(REFRACTION_SHADER.contains("sdRoundedRect"))
        assertTrue(REFRACTION_SHADER.contains("gradSdRoundedRect"))
        assertTrue(REFRACTION_SHADER.contains("circleMap"))
    }

    @Test
    fun `dispersion shader adds chromatic aberration on top of refraction`() {
        assertTrue(REFRACTION_DISPERSION_SHADER.contains("uniform shader content"))
        assertTrue(REFRACTION_DISPERSION_SHADER.contains("uniform float chromaticAberration"))
        assertTrue(REFRACTION_DISPERSION_SHADER.contains("refractionHeight"))
        assertTrue(REFRACTION_DISPERSION_SHADER.contains("content.eval"))
    }

    @Test
    fun `highlight shader declares color angle and falloff`() {
        assertTrue(HIGHLIGHT_SHADER.contains("layout(color) uniform half4 color"))
        assertTrue(HIGHLIGHT_SHADER.contains("uniform float angle"))
        assertTrue(HIGHLIGHT_SHADER.contains("uniform float falloff"))
        assertTrue(HIGHLIGHT_SHADER.contains("uniform float4 cornerRadii"))
    }

    @Test
    fun `capability thresholds reject missing api level`() {
        // JVM 单测中 Build.VERSION.SDK_INT 恒为 0，低于 33/31，两个能力都应返回 false
        assertFalse(isRuntimeShaderGlassSupported())
        assertFalse(isBackdropBlurSupported())
    }
}
