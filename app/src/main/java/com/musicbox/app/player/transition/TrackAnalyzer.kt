package com.musicbox.app.player.transition

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlin.math.sqrt

/**
 * 曲首音频分析器：把播放地址解码成 PCM，算 RMS 包络，再交给 [TransitionMath] 估速。
 *
 * 只分析曲首 [MAX_ANALYSE_MS]，原因：
 * - 节拍与片头起点都在开头的十几秒里，没必要下载整首；
 * - 走 HTTP 渐进下载时，解码器只需要开头那一段字节，耗时可忽略。
 *
 * 不使用任何第三方 DSP 库：Android 自带的 MediaExtractor / MediaCodec 就能完成
 * 解码，纯 Kotlin 完成包络与自相关，避免引入 NDK 与模型文件。
 */
class TrackAnalyzer {

    /** 分析失败（无音轨 / 解码异常 / 网络失败）时返回 null，调用方退回固定淡化时长。 */
    fun analyze(url: String): TrackAnalysis? {
        val envelope = runCatching { decodeEnvelope(url) }.getOrNull() ?: return null
        if (envelope.rms.isEmpty()) return null

        val onset = TransitionMath.onsetEnvelope(envelope.rms)
        val tempo = if (onset.isNotEmpty()) TransitionMath.estimateTempo(onset) else null
        val introFrame = TransitionMath.introStartFrame(envelope.rms)

        return TrackAnalysis(
            bpm = tempo?.bpm ?: 0f,
            beatOffsetMs = (tempo?.beatOffsetFrames ?: 0) * TransitionMath.HOP_MS.toLong(),
            introStartMs = introFrame * TransitionMath.HOP_MS.toLong(),
            analysedMs = envelope.rms.size * TransitionMath.HOP_MS.toLong(),
            loudness = envelope.rms.maxOrNull() ?: 0f,
        )
    }

    private class Envelope(val rms: FloatArray)

    private fun decodeEnvelope(url: String): Envelope? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(url, emptyMap<String, String>())
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val maxFrames = (MAX_ANALYSE_MS / TransitionMath.HOP_MS).toInt()
            val rms = FloatArray(maxFrames)
            var frameCount = 0

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var channels = 1
            var sampleRate = 44_100
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var hopSamples = sampleRate * TransitionMath.HOP_MS / 1000
            var hopSum = 0.0
            var hopCount = 0

            while (!outputDone && frameCount < maxFrames) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val buffer = codec.getInputBuffer(inputIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = codec.outputFormat
                        channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 1).coerceAtLeast(1)
                        sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        pcmEncoding = if (out.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            out.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else {
                            AudioFormat.ENCODING_PCM_16BIT
                        }
                        hopSamples = (sampleRate * TransitionMath.HOP_MS / 1000).coerceAtLeast(1)
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                    else -> {
                        if (outputIndex >= 0) {
                            if (info.size > 0 && frameCount < maxFrames) {
                                val buffer = codec.getOutputBuffer(outputIndex)!!
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                val result = accumulate(
                                    buffer = buffer,
                                    pcmEncoding = pcmEncoding,
                                    channels = channels,
                                    hopSamples = hopSamples,
                                    hopSum = hopSum,
                                    hopCount = hopCount,
                                    rms = rms,
                                    frameCount = frameCount,
                                )
                                hopSum = result.hopSum
                                hopCount = result.hopCount
                                frameCount = result.frameCount
                            }
                            codec.releaseOutputBuffer(outputIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                outputDone = true
                            }
                        }
                    }
                }
            }

            if (frameCount == 0) return null
            return Envelope(rms.copyOf(frameCount))
        } finally {
            codec?.let {
                runCatching { it.stop() }
                it.release()
            }
            extractor.release()
        }
    }

    private class Accumulated(val hopSum: Double, val hopCount: Int, val frameCount: Int)

    /** 把一块 PCM 叠加进包络；按 [channels] 取平均得到单声道，逐帧算 RMS。 */
    private fun accumulate(
        buffer: java.nio.ByteBuffer,
        pcmEncoding: Int,
        channels: Int,
        hopSamples: Int,
        hopSum: Double,
        hopCount: Int,
        rms: FloatArray,
        frameCount: Int,
    ): Accumulated {
        var sum = hopSum
        var count = hopCount
        var frames = frameCount

        when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val floats = buffer.asFloatBuffer()
                while (floats.hasRemaining() && frames < rms.size) {
                    var sample = 0f
                    repeat(channels) {
                        if (floats.hasRemaining()) sample += floats.get()
                    }
                    sample /= channels
                    sum += (sample * sample).toDouble()
                    count++
                    if (count >= hopSamples) {
                        rms[frames++] = sqrt(sum / count).toFloat()
                        sum = 0.0
                        count = 0
                    }
                }
            }

            else -> {
                val shorts = buffer.asShortBuffer()
                while (shorts.hasRemaining() && frames < rms.size) {
                    var sample = 0f
                    repeat(channels) {
                        if (shorts.hasRemaining()) sample += shorts.get() / 32768f
                    }
                    sample /= channels
                    sum += (sample * sample).toDouble()
                    count++
                    if (count >= hopSamples) {
                        rms[frames++] = sqrt(sum / count).toFloat()
                        sum = 0.0
                        count = 0
                    }
                }
            }
        }
        return Accumulated(sum, count, frames)
    }

    private companion object {
        /** 只分析曲首 20 秒：足够估速与找片头，又不会为了分析下载半首歌。 */
        const val MAX_ANALYSE_MS = 20_000L
        const val TIMEOUT_US = 10_000L
    }
}
