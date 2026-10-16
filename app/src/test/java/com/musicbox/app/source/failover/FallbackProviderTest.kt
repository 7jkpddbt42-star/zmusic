package com.musicbox.app.source.failover

import com.musicbox.app.model.SearchType
import com.musicbox.app.source.SourceError
import com.musicbox.app.source.netease.HttpTransport
import com.musicbox.app.source.netease.TransportResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackProviderTest {

    private class FakeTransport(private val response: TransportResponse) : HttpTransport {
        var lastUrl: String? = null
        override fun post(url: String, body: String, headers: Map<String, String>) = response
        override fun get(url: String, headers: Map<String, String>): TransportResponse {
            lastUrl = url
            return response
        }
    }

    private val searchJson = """{"code":200,"result":{"songs":[{"id":7,"name":"歌","ar":[{"name":"A"}],"al":{"name":"专辑","picUrl":null},"dt":1000}]}}"""
    private val urlJson = """{"code":200,"data":[{"id":7,"url":"http://x/y.mp3","br":320000,"fee":0,"level":"exhigh","freeTrialInfo":null}]}"""
    private val lyricJson = """{"code":200,"lrc":{"lyric":"[00:00]a"},"yrc":{"lyric":null},"tlyric":{"lyric":null},"romalrc":{"lyric":null}}"""

    @Test
    fun `search builds a keyword url and maps songs`() = runBlocking {
        val t = FakeTransport(TransportResponse(200, searchJson))
        val provider = FallbackProvider(t, baseUrl = { "https://example.com" })

        val songs = provider.search("江南", SearchType.SONG, page = 1).getOrThrow()

        assertEquals(1, songs.size)
        assertEquals(7L, songs.first().id)
        assertEquals("fallback", songs.first().providerId)
        assertTrue(t.lastUrl!!.startsWith("https://example.com/search?keywords="))
        assertTrue(t.lastUrl!!.contains("type=1"))
    }

    @Test
    fun `search fails as NotConfigured when base url is empty`() = runBlocking {
        val provider = FallbackProvider(FakeTransport(TransportResponse(200, "{}")), baseUrl = { "" })
        val result = provider.search("x", SearchType.SONG, 1)
        assertTrue(result.exceptionOrNull() is SourceError.NotConfigured)
    }

    @Test
    fun `getStreamUrl maps url and trial flag`() = runBlocking {
        val provider = FallbackProvider(FakeTransport(TransportResponse(200, urlJson)), baseUrl = { "https://e.com" })
        val info = provider.getStreamUrl(7L).getOrThrow()
        assertEquals("http://x/y.mp3", info.url)
        assertEquals(false, info.isTrial)
    }

    @Test
    fun `getStreamUrl fails as Unplayable when url blank`() = runBlocking {
        val json = """{"code":200,"data":[{"id":7,"url":null,"br":0,"fee":0}]}"""
        val provider = FallbackProvider(FakeTransport(TransportResponse(200, json)), baseUrl = { "https://e.com" })
        assertTrue(provider.getStreamUrl(7L).exceptionOrNull() is SourceError.Unplayable)
    }

    @Test
    fun `getLyric maps raw sections`() = runBlocking {
        val provider = FallbackProvider(FakeTransport(TransportResponse(200, lyricJson)), baseUrl = { "https://e.com" })
        val data = provider.getLyric(7L).getOrThrow()
        assertEquals("[00:00]a", data.lrc)
        assertEquals(null, data.yrc)
    }
}
