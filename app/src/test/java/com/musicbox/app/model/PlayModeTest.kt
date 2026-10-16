package com.musicbox.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayModeTest {

    @Test
    fun `cycles through three modes in order`() {
        assertEquals(PlayMode.REPEAT_ONE, PlayMode.ORDER.next())
        assertEquals(PlayMode.SHUFFLE, PlayMode.REPEAT_ONE.next())
        assertEquals(PlayMode.ORDER, PlayMode.SHUFFLE.next())
    }

    @Test
    fun `every mode has a chinese label`() {
        assertEquals(listOf("顺序", "单曲循环", "随机"), PlayMode.entries.map { it.label })
    }
}
