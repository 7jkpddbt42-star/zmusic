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

    /**
     * 版权/制作信息行。部分音源把 "OP: xxx"、"作词: xxx" 之类的制作名单
     * 直接混在带时间戳的正文里，落到界面就会变成"歌词"显示出来。
     */
    private val creditKeyRegex = Regex(
        """(?:^|[\s/&、，·+＋人])(作词|作曲|编曲|制作|出品|发行|监制|统筹|策划|录音|混响|混音|母带|和声|和音|伴唱|配唱|人声|吉他|贝斯|鼓|键盘|弦乐|钢琴|架子鼓|打击乐|手鼓|非洲鼓|笛子|长笛|短笛|竹笛|二胡|古筝|琵琶|中阮|箫|唢呐|口琴|手风琴|小提琴|中提琴|大提琴|低音提琴|萨克斯|小号|长号|圆号|合成器|音效|采样|后期|混录|缩混|监唱|录音室|录音师|混音师|母带师|美术|封面|设计|插画|视觉|企划|宣发|推广|文案|翻译|校对|鸣谢|感谢|特别鸣谢|编辑|助理|处理|编写|指导|团队|工作室|版权|词|曲|编|op|sp|producer|composer|lyricist|arranger|mixing|mastering|publisher|label|engineer|editor|supervisor|executive|artwork|design|vocal)(?:$|[\s/&、，·+＋人])""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * 冒号前最多这么长才算制作信息。真正的歌词几乎不会在短前缀里塞进
     * "混音/母带" 之类的词，而 "混音&母带：周天澈" 这种复合写法能被稳定识别。
     */
    private const val CREDIT_PREFIX_MAX = 20

    /**
     * 角色词被修饰后落在头部末尾时（"人声编辑"、"母带后期处理"、"和声编写"），
     * 左侧已经不是分隔符，上一条正则匹配不到；只要头部以角色词结尾就同样算制作信息。
     */
    private val creditSuffixRegex = Regex(
        """(作词|作曲|编曲|制作|出品|发行|监制|统筹|策划|录音|混响|混音|母带|和声|和音|伴唱|配唱|人声|编辑|助理|处理|编写|指导|后期|混录|缩混|监唱|团队|工作室|词|曲|编)$""",
    )

    /** 网易云常见的版权声明行（没有冒号），同样不该出现在歌词里。 */
    private val boilerplateRegex = Regex(
        """未经.{0,6}许可|不得翻唱|不得翻录|本歌曲来自|版权归.{0,12}所有""",
    )

    private fun isCreditLine(text: String): Boolean {
        val line = text.trim()
        if (boilerplateRegex.containsMatchIn(line)) return true
        // 兼容 "混音&母带："、"作词/作曲："、"词 :" 等复合或带空格的写法
        val colon = line.indexOfFirst { it == ':' || it == '：' }
        if (colon <= 0) return false
        val head = line.substring(0, colon)
        return head.length <= CREDIT_PREFIX_MAX &&
            (creditKeyRegex.containsMatchIn(head) || creditSuffixRegex.containsMatchIn(head))
    }

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

        // 制作名单不是歌词：过滤后再对齐，避免迷你条/歌词页开头出现 "OP: xxx"
        val lyric = main.filterNot { isCreditLine(it.text) }
        if (lyric.isEmpty()) return LyricTimeline(emptyList())

        val translation = parseLrc(data.translation).filterNot { isCreditLine(it.text) }
        val romaji = parseLrc(data.romaji).filterNot { isCreditLine(it.text) }

        val withAlignment = lyric.map { line ->
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
