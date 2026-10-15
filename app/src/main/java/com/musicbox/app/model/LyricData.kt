package com.musicbox.app.model

/**
 * 歌词原始载荷。MI-3 起由音源填充并解析为时间轴。
 * 三个字段都为原始文本，解析职责在 lyric 层，音源不解析。
 */
data class LyricData(
    val yrc: String?,          // 逐字歌词（YRC）
    val lrc: String?,          // 逐行歌词（LRC）
    val translation: String?,  // 翻译
    val romaji: String?,       // 罗马音
)
