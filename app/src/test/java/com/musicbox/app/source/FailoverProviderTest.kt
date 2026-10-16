package com.musicbox.app.source

import com.musicbox.app.model.LyricData
import com.musicbox.app.model.SearchType
import com.musicbox.app.model.Song
import com.musicbox.app.model.StreamInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FailoverProviderTest {

    /** 固定返回值的假音源。 */
    private class FakeProvider(
        override val id: String,
        private val onStream: () -> Result<StreamInfo> = { Result.success(StreamInfo("", 0, false)) },
        private val onLyric: () -> Result<LyricData> = { Result.failure(SourceError.NotConfigured()) },
        private val onSearch: () -> Result<List<Song>> = { Result.success(emptyList()) },
    ) : MusicProvider {
        var searchCalls = 0
        var streamCalls = 0
        override suspend fun search(keyword: String, type: SearchType, page: Int): Result<List<Song>> {
            searchCalls++
            return onSearch()
        }
        override suspend fun getStreamUrl(songId: Long): Result<StreamInfo> {
            streamCalls++
            return onStream()
        }
        override suspend fun getLyric(songId: Long): Result<LyricData> = onLyric()
    }

    private val song = Song(1, "s", listOf("a"), null, 0, null, "x")

    @Test
    fun `primary success skips fallback`() = runBlocking {
        val primary = FakeProvider("p") { Result.success(listOf(song)) }
        val fallback = FakeProvider("f") { Result.success(emptyList()) }
        val provider = FailoverProvider(primary, fallback)

        val result = provider.search("k", SearchType.SONG, 1)

        assertTrue(result.isSuccess)
        assertEquals(1, primary.searchCalls)
        assertEquals(0, fallback.searchCalls)
    }

    @Test
    fun `primary network failure triggers fallback once`() = runBlocking {
        val primary = FakeProvider("p") { Result.failure(SourceError.Network()) }
        val fallback = FakeProvider("f") { Result.success(listOf(song)) }
        val provider = FailoverProvider(primary, fallback)

        val result = provider.search("k", SearchType.SONG, 1)

        assertTrue(result.isSuccess)
        assertEquals(1, fallback.searchCalls)
    }

    @Test
    fun `unplayable is not retried on fallback`() = runBlocking {
        val primary = FakeProvider("p", onStream = { Result.failure(SourceError.Unplayable()) })
        val fallback = FakeProvider("f", onStream = { Result.success(StreamInfo("u", 0, false)) })
        val provider = FailoverProvider(primary, fallback)

        val result = provider.getStreamUrl(1L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SourceError.Unplayable)
        assertEquals(0, fallback.streamCalls)
    }

    @Test
    fun `when fallback also fails the primary error is reported`() = runBlocking {
        val primary = FakeProvider("p") { Result.failure(SourceError.Api(-460, null)) }
        val fallback = FakeProvider("f") { Result.failure(SourceError.Network()) }
        val provider = FailoverProvider(primary, fallback)

        val result = provider.search("k", SearchType.SONG, 1)

        assertTrue(result.exceptionOrNull() is SourceError.Api)
    }
}
