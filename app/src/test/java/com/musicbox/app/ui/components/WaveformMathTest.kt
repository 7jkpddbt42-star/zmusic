package com.musicbox.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * [waveformBarFraction] 与 [particleParams] 的纯函数单测：
 * 重点验证"确定性"（同输入同输出）与取值范围。
 */
class WaveformMathTest {

    // ---------- waveformBarFraction ----------

    @Test
    fun `waveform fraction is deterministic for same input`() {
        for (index in 0 until 64) {
            val a = waveformBarFraction(songId = 123456789L, index = index)
            val b = waveformBarFraction(songId = 123456789L, index = index)
            assertEquals(a, b, 0.0f)
        }
    }

    @Test
    fun `waveform fraction stays within 0_15 to 1`() {
        val songIds = listOf<Long?>(null, 0L, 1L, -1L, 42L, Long.MAX_VALUE, Long.MIN_VALUE)
        for (songId in songIds) {
            for (index in 0 until 200) {
                val f = waveformBarFraction(songId = songId, index = index)
                assertTrue("songId=$songId index=$index f=$f 越界", f in 0.15f..1f)
            }
        }
    }

    @Test
    fun `different indices produce at least two distinct values`() {
        val values = (0 until 16).map { waveformBarFraction(songId = 7L, index = it) }.toSet()
        assertTrue("条高几乎全相同，波形不成形", values.size >= 2)
    }

    @Test
    fun `different song ids change most bar heights`() {
        val a = (0 until 64).map { waveformBarFraction(songId = 111L, index = it) }
        val b = (0 until 64).map { waveformBarFraction(songId = 222L, index = it) }
        val differing = a.indices.count { a[it] != b[it] }
        assertTrue("不同歌曲的波形差异过小：$differing/64", differing > 32)
    }

    @Test
    fun `null song id falls back to zero deterministically`() {
        for (index in 0 until 32) {
            assertEquals(
                waveformBarFraction(songId = 0L, index = index),
                waveformBarFraction(songId = null, index = index),
                0.0f,
            )
        }
    }

    // ---------- particleParams ----------

    @Test
    fun `particle params are deterministic for same input`() {
        for (index in 0 until 40) {
            assertEquals(particleParams(index, 40), particleParams(index, 40))
        }
    }

    @Test
    fun `particle params fall inside expected ranges`() {
        val count = 40
        for (index in 0 until count) {
            val p = particleParams(index, count)
            assertTrue("x=${p.x}", p.x >= 0f && p.x < 1f)
            assertTrue("y=${p.y}", p.y >= 0f && p.y < 1f)
            assertTrue("speed=${p.speed}", p.speed >= 0.05f && p.speed <= 0.25f)
            assertTrue("radiusFactor=${p.radiusFactor}", p.radiusFactor >= 0.4f && p.radiusFactor <= 1f)
            assertTrue("phase=${p.phase}", p.phase >= 0f && p.phase < (2f * PI.toFloat()))
            assertTrue("alpha=${p.alpha}", p.alpha >= 0.10f && p.alpha <= 0.35f)
        }
    }

    @Test
    fun `different indices give distinct x positions when count is fixed`() {
        val count = 40
        val xs = (0 until count).map { particleParams(it, count).x }
        assertTrue("x 出现重复：$xs", xs.toSet().size == count)
    }

    @Test
    fun `y positions vary across indices`() {
        val count = 40
        val ys = (0 until count).map { particleParams(it, count).y }.toSet()
        assertTrue("y 几乎全相同，粒子扎堆", ys.size >= 2)
    }
}
