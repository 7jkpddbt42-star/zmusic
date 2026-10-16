package com.musicbox.app.lyric

import com.musicbox.app.model.LyricData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricParserTest {

    // §1.3 实测样本（id=5257138「屋顶」第一句）
    private val yrcSample =
        "{\"t\":0,\"c\":[{\"tx\":\"作曲: \"},{\"tx\":\"周杰伦\"}]}\n" +
            "{\"t\":1000,\"c\":[{\"tx\":\"作词: \"},{\"tx\":\"周杰伦\"}]}\n" +
            "[24080,5080](24080,280,0)半(24360,210,0)夜(24570,560,0)睡(25130,300,0)不(25430,350,0)着(25780,950,0)觉"

    private val lrcSample =
        "[00:24.080] 半夜睡不着觉 把心情哼成歌\n" +
            "[00:29.520] 只好到屋顶找另一个梦境"

    @Test
    fun `parses mixed json and yrc lines into per word timeline`() {
        val timeline = LyricParser.parse(LyricData(yrc = yrcSample, lrc = null, translation = null, romaji = null))

        assertEquals(3, timeline.lines.size)

        // 前两行是 JSON credits：有时间、整行文本、无逐字
        assertEquals(0L, timeline.lines[0].startMs)
        assertEquals("作曲: 周杰伦", timeline.lines[0].text)
        assertTrue(timeline.lines[0].words.isEmpty())
        assertEquals(1000L, timeline.lines[1].startMs)
        assertEquals("作词: 周杰伦", timeline.lines[1].text)

        // 第三行是 YRC：有逐字
        val sung = timeline.lines[2]
        assertEquals(24_080L, sung.startMs)
        assertEquals(5_080L, sung.durationMs)
        assertEquals("半夜睡不着觉", sung.text)
        assertEquals(6, sung.words.size)
        assertEquals(24_080L, sung.words[0].startMs)
        assertEquals(280L, sung.words[0].durationMs)
        assertEquals("半", sung.words[0].text)
        assertEquals("觉", sung.words[5].text)
        assertEquals(25_780L, sung.words[5].startMs)
    }

    @Test
    fun `fills missing durations from next line start`() {
        val timeline = LyricParser.parse(LyricData(yrc = null, lrc = lrcSample, translation = null, romaji = null))

        assertEquals(2, timeline.lines.size)
        assertEquals(24_080L, timeline.lines[0].startMs)
        // 无 duration → 用下一行起始填充
        assertEquals(29_520L - 24_080L, timeline.lines[0].durationMs)
        // 末行 → 兜底时长
        assertTrue(timeline.lines[1].durationMs >= 5_000L)
        assertTrue(timeline.lines[0].words.isEmpty())
    }

    @Test
    fun `parses lrc fraction digits of any length`() {
        val text = "[00:01] a\n[00:02.5] b\n[00:03.25] c\n[00:04.125] d"
        val timeline = LyricParser.parse(LyricData(yrc = null, lrc = text, translation = null, romaji = null))

        assertEquals(1_000L, timeline.lines[0].startMs)
        assertEquals(2_500L, timeline.lines[1].startMs)
        assertEquals(3_250L, timeline.lines[2].startMs)
        assertEquals(4_125L, timeline.lines[3].startMs)
    }

    @Test
    fun `splits a line with multiple timestamps`() {
        val text = "[00:01.000][01:20.000] 副歌"
        val timeline = LyricParser.parse(LyricData(yrc = null, lrc = text, translation = null, romaji = null))

        assertEquals(2, timeline.lines.size)
        assertEquals(1_000L, timeline.lines[0].startMs)
        assertEquals(80_000L, timeline.lines[1].startMs)
        assertEquals("副歌", timeline.lines[0].text)
        assertEquals("副歌", timeline.lines[1].text)
    }

    @Test
    fun `skips metadata tags and applies offset`() {
        // 译文实测含 [by:xxx]；offset 表示整体平移
        val text = "[by:某字幕组]\n[offset:+500]\n[00:10.000] hello"
        val timeline = LyricParser.parse(LyricData(yrc = null, lrc = text, translation = null, romaji = null))

        assertEquals(1, timeline.lines.size)
        assertEquals(9_500L, timeline.lines[0].startMs) // 10_000 - 500
        assertEquals("hello", timeline.lines[0].text)
    }

    @Test
    fun `ignores malformed and blank lines`() {
        val text = "\n\n这不是歌词\n[00:notatime] x\n[00:05.000] 正常"
        val timeline = LyricParser.parse(LyricData(yrc = null, lrc = text, translation = null, romaji = null))

        assertEquals(1, timeline.lines.size)
        assertEquals("正常", timeline.lines[0].text)
    }

    @Test
    fun `aligns translation and romaji by nearest timestamp within tolerance`() {
        val yrc = "[29860,3750](29860,110,0)L(29970,390,0)o(30360,510,0)s(30870,250,0)e(31120,400,0)r"
        val translation = "[00:29.850] 一如既往日复一日"
        val romaji = "[00:29.850] i tsu mo do o ri"

        val timeline = LyricParser.parse(LyricData(yrc = yrc, lrc = null, translation = translation, romaji = romaji))

        assertEquals(1, timeline.lines.size)
        // yrc 行 29_860 与译文 29_850 相差 10ms < 600ms 容差 → 对齐
        assertEquals("一如既往日复一日", timeline.lines[0].translation)
        assertEquals("i tsu mo do o ri", timeline.lines[0].romaji)
    }

    @Test
    fun `leaves translation null when no line is close enough`() {
        val yrc = "[29860,3750](29860,110,0)L(29970,390,0)o(30360,510,0)s(30870,250,0)e"
        val translation = "[01:20.000] 差很远"

        val timeline = LyricParser.parse(LyricData(yrc = yrc, lrc = null, translation = translation, romaji = null))

        assertNull(timeline.lines[0].translation)
    }

    @Test
    fun `falls back to lrc when yrc has no word lines`() {
        // yrc 字段存在但只有 credits、没有逐字行 → 必须退回 lrc 整行
        val yrc = "{\"t\":0,\"c\":[{\"tx\":\"作曲: \"}]}"
        val timeline = LyricParser.parse(LyricData(yrc = yrc, lrc = lrcSample, translation = null, romaji = null))

        assertEquals(2, timeline.lines.size)
        assertEquals("半夜睡不着觉 把心情哼成歌", timeline.lines[0].text)
        assertTrue(timeline.lines[0].words.isEmpty())
    }

    @Test
    fun `empty everything yields an empty timeline`() {
        val timeline = LyricParser.parse(LyricData(yrc = "", lrc = null, translation = null, romaji = null))
        assertTrue(timeline.isEmpty)
        assertEquals(-1, timeline.indexAt(1_000))
    }

    @Test
    fun `yrc word header with optional third number is tolerated`() {
        // 兼容只给两个数字的字头： [5000,1000](5000,300)你(5300,400)好
        val yrc = "[5000,1000](5000,300)你(5300,400)好"
        val timeline = LyricParser.parse(LyricData(yrc = yrc, lrc = null, translation = null, romaji = null))

        assertEquals(1, timeline.lines.size)
        assertEquals("你好", timeline.lines[0].text)
        assertEquals(2, timeline.lines[0].words.size)
        assertEquals(300L, timeline.lines[0].words[0].durationMs)
    }

    @Test
    fun `keeps spaces inside yrc words`() {
        // §1.3 实测：28_560 行里 "浪 " 后带空格
        val yrc = "[28560,5530](28560,560,0)浮(29120,470,0)沉(29590,960,0)随(30550,820,0)浪 (31370,610,0)只"
        val timeline = LyricParser.parse(LyricData(yrc = yrc, lrc = null, translation = null, romaji = null))

        assertEquals("浮沉随浪 只", timeline.lines[0].text)
        assertEquals("浪 ", timeline.lines[0].words[3].text)
    }
}
