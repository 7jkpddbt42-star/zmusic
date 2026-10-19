package com.musicbox.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [backgroundBitmapInSampleSize] 的纯函数单测：采样率应为 2 的幂且解码后最长边 ≤ maxEdge。 */
class BackgroundMathTest {

    @Test
    fun `small image needs no downsampling`() {
        assertEquals(1, backgroundBitmapInSampleSize(800, 600, 1024))
        // 恰好等于 maxEdge 时不降采样
        assertEquals(1, backgroundBitmapInSampleSize(1024, 1024, 1024))
        assertEquals(1, backgroundBitmapInSampleSize(1024, 512, 1024))
    }

    @Test
    fun `large image is downsampled by power of two within max edge`() {
        val sample = backgroundBitmapInSampleSize(4096, 2048, 1024)
        assertTrue("采样率应为 2 的幂：$sample", sample > 0 && (sample and (sample - 1)) == 0)
        assertEquals(4, sample)
        assertTrue("解码后最长边越界", 4096 / sample <= 1024)
    }

    @Test
    fun `result never exceeds max edge across sizes`() {
        val widths = listOf(1, 100, 1023, 1025, 2000, 4000, 8000)
        val heights = listOf(1, 512, 1500, 3000)
        for (w in widths) {
            for (h in heights) {
                val sample = backgroundBitmapInSampleSize(w, h, 1024)
                val longest = maxOf(w, h)
                assertTrue("w=$w h=$h sample=$sample 越界", longest / sample <= 1024)
            }
        }
    }

    @Test
    fun `illegal sizes return 1`() {
        assertEquals(1, backgroundBitmapInSampleSize(0, 100, 1024))
        assertEquals(1, backgroundBitmapInSampleSize(100, 0, 1024))
        assertEquals(1, backgroundBitmapInSampleSize(-5, -5, 1024))
        assertEquals(1, backgroundBitmapInSampleSize(4096, 4096, 0))
    }
}
