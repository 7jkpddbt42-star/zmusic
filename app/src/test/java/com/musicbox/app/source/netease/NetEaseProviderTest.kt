package com.musicbox.app.source.netease

import com.musicbox.app.model.SearchType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

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

    // ── MI-2 取流 ────────────────────────────────────────────────

    private fun songUrlFixture(): String =
        javaClass.classLoader!!.getResource("song_url_response.json")!!.readText()

    /**
     * 把 transport 收到的 params 解回明文，用于断言我们真正发出去的加密 JSON。
     * 只在测试里做：生产代码不需要解密自己的能力。
     */
    private fun decryptParams(params: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(NetEaseCrypto.EAPI_KEY.toByteArray(Charsets.UTF_8), "AES"),
        )
        return String(cipher.doFinal(params.hexToBytes()), Charsets.UTF_8)
    }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0)
        return ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    /** 从 payload（uri + 分隔符 + text + 分隔符 + digest）里抠出 jsonText。 */
    private fun jsonTextOf(params: String): String =
        decryptParams(params).split(NetEaseCrypto.EAPI_SEP)[1]

    @Test
    fun `getStreamUrl posts ids and level then maps full stream`() = runBlocking {
        val transport = FakeTransport(TransportResponse(code = 200, body = songUrlFixture()))
        val provider = NetEaseProvider(transport = transport, deviceId = "ABCD")

        val result = provider.getStreamUrl(347368L)

        assertTrue(result.isSuccess)
        val info = result.getOrThrow()
        assertEquals(
            "http://m802.music.126.net/20261016173259/sample/jd-musicrep-ts/sample.mp3?vuutv=abc",
            info.url,
        )
        assertEquals(320000, info.bitrateBps)
        assertEquals(false, info.isTrial)

        // 我们真正发出去的东西
        assertEquals(
            "https://interfacepc.music.163.com/eapi/song/enhance/player/url/v1",
            transport.lastUrl,
        )
        assertTrue(transport.lastBody!!.startsWith("params="))
        val sentJson = jsonTextOf(
            URLDecoder.decode(transport.lastBody!!.removePrefix("params="), "UTF-8"),
        )
        assertTrue("实际请求体：$sentJson", sentJson.contains("""{"ids":"[347368]""""))
        assertTrue(sentJson.contains(""""level":"exhigh""""))
        assertTrue(sentJson.contains(""""encodeType":"mp3""""))
        assertTrue(sentJson.contains(""""e_r":false"""))
        assertTrue(sentJson.contains(""""header":{"osver""""))
        assertEquals(NetEaseHeader.UA, transport.lastHeaders!!["User-Agent"])
        assertTrue(transport.lastHeaders!!["Cookie"]!!.contains("deviceId=ABCD"))
    }

    @Test
    fun `getStreamUrl marks trial clip from freeTrialInfo`() = runBlocking {
        val trial = """{"code":200,"data":[{"id":1357375695,"url":"http://m702.music.126.net/x.mp3","br":128012,"size":481115,"expi":1200,"fee":1,"level":"standard","freeTrialInfo":{"fragmentType":-1,"start":0,"end":30,"algData":{"fragSource":"default"}}}]}"""
        val transport = FakeTransport(TransportResponse(code = 200, body = trial))
        val provider = NetEaseProvider(transport = transport, deviceId = "ABCD")

        val info = provider.getStreamUrl(1357375695L).getOrThrow()

        assertEquals(true, info.isTrial)
        assertEquals(128012, info.bitrateBps)
    }

    @Test
    fun `getStreamUrl fails as Unplayable when url is empty`() = runBlocking {
        val noUrl = """{"code":200,"data":[{"id":1,"url":null,"br":0,"expi":1200,"fee":0,"level":"none","freeTrialInfo":null}]}"""
        val transport = FakeTransport(TransportResponse(code = 200, body = noUrl))
        val provider = NetEaseProvider(transport = transport, deviceId = "ABCD")

        val result = provider.getStreamUrl(1L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is com.musicbox.app.source.SourceError.Unplayable)
    }

    @Test
    fun `getStreamUrl fails as Api when top level code is not 200`() = runBlocking {
        val transport = FakeTransport(
            TransportResponse(code = 200, body = """{"code":-460,"msg":"cheating"}"""),
        )
        val provider = NetEaseProvider(transport = transport, deviceId = "ABCD")

        val result = provider.getStreamUrl(1L)

        assertTrue(result.isFailure)
        assertEquals(-460, (result.exceptionOrNull() as com.musicbox.app.source.SourceError.Api).code)
    }
}
