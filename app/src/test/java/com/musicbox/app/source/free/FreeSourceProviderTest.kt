package com.musicbox.app.source.free

import com.musicbox.app.model.LyricData
import com.musicbox.app.model.SearchType
import com.musicbox.app.model.Song
import com.musicbox.app.model.StreamInfo
import com.musicbox.app.source.MusicProvider
import com.musicbox.app.source.netease.HttpTransport
import com.musicbox.app.source.netease.TransportResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeSourceProviderTest {

    private class FakeOfficial(
        private val stream: StreamInfo,
    ) : MusicProvider {
        override val id: String = "official"
        var streamCalls = 0
        override suspend fun search(keyword: String, type: SearchType, page: Int): Result<List<Song>> =
            Result.success(emptyList())
        override suspend fun getStreamUrl(songId: Long): Result<StreamInfo> {
            streamCalls++
            return Result.success(stream)
        }
        override suspend fun getLyric(songId: Long): Result<LyricData> =
            Result.failure(IllegalStateException())
    }

    /** 按 URL 片段匹配应答，记录被请求过的地址。 */
    private class FakeTransport(
        private val routes: List<Pair<String, TransportResponse>>,
    ) : HttpTransport {
        val requested = mutableListOf<String>()
        override fun post(url: String, body: String, headers: Map<String, String>): TransportResponse =
            error("免费源不走 POST")
        override fun get(url: String, headers: Map<String, String>): TransportResponse {
            requested += url
            return routes.firstOrNull { url.contains(it.first) }?.second ?: TransportResponse(404, "")
        }
    }

    private fun audio(type: String = "audio/mpeg") =
        TransportResponse(206, "", contentType = type)

    private fun json(body: String) = TransportResponse(200, body, contentType = "application/json")

    @Test
    fun `disabled mode never touches third party`() = runBlocking {
        val transport = FakeTransport(emptyList())
        val provider = FreeSourceProvider(
            official = FakeOfficial(StreamInfo("http://official/trial.mp3", 128_000, isTrial = true)),
            transport = transport,
            enabled = { false },
        )

        val result = provider.getStreamUrl(1L)

        assertEquals("http://official/trial.mp3", result.getOrNull()?.url)
        assertTrue(result.getOrNull()!!.isTrial)
        assertTrue(transport.requested.isEmpty())
    }

    @Test
    fun `full official stream is used as is`() = runBlocking {
        val transport = FakeTransport(emptyList())
        val provider = FreeSourceProvider(
            official = FakeOfficial(StreamInfo("http://official/full.mp3", 320_000, isTrial = false)),
            transport = transport,
            enabled = { true },
        )

        val result = provider.getStreamUrl(1L)

        assertEquals("http://official/full.mp3", result.getOrNull()?.url)
        assertTrue(transport.requested.isEmpty())
    }

    @Test
    fun `trial stream is replaced by gdstudio url`() = runBlocking {
        val transport = FakeTransport(
            listOf(
                "types=url&source=netease" to json("""{"url":"http://cdn.test/song.mp3"}"""),
                "cdn.test/song.mp3" to audio(),
            ),
        )
        val provider = FreeSourceProvider(
            official = FakeOfficial(StreamInfo("http://official/trial.mp3", 128_000, isTrial = true)),
            transport = transport,
            enabled = { true },
        )

        val result = provider.getStreamUrl(7L)

        assertEquals("http://cdn.test/song.mp3", result.getOrNull()?.url)
        assertFalse(result.getOrNull()!!.isTrial)
    }

    @Test
    fun `empty gdstudio answer falls back to official trial`() = runBlocking {
        val transport = FakeTransport(
            listOf("types=url&source=netease" to json("""{"url":""}""")),
        )
        val provider = FreeSourceProvider(
            official = FakeOfficial(StreamInfo("http://official/trial.mp3", 128_000, isTrial = true)),
            transport = transport,
            enabled = { true },
        )

        val result = provider.getStreamUrl(7L)

        assertEquals("http://official/trial.mp3", result.getOrNull()?.url)
        assertTrue(result.getOrNull()!!.isTrial)
    }

    @Test
    fun `non audio answer is rejected by probe`() = runBlocking {
        val transport = FakeTransport(
            listOf(
                "types=url&source=netease" to json("""{"url":"http://cdn.test/song.mp3"}"""),
                "cdn.test/song.mp3" to TransportResponse(200, "<html>", contentType = "text/html"),
            ),
        )
        val provider = FreeSourceProvider(
            official = FakeOfficial(StreamInfo("http://official/trial.mp3", 128_000, isTrial = true)),
            transport = transport,
            enabled = { true },
        )

        val result = provider.getStreamUrl(7L)

        assertEquals("http://official/trial.mp3", result.getOrNull()?.url)
    }
}
