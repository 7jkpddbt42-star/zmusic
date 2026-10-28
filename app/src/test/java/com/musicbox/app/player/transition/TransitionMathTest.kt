package com.musicbox.app.player.transition

import com.musicbox.app.player.transition.TransitionMath.HOP_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 节拍分析的正确性验证：用合成「鼓点轨」检查估速结果。
 *
 * 合成方式贴近真实音乐包络：每拍一个快速衰减的脉冲（kick 的能量包络形状），
 * 底鼓之间加一点持续低能量（贝斯/和弦），确保 onset 提取不会把持续音当成拍。
 */
class TransitionMathTest {

    /** 生成一组合成包络：[bpm] 速度、[seconds] 时长、[offsetMs] 第一拍偏移。 */
    private fun clickEnvelope(
        bpm: Float,
        seconds: Float,
        offsetMs: Int = 0,
        leadingSilenceMs: Int = 0,
    ): FloatArray {
        val frames = (seconds * 1000 / HOP_MS).toInt()
        val envelope = FloatArray(frames)
        val beatFrames = (60_000f / bpm / HOP_MS)
        val silenceFrames = leadingSilenceMs / HOP_MS
        var i = silenceFrames
        while (i < frames) {
            envelope[i] = 0.9f
            if (i + 1 < frames) envelope[i + 1] = 0.45f
            if (i + 2 < frames) envelope[i + 2] = 0.2f
            i += beatFrames.toInt().coerceAtLeast(1)
        }
        // 持续低能量垫底：模拟贝斯/和弦，不应被 onset 检测当成鼓点
        for (index in envelope.indices) {
            envelope[index] += 0.06f
        }
        // 首拍偏移：把整段脉冲平移，模拟「第一拍不在 0 秒」
        val shift = offsetMs / HOP_MS
        if (shift > 0) {
            val shifted = FloatArray(frames)
            for (index in 0 until frames - shift) {
                shifted[index + shift] = envelope.getOrElse(index) { 0f }
            }
            return shifted
        }
        return envelope
    }

    @Test
    fun `120 BPM 合成鼓点应估出 120`() {
        val rms = clickEnvelope(bpm = 120f, seconds = 16f)
        val onset = TransitionMath.onsetEnvelope(rms)
        val tempo = TransitionMath.estimateTempo(onset)
        assertNotNull(tempo)
        assertEquals(120f, tempo!!.bpm, 3f)
    }

    @Test
    fun `90 BPM 合成鼓点应估出 90`() {
        val rms = clickEnvelope(bpm = 90f, seconds = 16f)
        val onset = TransitionMath.onsetEnvelope(rms)
        val tempo = TransitionMath.estimateTempo(onset)
        assertNotNull(tempo)
        // 90 与 180 互为倍频，先验应该把结果压回 90 附近
        assertTrue("估出的 BPM 偏离过大：${tempo!!.bpm}", kotlin.math.abs(tempo.bpm - 90f) < 6f)
    }

    @Test
    fun `节拍相位应贴近第一拍`() {
        val rms = clickEnvelope(bpm = 120f, seconds = 16f, offsetMs = 200)
        val onset = TransitionMath.onsetEnvelope(rms)
        val tempo = TransitionMath.estimateTempo(onset)
        assertNotNull(tempo)
        // 120 BPM 下一拍 500ms，相位以拍为周期，200ms 与 200±500 等价，取模后应接近 200
        val offsetMs = tempo!!.beatOffsetFrames * HOP_MS
        val phase = offsetMs % 500
        assertTrue("相位偏离：$offsetMs ms", phase in 150..260 || phase in 740..860)
    }

    @Test
    fun `片头静音应被识别为起点而不是 0 秒`() {
        val rms = clickEnvelope(bpm = 120f, seconds = 16f, leadingSilenceMs = 2_000)
        val introFrame = TransitionMath.introStartFrame(rms)
        val introMs = introFrame * HOP_MS
        assertTrue("片头起点偏差：$introMs ms", kotlin.math.abs(introMs - 2_000) <= 200)
    }

    @Test
    fun `无节奏的平稳噪声不应该给出 BPM`() {
        val flat = FloatArray(800) { 0.1f + (it % 3) * 0.001f }
        val onset = TransitionMath.onsetEnvelope(flat)
        assertNull(TransitionMath.estimateTempo(onset))
    }

    @Test
    fun `淡化时长按拍数缩放且落在合理区间`() {
        val fast = TrackAnalysis(bpm = 160f, beatOffsetMs = 0, introStartMs = 0, analysedMs = 1, loudness = 1f)
        val slow = TrackAnalysis(bpm = 60f, beatOffsetMs = 0, introStartMs = 0, analysedMs = 1, loudness = 1f)
        assertEquals(1500L, TransitionMath.fadeOutMs(fast))
        assertEquals(3200L, TransitionMath.fadeOutMs(slow))
        assertEquals(1800L, TransitionMath.fadeOutMs(null))
        assertTrue(TransitionMath.fadeInMs(fast) < TransitionMath.fadeInMs(slow))
    }
}
