package com.musicbox.app.player.transition

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 过渡分析用到的纯数学部分（与 Android 解码解耦，方便单测）。
 *
 * 参考的公开做法与 aubio / librosa 的节奏分析一致：
 * 1. 把音频切成 20ms 一帧的 RMS 包络；
 * 2. 取「上升沿」得到 onset 强度（能量通量），持续音不会产生假的鼓点；
 * 3. 对 onset 序列做自相关，60–190 BPM 范围内找最强周期；
 * 4. 用周期折叠（comb filter）找第一拍相位，得到节拍网格。
 *
 * 这套方法不做神经网络，但对流行乐/摇滚/电子这类节拍稳定的曲子足够稳，
 * 而且完全在手机上离线跑，不需要模型文件与额外依赖。
 */
internal object TransitionMath {

    /** 包络帧长（毫秒）。20ms 在手机上足以分辨鼓点，也不会让自相关计算量过大。 */
    const val HOP_MS = 20

    private const val MIN_BPM = 60f
    private const val MAX_BPM = 190f

    /** 节拍猜测：BPM + 第一拍所在帧。 */
    data class Tempo(val bpm: Float, val beatOffsetFrames: Int)

    /**
     * RMS 包络 → onset 强度。
     *
     * 只保留能量上升的部分：和弦持续音、长音、混响尾巴不会产生峰值，
     * 这样自相关找出来的周期才是鼓点周期而不是别的周期性。
     */
    fun onsetEnvelope(rms: FloatArray): FloatArray {
        val peak = rms.maxOrNull() ?: return FloatArray(0)
        if (peak <= 1e-5f) return FloatArray(0)
        val norm = FloatArray(rms.size) { rms[it] / peak }
        val flux = FloatArray(norm.size)
        for (i in 1 until norm.size) {
            flux[i] = (norm[i] - norm[i - 1]).coerceAtLeast(0f)
        }
        // 轻微平滑：去掉单帧毛刺，保留鼓点的陡峭边缘
        val out = FloatArray(flux.size)
        for (i in flux.indices) {
            val left = flux.getOrElse(i - 1) { flux[i] }
            val right = flux.getOrElse(i + 1) { flux[i] }
            out[i] = left * 0.25f + flux[i] * 0.5f + right * 0.25f
        }
        return out
    }

    /**
     * 自相关估速。
     *
     * [onset] 为 onset 强度序列，[hopMs] 为每帧毫秒数。
     * 返回 null 表示能量太散、没有可信的周期（例如纯人声、环境音）。
     */
    fun estimateTempo(onset: FloatArray, hopMs: Int = HOP_MS): Tempo? {
        if (onset.size < MIN_FRAMES_FOR_TEMPO) return null
        val framesPerSecond = 1000f / hopMs
        val minLag = (framesPerSecond * 60f / MAX_BPM).roundToInt().coerceAtLeast(2)
        val maxLag = (framesPerSecond * 60f / MIN_BPM).roundToInt()
        if (maxLag >= onset.size) return null

        var bestLag = -1
        var bestScore = 0f
        var scoreTotal = 0f
        var scoreCount = 0
        val scores = FloatArray(maxLag + 2)
        for (lag in minLag..maxLag) {
            var product = 0f
            var energy = 0f
            for (i in 0 until onset.size - lag) {
                product += onset[i] * onset[i + lag]
                energy += onset[i] * onset[i]
            }
            if (energy <= 1e-6f) continue
            val bpm = 60f * framesPerSecond / lag
            val score = (product / energy) * tempoPrior(bpm)
            scores[lag] = score
            scoreTotal += score
            scoreCount++
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        if (bestLag < 0) return null

        // 平稳信号的自相关在所有滞后上都差不多高（最大/平均≈1），没有真正的周期峰。
        // 只有明显高于平均水平的峰才当作「有节拍」，否则宁可不给结果。
        val meanScore = if (scoreCount > 0) scoreTotal / scoreCount else 0f
        if (bestScore < meanScore * MIN_PEAK_RATIO) return null

        // 抛物线插值：真实节拍很少正好落在整数帧上，插值后 BPM 抖动明显更小
        val left = scores.getOrElse(bestLag - 1) { 0f }
        val right = scores.getOrElse(bestLag + 1) { 0f }
        val denom = left - 2f * bestScore + right
        val refinedLag = if (abs(denom) > 1e-6f) {
            bestLag + 0.5f * (left - right) / denom
        } else {
            bestLag.toFloat()
        }.coerceIn(minLag.toFloat(), maxLag.toFloat())

        val bpm = 60f * framesPerSecond / refinedLag
        val lagFrames = refinedLag.roundToInt().coerceAtLeast(2)
        return Tempo(bpm = bpm, beatOffsetFrames = beatOffsetFrames(onset, lagFrames))
    }

    /**
     * 节拍相位：把 onset 序列按周期折叠，落在同一相位上的能量之和最大的那个相位就是第一拍。
     * 允许 ±1 帧抖动，避免录制的细微偏差把一次鼓点拆到两个相位里。
     */
    fun beatOffsetFrames(onset: FloatArray, lagFrames: Int): Int {
        if (lagFrames <= 0 || onset.isEmpty()) return 0
        var bestOffset = 0
        var bestSum = -1f
        for (offset in 0 until lagFrames) {
            var sum = 0f
            var i = offset
            while (i < onset.size) {
                sum += onset[i] +
                    0.5f * onset.getOrElse(i - 1) { 0f } +
                    0.5f * onset.getOrElse(i + 1) { 0f }
                i += lagFrames
            }
            if (sum > bestSum) {
                bestSum = sum
                bestOffset = offset
            }
        }
        return bestOffset
    }

    /**
     * 第一个「有声音」的帧。阈值取峰值的 8%，并要求连续三帧都超过阈值，
     * 避免单帧噪声把起点误判到前奏中间。
     */
    fun introStartFrame(rms: FloatArray): Int {
        val peak = rms.maxOrNull() ?: return 0
        if (peak <= 1e-5f) return 0
        val threshold = max(peak * 0.08f, 0.004f)
        for (i in rms.indices) {
            val next1 = rms.getOrElse(i + 1) { 1f }
            val next2 = rms.getOrElse(i + 2) { 1f }
            if (rms[i] > threshold && next1 > threshold * 0.6f && next2 > threshold * 0.6f) {
                return i
            }
        }
        return 0
    }

    /** 淡出时长：4 拍，限制在 1.4–3.2 秒；没有 BPM 时用 1.8 秒。 */
    fun fadeOutMs(analysis: TrackAnalysis?): Long =
        analysis?.beatMs?.let { (it * 4f).toLong().coerceIn(1400L, 3200L) } ?: 1800L

    /** 淡入时长：2 拍，限制在 0.8–2.0 秒；比淡出短，避免新歌半天进不了正题。 */
    fun fadeInMs(analysis: TrackAnalysis?): Long =
        analysis?.beatMs?.let { (it * 2f).toLong().coerceIn(800L, 2000L) } ?: 1200L

    /**
     * 人耳对 60–190 BPM 的偏好并不是均匀的：以 120 BPM 为中心做对数距离加权，
     * 能显著减少「同一段鼓点被判成 80 还是 160」这类倍频错误。
     */
    private fun tempoPrior(bpm: Float): Float {
        if (bpm <= 0f) return 0f
        val octaves = ln(bpm / PREFERRED_BPM) / ln(2f)
        return 1f / (1f + 0.9f * octaves * octaves)
    }

    private const val PREFERRED_BPM = 120f

    /** 至少要有 4 秒包络才敢估速（200 帧）。 */
    private const val MIN_FRAMES_FOR_TEMPO = 200

    /** 自相关峰相对平均值的下限：低于它就认为没有可用的节拍。 */
    private const val MIN_PEAK_RATIO = 1.35f
}
