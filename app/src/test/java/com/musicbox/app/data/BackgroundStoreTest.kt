package com.musicbox.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** [BackgroundStore.extensionFor] 的纯函数单测（不依赖 Android framework）。 */
class BackgroundStoreTest {

    @Test
    fun `jpeg maps to jpg`() {
        assertEquals("jpg", BackgroundStore.extensionFor("image/jpeg", "png"))
    }

    @Test
    fun `png maps to png`() {
        assertEquals("png", BackgroundStore.extensionFor("image/png", "jpg"))
    }

    @Test
    fun `webp maps to webp`() {
        assertEquals("webp", BackgroundStore.extensionFor("image/webp", "jpg"))
    }

    @Test
    fun `mp4 maps to mp4`() {
        assertEquals("mp4", BackgroundStore.extensionFor("video/mp4", "webm"))
    }

    @Test
    fun `webm maps to webm`() {
        assertEquals("webm", BackgroundStore.extensionFor("video/webm", "mp4"))
    }

    @Test
    fun `3gpp maps to 3gp`() {
        assertEquals("3gp", BackgroundStore.extensionFor("video/3gpp", "mp4"))
    }

    @Test
    fun `null mime returns fallback`() {
        assertEquals("jpg", BackgroundStore.extensionFor(null, "jpg"))
        assertEquals("mp4", BackgroundStore.extensionFor(null, "mp4"))
    }

    @Test
    fun `unknown mime returns fallback`() {
        assertEquals("bin", BackgroundStore.extensionFor("application/octet-stream", "bin"))
    }

    @Test
    fun `mime matching is case insensitive`() {
        assertEquals("jpg", BackgroundStore.extensionFor("IMAGE/JPEG", "png"))
    }
}
