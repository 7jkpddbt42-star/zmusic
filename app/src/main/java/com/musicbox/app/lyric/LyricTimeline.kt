package com.musicbox.app.lyric

import com.musicbox.app.model.LyricData

/**
 * 一个逐字单元。YRC 的每个 `(start,dur,0)text` 就是一个 LyricWord，
 * text 可能含多个字符（也含空格）——CJK 一般一字一词，拉丁文一般一词一 Word。
 */
data class LyricWord(
    val startMs: Long,
    val durationMs: Long,
    val text: String,
) {
    val endMs: Long get() = startMs + durationMs
}

/**
 * 一行歌词。
 * words 为空 → 只有逐行时间（LRC 降级），整行高亮；
 * words 非空 → 可做逐字填充动画。
 */
data class LyricLine(
    val startMs: Long,
    val durationMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val translation: String? = null,
    val romaji: String? = null,
) {
    val endMs: Long get() = startMs + durationMs
}

/**
 * 整首歌的歌词时间轴。lines 按 startMs 升序、无重叠、每行 durationMs > 0。
 */
data class LyricTimeline(
    val lines: List<LyricLine>,
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    /**
     * 当前位置对应的"当前行"下标。
     * - 首行开始之前 → -1（还没到第一句）
     * - 行与行之间的空隙 → 返回最近**已开始**的那一行（卡拉OK惯例：上一句保持高亮到下一句开始）
     */
    fun indexAt(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        if (positionMs < lines.first().startMs) return -1
        var low = 0
        var high = lines.size - 1
        var answer = 0
        while (low <= high) {
            val mid = (low + high) / 2
            if (lines[mid].startMs <= positionMs) {
                answer = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return answer
    }

    fun lineAt(positionMs: Long): LyricLine? = lines.getOrNull(indexAt(positionMs))
}

/**
 * 某个字（词）在给定播放位置下的"填充进度"，0f~1f。
 * 只做纯数学，供 UI 每帧调用——动画的丝滑就靠它每帧被重算。
 */
fun wordProgress(positionMs: Long, word: LyricWord): Float {
    if (positionMs <= word.startMs) return 0f
    if (positionMs >= word.endMs) return 1f
    val span = word.durationMs.coerceAtLeast(1L).toFloat()
    return ((positionMs - word.startMs).toFloat() / span).coerceIn(0f, 1f)
}

/** 未使用，仅供 LyricData 的可见性提醒：解析器入口吃的是 LyricData。 */
internal typealias RawLyric = LyricData
