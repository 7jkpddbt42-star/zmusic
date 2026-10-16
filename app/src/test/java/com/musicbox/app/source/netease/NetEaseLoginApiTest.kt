package com.musicbox.app.source.netease

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetEaseLoginApiTest {

    private class RecordingTransport(private val response: TransportResponse) : HttpTransport {
        var lastUrl: String? = null
        var lastBody: String? = null
        var lastHeaders: Map<String, String>? = null
        override fun post(url: String, body: String, headers: Map<String, String>): TransportResponse {
            lastUrl = url; lastBody = body; lastHeaders = headers
            return response
        }
        override fun get(url: String, headers: Map<String, String>): TransportResponse =
            throw UnsupportedOperationException("NetEaseLoginApi 只用 post")
    }

    @Test
    fun `createQrKey posts to eapi unikey and returns key`() = runBlocking {
        val t = RecordingTransport(TransportResponse(200, """{"code":200,"unikey":"abc-123"}"""))
        val api = NetEaseLoginApi(t, deviceId = "DEV1")

        val key = api.createQrKey().getOrThrow()

        assertEquals("abc-123", key)
        assertEquals(
            "https://interfacepc.music.163.com/eapi/login/qrcode/unikey",
            t.lastUrl,
        )
        assertTrue(t.lastBody!!.startsWith("params="))
    }

    @Test
    fun `createQrKey fails as Api when code is not 200`() = runBlocking {
        val t = RecordingTransport(TransportResponse(200, """{"code":-460}"""))
        val result = NetEaseLoginApi(t, "D").createQrKey()
        assertTrue(result.isFailure)
        assertEquals(-460, (result.exceptionOrNull() as com.musicbox.app.source.SourceError.Api).code)
    }

    @Test
    fun `poll maps 801 to Waiting`() = runBlocking {
        val t = RecordingTransport(TransportResponse(200, """{"code":801,"message":"等待扫码"}"""))
        assertEquals(QrPollResult.Waiting, NetEaseLoginApi(t, "D").poll("k").getOrThrow())
    }

    @Test
    fun `poll maps 800 to Expired and 802 to Scanned`() = runBlocking {
        assertEquals(
            QrPollResult.Expired,
            NetEaseLoginApi(RecordingTransport(TransportResponse(200, """{"code":800}""")), "D")
                .poll("k").getOrThrow(),
        )
        assertEquals(
            QrPollResult.Scanned,
            NetEaseLoginApi(RecordingTransport(TransportResponse(200, """{"code":802}""")), "D")
                .poll("k").getOrThrow(),
        )
    }

    @Test
    fun `poll 803 extracts MUSIC_U and csrf from cookie string`() = runBlocking {
        val body = """{"code":803,"cookie":"MUSIC_U=MUSIC_TOKEN; __csrf=CSRF_TOKEN; NMTID=x"}"""
        val t = RecordingTransport(TransportResponse(200, body))

        val result = NetEaseLoginApi(t, "D").poll("k").getOrThrow()

        assertEquals(
            QrPollResult.Success(musicU = "MUSIC_TOKEN", csrf = "CSRF_TOKEN"),
            result,
        )
    }

    @Test
    fun `poll fails as Parse when 803 has no MUSIC_U`() = runBlocking {
        val body = """{"code":803,"cookie":"NMTID=x"}"""
        val t = RecordingTransport(TransportResponse(200, body))
        val result = NetEaseLoginApi(t, "D").poll("k")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is com.musicbox.app.source.SourceError.Parse)
    }
}
