package com.musicbox.app.lyric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricTimelineTest {

    private fun line(start: Long, dur: Long) = LyricLine(start, dur, "L$start")

    private val timeline = LyricTimeline(
        listOf(
            line(10_000, 2_000),
            line(13_000, 1_000),
            line(15_000, 3_000),
        ),
    )

    @Test
    fun `indexAt returns minus one before the first line`() {
        assertEquals(-1, timeline.indexAt(0))
        assertEquals(-1, timeline.indexAt(9_999))
    }

    @Test
    fun `indexAt returns the last line whose start has passed`() {
        assertEquals(0, timeline.indexAt(10_000))
        assertEquals(0, timeline.indexAt(12_999))
        assertEquals(1, timeline.indexAt(13_000))
        assertEquals(2, timeline.indexAt(99_999))
    }

    @Test
    fun `indexAt keeps previous line during a gap`() {
        // 14_000 落在第 1 行结束(14_000)与第 2 行开始(15_000)之间的空隙 → 仍算第 1 行
        assertEquals(1, timeline.indexAt(14_000))
        assertEquals(1, timeline.indexAt(14_999))
    }

    @Test
    fun `lineAt returns null before first line`() {
        assertNull(timeline.lineAt(0))
        assertEquals(1, timeline.lineAt(13_500)!!.startMs / 13_000)
    }

    @Test
    fun `empty timeline always returns minus one`() {
        val empty = LyricTimeline(emptyList())
        assertEquals(-1, empty.indexAt(0))
        assertEquals(-1, empty.indexAt(10_000))
        assertNull(empty.lineAt(10_000))
    }

    @Test
    fun `wordProgress climbs from zero to one`() {
        val word = LyricWord(startMs = 1_000, durationMs = 400, text = "屋")
        assertEquals(0f, wordProgress(500, word), 0.0001f)
        assertEquals(0f, wordProgress(1_000, word), 0.0001f)
        assertEquals(0.5f, wordProgress(1_200, word), 0.0001f)
        assertEquals(1f, wordProgress(1_400, word), 0.0001f)
        assertEquals(1f, wordProgress(9_999, word), 0.0001f)
    }

    @Test
    fun `wordProgress never divides by zero`() {
        val word = LyricWord(startMs = 1_000, durationMs = 0, text = "a")
        assertEquals(0f, wordProgress(1_000, word), 0.0001f)
        assertEquals(1f, wordProgress(1_001, word), 0.0001f)
    }

    @Test
    fun `currentLineTextAt returns empty before the first line`() {
        assertEquals("", timeline.currentLineTextAt(0, includeTranslation = true))
        assertEquals("", timeline.currentLineTextAt(9_999, includeTranslation = false))
    }

    @Test
    fun `currentLineTextAt returns the line text when hit`() {
        assertEquals("L10000", timeline.currentLineTextAt(10_000, includeTranslation = false))
        assertEquals("L13000", timeline.currentLineTextAt(13_500, includeTranslation = false))
    }

    @Test
    fun `currentLineTextAt joins translation when present`() {
        val t = LyricTimeline(
            listOf(LyricLine(startMs = 1_000, durationMs = 1_000, text = "原词", translation = "译文")),
        )
        assertEquals("原词 · 译文", t.currentLineTextAt(1_500, includeTranslation = true))
        // 关掉译文开关时只返回原文
        assertEquals("原词", t.currentLineTextAt(1_500, includeTranslation = false))
    }

    @Test
    fun `currentLineTextAt ignores blank translation`() {
        val t = LyricTimeline(
            listOf(
                LyricLine(startMs = 1_000, durationMs = 1_000, text = "原词", translation = null),
                LyricLine(startMs = 3_000, durationMs = 1_000, text = "第二", translation = "  "),
            ),
        )
        assertEquals("原词", t.currentLineTextAt(1_500, includeTranslation = true))
        assertEquals("第二", t.currentLineTextAt(3_500, includeTranslation = true))
    }

    @Test
    fun `currentLineTextAt keeps previous line during a gap`() {
        // 14_500 落在第 1 行结束(14_000)与第 2 行开始(15_000)之间的空隙 → 仍返回第 1 行
        assertEquals("L13000", timeline.currentLineTextAt(14_500, includeTranslation = false))
    }

    @Test
    fun `nextLineTextAt returns the line after the current one`() {
        // 第 0 行（10_000 起）→ 下一行是第 1 行
        assertEquals("L13000", timeline.nextLineTextAt(10_000, includeTranslation = false))
        // 空隙期仍算第 0 行 → 下一行不变
        assertEquals("L13000", timeline.nextLineTextAt(12_999, includeTranslation = false))
        // 第 1 行（13_500）→ 下一行是第 2 行
        assertEquals("L15000", timeline.nextLineTextAt(13_500, includeTranslation = false))
    }

    @Test
    fun `nextLineTextAt returns empty on the last line`() {
        assertEquals("", timeline.nextLineTextAt(15_000, includeTranslation = false))
        assertEquals("", timeline.nextLineTextAt(99_999, includeTranslation = true))
    }

    @Test
    fun `nextLineTextAt treats before-first as index minus one so next is the first line`() {
        assertEquals("L10000", timeline.nextLineTextAt(0, includeTranslation = false))
        assertEquals("L10000", timeline.nextLineTextAt(9_999, includeTranslation = false))
    }

    @Test
    fun `nextLineTextAt joins translation and ignores blank translation`() {
        val t = LyricTimeline(
            listOf(
                LyricLine(startMs = 1_000, durationMs = 1_000, text = "第一", translation = "译一"),
                LyricLine(startMs = 3_000, durationMs = 1_000, text = "第二", translation = "译二"),
                LyricLine(startMs = 5_000, durationMs = 1_000, text = "第三", translation = "  "),
            ),
        )
        // 第 0 行时下一行（第 1 行）带译文 → 拼接
        assertEquals("第二 · 译二", t.nextLineTextAt(1_500, includeTranslation = true))
        // 关掉译文开关只取原文
        assertEquals("第二", t.nextLineTextAt(1_500, includeTranslation = false))
        // 第 1 行时下一行（第 2 行）译文为空白 → 只取原文
        assertEquals("第三", t.nextLineTextAt(3_500, includeTranslation = true))
    }

    @Test
    fun `nextLineTextAt on an empty timeline returns empty`() {
        assertEquals("", LyricTimeline(emptyList()).nextLineTextAt(1_000, includeTranslation = true))
    }
}
