package com.musicbox.app.source.netease

import com.musicbox.app.model.SearchType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetEaseProviderTest {

    private class FakeTransport(
        private val response: TransportResponse,
    ) : HttpTransport {
        var lastUrl: String? = null
        var lastBody: String? = null
        var lastHeaders: Map<String, String>? = null

        override fun post(url: String, body: String, headers: Map<String, String>): TransportResponse {
            lastUrl = url
            lastBody = body
            lastHeaders = headers
            return response
        }
    }

    private fun fixtureBody(): String =
        javaClass.classLoader!!
            .getResource("search_response.json")!!
            .readText()

    @Test
    fun `search posts params to eapi endpoint and maps songs`() = runBlocking {
        val transport = FakeTransport(TransportResponse(code = 200, body = fixtureBody()))
        val provider = NetEaseProvider(transport = transport, deviceId = "ABCD")

        val result = provider.search("海阔天空", SearchType.SONG, page = 1)

        assertTrue(result.isSuccess)
        val songs = result.getOrThrow()
        assertEquals(2, songs.size)

        val first = songs.first()
        assertEquals(1357375695L, first.id)
        assertEquals("海阔天空", first.name)
        assertEquals(listOf("Beyond"), first.artists)
        assertEquals("华纳廿三周年纪念精选系列 - Beyond", first.album)
        assertEquals(239560L, first.durationMs)
        assertEquals(
            "http://p4.music.126.net/q6cm6Pk70YArijk1_QDoEg==/109951163984013003.jpg",
            first.coverUrl,
        )
        assertEquals("netease", first.providerId)

        // 请求本身长什么样
        assertEquals("https://interfacepc.music.163.com/eapi/cloudsearch/pc", transport.lastUrl)
        assertTrue(transport.lastBody!!.startsWith("params="))
        assertEquals(NetEaseHeader.UA, transport.lastHeaders!!["User-Agent"])
        assertNotNull(transport.lastHeaders!!["Cookie"])
        assertTrue(transport.lastHeaders!!["Cookie"]!!.contains("deviceId=ABCD"))
    }

    @Test
    fun `search returns failure when api code is not 200`() = runBlocking {
        val transport = FakeTransport(
            TransportResponse(code = 200, body = """{"code":-460,"msg":"cheating"}"""),
        )
        val provider = NetEaseProvider(transport = transport, deviceId = "ABCD")

        val result = provider.search("x", SearchType.SONG, page = 1)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is com.musicbox.app.source.SourceError.Api)
        assertEquals(-460, (error as com.musicbox.app.source.SourceError.Api).code)
    }

    @Test
    fun `search returns parse failure when body is not json`() = runBlocking {
        val transport = FakeTransport(TransportResponse(code = 200, body = "<html>oops</html>"))
        val provider = NetEaseProvider(transport = transport, deviceId = "ABCD")

        val result = provider.search("x", SearchType.SONG, page = 1)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is com.musicbox.app.source.SourceError.Parse)
    }
}
