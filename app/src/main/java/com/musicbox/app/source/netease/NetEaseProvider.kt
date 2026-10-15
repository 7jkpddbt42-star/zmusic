package com.musicbox.app.source.netease

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.musicbox.app.model.LyricData
import com.musicbox.app.model.SearchType
import com.musicbox.app.model.Song
import com.musicbox.app.model.StreamInfo
import com.musicbox.app.source.MusicProvider
import com.musicbox.app.source.SourceError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URLEncoder

/**
 * 网易云音源（端上自包含签名）。
 *
 * 只负责：组 JSON → 签名 → 发请求 → 解析。
 * 不负责：备用源切换、重试策略、缓存（MI-4）。
 */
class NetEaseProvider(
    private val transport: HttpTransport,
    private val deviceId: String,
) : MusicProvider {

    override val id: String = "netease"

    private val gson = Gson()

    override suspend fun search(
        keyword: String,
        type: SearchType,
        page: Int,
    ): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val limit = SEARCH_PAGE_SIZE
            // header 只构造一次：加密 JSON 里的 header 与 Cookie 头必须携带同一组
            // requestId/buildver，否则是最容易触发风控的隐性偏差。
            val header = NetEaseHeader.build(deviceId)
            val payload = linkedMapOf<String, Any>(
                "s" to keyword,
                "type" to type.apiValue,
                "limit" to limit,
                "offset" to (page - 1).coerceAtLeast(0) * limit,
                "total" to true,
                "e_r" to false,
                "header" to header,
            )
            val uri = SEARCH_URI
            val jsonText = gson.toJson(payload)
            val params = NetEaseCrypto.eapiParams(uri, jsonText)
            val body = "params=" + URLEncoder.encode(params, "UTF-8")
            val headers = mapOf(
                "User-Agent" to NetEaseHeader.UA,
                "Cookie" to NetEaseHeader.cookieHeader(header),
                "Referer" to "https://music.163.com",
            )

            val response = transport.post(EAPI_BASE + uri.replace("/api", "/eapi"), body, headers)

            val dto = try {
                gson.fromJson(response.body, SearchRespDto::class.java)
            } catch (e: JsonSyntaxException) {
                throw SourceError.Parse("响应不是合法 JSON", e)
            } ?: throw SourceError.Parse("响应为空", null)

            if (dto.code != 200) {
                throw SourceError.Api(dto.code, null)
            }

            val songs = dto.result?.songs.orEmpty().map { it.toSong(id) }
            Result.success(songs)
        } catch (e: SourceError) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(SourceError.Network(e))
        }
    }

    override suspend fun getStreamUrl(songId: Long): Result<StreamInfo> =
        Result.failure(SourceError.NotImplementedYet("MI-2"))

    override suspend fun getLyric(songId: Long): Result<LyricData> =
        Result.failure(SourceError.NotImplementedYet("MI-3"))

    private companion object {
        const val EAPI_BASE = "https://interfacepc.music.163.com"
        const val SEARCH_URI = "/api/cloudsearch/pc"
        const val SEARCH_PAGE_SIZE = 30
    }
}
