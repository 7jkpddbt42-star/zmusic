package com.musicbox.app.source.failover

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.musicbox.app.model.LyricData
import com.musicbox.app.model.SearchType
import com.musicbox.app.model.Song
import com.musicbox.app.model.StreamInfo
import com.musicbox.app.source.MusicProvider
import com.musicbox.app.source.SourceError
import com.musicbox.app.source.netease.HttpTransport
import com.musicbox.app.source.netease.LyricRespDto
import com.musicbox.app.source.netease.SearchRespDto
import com.musicbox.app.source.netease.SongUrlRespDto
import com.musicbox.app.source.netease.TransportResponse
import com.musicbox.app.source.netease.toSong
import com.musicbox.app.source.netease.toStreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URLEncoder

/**
 * 备用音源（设计文档 §7）：一个**地址可在设置中配置**的第三方公共接口实例。
 * 默认地址留空 = 未配置，不参与正常流程；只在端上签名路径临时失效时作为应急兜底。
 *
 * 协议与 NetEaseCloudMusicApi 一致，响应结构与 NetEaseDto 完全兼容 → 直接复用 DTO。
 * 首选路径永远是端上自包含（NetEaseProvider），本类不参与正常流程。
 */
class FallbackProvider(
    private val transport: HttpTransport,
    private val baseUrl: () -> String,
    private val gson: Gson = Gson(),
) : MusicProvider {

    override val id: String = "fallback"

    private fun base(): String = baseUrl().trim().trimEnd('/')

    override suspend fun search(
        keyword: String,
        type: SearchType,
        page: Int,
    ): Result<List<Song>> = withContext(Dispatchers.IO) {
        val base = base()
        if (base.isEmpty()) return@withContext Result.failure(SourceError.NotConfigured())
        try {
            val limit = PAGE_SIZE
            val offset = (page - 1).coerceAtLeast(0) * limit
            val url = "$base/search?keywords=${enc(keyword)}&type=${type.apiValue}" +
                "&limit=$limit&offset=$offset"
            val body = transport.get(url, HEADERS).body
            val dto = parse(body, SearchRespDto::class.java)
            if (dto.code != 200) throw SourceError.Api(dto.code, null)
            Result.success(dto.result?.songs.orEmpty().map { it.toSong(id) })
        } catch (e: SourceError) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(SourceError.Network(e))
        }
    }

    override suspend fun getStreamUrl(songId: Long): Result<StreamInfo> = withContext(Dispatchers.IO) {
        val base = base()
        if (base.isEmpty()) return@withContext Result.failure(SourceError.NotConfigured())
        try {
            val url = "$base/song/url/v1?id=$songId&level=exhigh"
            val body = transport.get(url, HEADERS).body
            val dto = parse(body, SongUrlRespDto::class.java)
            if (dto.code != 200) throw SourceError.Api(dto.code, null)
            val info = dto.data?.firstOrNull()?.toStreamInfo()
                ?: throw SourceError.Unplayable("当前歌曲暂无可用播放地址")
            Result.success(info)
        } catch (e: SourceError) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(SourceError.Network(e))
        }
    }

    override suspend fun getLyric(songId: Long): Result<LyricData> = withContext(Dispatchers.IO) {
        val base = base()
        if (base.isEmpty()) return@withContext Result.failure(SourceError.NotConfigured())
        try {
            val body = transport.get("$base/lyric?id=$songId", HEADERS).body
            val dto = parse(body, LyricRespDto::class.java)
            if (dto.code != 200) throw SourceError.Api(dto.code, null)
            Result.success(
                LyricData(
                    yrc = dto.yrc?.lyric?.takeIf { it.isNotBlank() },
                    lrc = dto.lrc?.lyric?.takeIf { it.isNotBlank() },
                    translation = dto.tlyric?.lyric?.takeIf { it.isNotBlank() },
                    romaji = dto.romalrc?.lyric?.takeIf { it.isNotBlank() },
                ),
            )
        } catch (e: SourceError) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(SourceError.Network(e))
        }
    }

    private fun <T> parse(body: String, clazz: Class<T>): T =
        try {
            gson.fromJson(body, clazz) ?: throw SourceError.Parse("响应为空", null)
        } catch (e: JsonSyntaxException) {
            throw SourceError.Parse("响应不是合法 JSON", e)
        }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val PAGE_SIZE = 30
        val HEADERS = mapOf("User-Agent" to "musicbox/1.0", "Accept" to "application/json")
    }
}
