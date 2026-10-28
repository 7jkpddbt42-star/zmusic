package com.musicbox.app.player.transition

/**
 * 一首歌的「过渡分析」结果。
 *
 * 这些量都只需要曲首十几秒的音频就能得到，所以分析成本很低（解码 20 秒、不需要整首下载）：
 * - [bpm] / [beatOffsetMs]：节拍网格。淡化时长按「几拍」算，淡出点/淡入点贴到拍上，
 *   听起来才是「跟着音乐走」而不是固定秒数的硬切。
 * - [introStartMs]：第一个有明显声音的位置。很多歌开头有几秒静音或极轻的前奏，
 *   从这个位置开始淡入，音量抬升才落在真正的音乐上。
 */
data class TrackAnalysis(
    val bpm: Float,
    val beatOffsetMs: Long,
    val introStartMs: Long,
    val analysedMs: Long,
    val loudness: Float,
) {
    /** 一拍的时长；没测出 BPM 时返回 null，由调用方退回默认秒数。 */
    val beatMs: Float?
        get() = if (bpm in 40f..220f) 60_000f / bpm else null
}

/**
 * 分析结果缓存。
 *
 * 按歌曲 id 存（同一首歌换音源地址不影响结果）；失败的歌曲记进 [failed]，
 * 避免每次切歌都重试一遍下载解码。
 */
object TransitionAnalysisCache {
    private val analyses = HashMap<Long, TrackAnalysis>()
    private val failed = HashSet<Long>()

    @Synchronized
    fun get(songId: Long): TrackAnalysis? = analyses[songId]

    @Synchronized
    fun isKnown(songId: Long): Boolean = analyses.containsKey(songId) || failed.contains(songId)

    @Synchronized
    fun put(songId: Long, analysis: TrackAnalysis?) {
        if (analysis == null) failed.add(songId) else analyses[songId] = analysis
    }

    @Synchronized
    fun clear() {
        analyses.clear()
        failed.clear()
    }
}

/** 服务与界面之间的过渡命令名：手动切歌时由服务侧先淡出再切。 */
object TransitionCommands {
    const val ACTION_FADE_SKIP = "com.musicbox.app.command.FADE_SKIP"
    const val ARG_FORWARD = "forward"
}
