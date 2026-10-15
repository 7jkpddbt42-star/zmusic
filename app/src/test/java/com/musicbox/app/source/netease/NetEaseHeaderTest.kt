package com.musicbox.app.source.netease

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetEaseHeaderTest {

    @Test
    fun `cookie header joins entries with percent encoding`() {
        val header = linkedMapOf(
            "deviceId" to "ABCD",
            "os" to "pc",
        )
        assertEquals("deviceId=ABCD; os=pc", NetEaseHeader.cookieHeader(header))
    }

    @Test
    fun `generated device id is 32 uppercase hex chars`() {
        val id = NetEaseHeader.newDeviceId()
        assertEquals(32, id.length)
        assertTrue(id.all { it in '0'..'9' || it in 'A'..'F' })
    }

    @Test
    fun `build fills required eapi fields`() {
        val header = NetEaseHeader.build(deviceId = "ABCD")
        assertEquals("pc", header["os"])
        assertEquals("140", header["versioncode"])
        assertEquals("1920x1080", header["resolution"])
        assertEquals("netease", header["channel"])
        assertEquals("ABCD", header["deviceId"])
        assertTrue(header.containsKey("requestId"))
        assertTrue(header.containsKey("buildver"))
    }
}
