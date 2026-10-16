package com.musicbox.app.lyric

import com.google.gson.Gson
import com.musicbox.app.model.LyricData

/**
 * 网易云歌词解析（格式规则见计划 §1.3）。
 *
 * 输入是三种格式的混排文本：
 *  A. 新版 JSON 行 : {"t":0,"c":[{"tx":"作曲: "},{"tx":"周杰伦"}]}   —— 无逐字
 *  B. 经典 YRC     : [行起始,行时长](字起始,字时长,0)字(字起始,字时长,0)字…  —— 有逐字
 *  C. 经典 LRC     : [mm:ss.xxx] 文本                              —— 无逐字
 * 解析职责全在这里，音源层只负责把原始文本搬回来（见 MusicProvider.getLyric）。
 */
object LyricParser {

    /** 译文/罗马音与主行的时间戳容差（实测相差 ±10~50ms，见 §1.4）。 */
    const val ALIGN_TOLERANCE_MS = 600L

    /** 末行没有后继行时的兜底时长。 */
    private const val TAIL_DURATION_MS = 5_000L

    /** 一行至少持续这么久，避免 next-start 与自身 start 相同时出现 0 时长。 */
    private const val MIN_LINE_MS = 200L

    private val gson = Gson()

    /** 新版 JSON 行。 */
    private data class JsonLine(val t: Long = 0L, val c: List<JsonCell>? = null)
    private data class JsonCell(val tx: String? = null)

    /** YRC 行头：[起始,时长] 或 [起始,时长,?]。 */
    private val yrcLineRegex = Regex("""^\[(\d+),(\d+)(?:,(\d+))?\](.*)$""")

    /** YRC 字头：(起始,时长) 或 (起始,时长,?) ，随后到下一个 ( 之前是文本。 */
    private val yrcWordRegex = Regex("""\((\d+),(\d+)(?:,(\d+))?\)([^()]*)""")

    /** LRC 行头：[mm:ss]、[mm:ss.x]、[mm:ss.xx]、[mm:ss.xxx]、[mm:ss:xxx]。 */
    private val lrcTagRegex = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]""")

    /** LRC 元信息标签：[by:xxx] [ti:xxx] [offset:±ms] … */
    private val metaRegex = Regex("""^\[([A-Za-z]+):(.*)]$""")

    /** 解析中间态：durationMs 为 null 表示"待用下一行起始填充"。 */
    private data class Parsed(
        var startMs: Long,
        val durationMs: Long?,
        val text: String,
        val words: List<LyricWord>,
        val translation: String? = null,
        val romaji: String? = null,
    )

    fun parse(data: LyricData): LyricTimeline {
        // 主歌词优先 YRC；只有 YRC 里真的存在逐字行时才用它，否则退回 LRC（§1.5）
        val main = parseYrc(data.yrc)
            .takeIf { list -> list.any { it.words.isNotEmpty() } }
            ?: parseLrc(data.lrc)

        if (main.isEmpty()) return LyricTimeline(emptyList())

        val translation = parseLrc(data.translation)
        val romaji = parseLrc(data.romaji)

        val withAlignment = main.map { line ->
            line.copy(
                translation = nearestText(translation, line.startMs),
                romaji = nearestText(romaji, line.startMs),
            )
        }
        return LyricTimeline(resolveDurations(withAlignment))
    }

    // ── 格式 A + B 混排 ─────────────────────────────────────────

    private fun parseYrc(text: String?): List<Parsed> {
        if (text.isNullOrBlank()) return emptyList()
        val out = ArrayList<Parsed>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            parseJsonLine(line)?.let { out += it; continue }
            parseYrcLine(line)?.let { out += it }
        }
        return out
    }

    private fun parseJsonLine(line: String): Parsed? {
        if (!line.startsWith("{")) return null
        val dto = runCatching { gson.fromJson(line, JsonLine::class.java) }.getOrNull() ?: return null
        val text = dto.c?.mapNotNull { it.tx }?.joinToString("").orEmpty()
        if (text.isBlank()) return null
        return Parsed(dto.t, durationMs = null, text = text, words = emptyList())
    }

    private fun parseYrcLine(line: String): Parsed? {
        val match = yrcLineRegex.find(line) ?: return null
        val startMs = match.groupValues[1].toLong()
        val durationMs = match.groupValues[2].toLong()
        val words = yrcWordRegex.findAll(match.groupValues[4]).map { word ->
            LyricWord(
                startMs = word.groupValues[1].toLong(),
                durationMs = word.groupValues[2].toLong(),
                text = word.groupValues[4],
            )
        }.toList()
        if (words.isEmpty()) return null
        return Parsed(startMs, durationMs, words.joinToString("") { it.text }, words)
    }

    // ── 格式 A + C 混排 ─────────────────────────────────────────

    private fun parseLrc(text: String?): List<Parsed> {
        if (text.isNullOrBlank()) return emptyList()
        val out = ArrayList<Parsed>()
        var offsetMs = 0L

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            parseJsonLine(line)?.let { out += it; continue }

            val meta = metaRegex.find(line)
            if (meta != null) {
                if (meta.groupValues[1].equals("offset", ignoreCase = true)) {
                    offsetMs = meta.groupValues[2].trim().toLongOrNull() ?: 0L
                }
                continue
            }

            val tags = lrcTagRegex.findAll(line).toList()
            if (tags.isEmpty()) continue
            val content = line.substring(tags.last().range.last + 1).trim()
            if (content.isEmpty()) continue
            for (tag in tags) {
                out += Parsed(
                    startMs = tag.groupValues[1].toLong() * 60_000L +
                        tag.groupValues[2].toLong() * 1_000L +
                        fractionToMs(tag.groupValues[3]),
                    durationMs = null,
                    text = content,
                    words = emptyList(),
                )
            }
        }

        if (offsetMs == 0L) return out
        return out.map { it.also { p -> p.startMs -= offsetMs } }
    }

    private fun fractionToMs(fraction: String): Long {
        if (fraction.isEmpty()) return 0L
        return when (fraction.length) {
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            else -> fraction.substring(0, 3).toLong()
        }
    }

    // ── 收尾 ────────────────────────────────────────────────────

    private fun resolveDurations(parsed: List<Parsed>): List<LyricLine> {
        val sorted = parsed.sortedBy { it.startMs }
        return sorted.mapIndexed { index, p ->
            val nextStart = sorted.getOrNull(index + 1)?.startMs
            val duration = p.durationMs?.takeIf { it > 0L }
                ?: nextStart?.let { it - p.startMs }
                ?: TAIL_DURATION_MS
            LyricLine(
                startMs = p.startMs,
                durationMs = duration.coerceAtLeast(MIN_LINE_MS),
                text = p.text,
                words = p.words,
                translation = p.translation,
                romaji = p.romaji,
            )
        }
    }

    private fun nearestText(candidates: List<Parsed>, targetMs: Long): String? {
        if (candidates.isEmpty()) return null
        var best: Parsed? = null
        var bestDelta = Long.MAX_VALUE
        for (candidate in candidates) {
            val delta = kotlin.math.abs(candidate.startMs - targetMs)
            if (delta < bestDelta) {
                bestDelta = delta
                best = candidate
            }
        }
        return if (bestDelta <= ALIGN_TOLERANCE_MS) best?.text?.takeIf { it.isNotBlank() } else null
    }
}
