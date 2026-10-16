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
}
