package com.musicbox.app.data

import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppLogTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `log appends lines with tag`() {
        val log = AppLog(File(tmp.root, "a.log")) { 1_700_000_000_000 }
        log.log("netease", "解析失败", IllegalStateException("boom"))
        val text = log.readAll()
        assertTrue(text.contains("[netease] 解析失败"))
        assertTrue(text.contains("IllegalStateException"))
    }

    @Test
    fun `readAll is empty when nothing logged`() {
        assertTrue(AppLog(File(tmp.root, "b.log")).readAll().isEmpty())
    }

    @Test
    fun `rolling keeps file under limit`() {
        val file = File(tmp.root, "c.log")
        val log = AppLog(file, maxBytes = 200, clock = { 1L })
        repeat(50) { log.log("t", "x".repeat(40)) }
        assertTrue("实际大小=${file.length()}", file.length() <= 400)
    }

    @Test
    fun `clear empties file`() {
        val log = AppLog(File(tmp.root, "d.log"))
        log.log("t", "m")
        log.clear()
        assertTrue(log.readAll().isEmpty())
    }
}
