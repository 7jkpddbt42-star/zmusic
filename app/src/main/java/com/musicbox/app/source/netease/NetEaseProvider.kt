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
    /** 登录会话供应器。每次请求现读，登录/登出后无需重建 provider。 */
    private val session: () -> com.musicbox.app.data.NetEaseSession = { com.musicbox.app.data.NetEaseSession.Guest },
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
            val s = session()
            val header = NetEaseHeader.build(deviceId, s.csrf)
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
                "Cookie" to NetEaseHeader.cookieHeader(header, s.musicU),
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

    override suspend fun getStreamUrl(songId: Long): Result<StreamInfo> = withContext(Dispatchers.IO) {
        try {
            val s = session()
            val header = NetEaseHeader.build(deviceId, s.csrf)
            val payload = linkedMapOf<String, Any>(
                // ids 必须是「字符串形式的 JSON 数组」，实测写成数字数组拿不到结果
                "ids" to "[$songId]",
                // 实测该档位对免费歌给到 320kbps，VIP 歌会自动降级为 standard + 试听片段
                "level" to STREAM_LEVEL,
                // 实测取 mp3 即可，且 ExoPlayer 吃 mp3 最省事
                "encodeType" to "mp3",
                "e_r" to false,
                "header" to header,
            )
            val uri = SONG_URL_URI
            val params = NetEaseCrypto.eapiParams(uri, gson.toJson(payload))
            val body = "params=" + URLEncoder.encode(params, "UTF-8")
            val headers = mapOf(
                "User-Agent" to NetEaseHeader.UA,
                "Cookie" to NetEaseHeader.cookieHeader(header, s.musicU),
                "Referer" to "https://music.163.com",
            )

            val response = transport.post(EAPI_BASE + uri.replace("/api", "/eapi"), body, headers)

            val dto = try {
                gson.fromJson(response.body, SongUrlRespDto::class.java)
            } catch (e: JsonSyntaxException) {
                throw SourceError.Parse("响应不是合法 JSON", e)
            } ?: throw SourceError.Parse("响应为空", null)

            if (dto.code != 200) {
                throw SourceError.Api(dto.code, null)
            }

            val first = dto.data?.firstOrNull()
                ?: throw SourceError.Parse("取流响应缺少 data", null)
            val info = first.toStreamInfo()
                ?: throw SourceError.Unplayable("当前歌曲暂无可用播放地址")

            Result.success(info)
        } catch (e: SourceError) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(SourceError.Network(e))
        }
    }

    override suspend fun getLyric(songId: Long): Result<LyricData> = withContext(Dispatchers.IO) {
        try {
            val s = session()
            val header = NetEaseHeader.build(deviceId, s.csrf)
            val payload = linkedMapOf<String, Any>(
                // 注意：这里是数字 id，不是取流那样的字符串 JSON 数组（实测，见计划 §1.1）
                "id" to songId,
                "cp" to false,
                "tv" to 0,
                "lv" to 0,
                "rv" to 0,
                "kv" to 0,
                // 下面三个控制是否返回 yrc/tlyric/romalrc，实测 0 即可拿到全部字段
                "yv" to 0,
                "ytv" to 0,
                "yrv" to 0,
                "e_r" to false,
                "header" to header,
            )
            val uri = LYRIC_URI
            val params = NetEaseCrypto.eapiParams(uri, gson.toJson(payload))
            val body = "params=" + URLEncoder.encode(params, "UTF-8")
            val headers = mapOf(
                "User-Agent" to NetEaseHeader.UA,
                "Cookie" to NetEaseHeader.cookieHeader(header, s.musicU),
                "Referer" to "https://music.163.com",
            )

            val response = transport.post(EAPI_BASE + uri.replace("/api", "/eapi"), body, headers)

            val dto = try {
                gson.fromJson(response.body, LyricRespDto::class.java)
            } catch (e: JsonSyntaxException) {
                throw SourceError.Parse("响应不是合法 JSON", e)
            } ?: throw SourceError.Parse("响应为空", null)

            if (dto.code != 200) {
                throw SourceError.Api(dto.code, null)
            }

            // 原始文本原样交给 lyric 层解析（音源不解析歌词，见设计文档 §4.2）
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

    private companion object {
        const val EAPI_BASE = "https://interfacepc.music.163.com"
        const val SEARCH_URI = "/api/cloudsearch/pc"
        const val SEARCH_PAGE_SIZE = 30

        const val SONG_URL_URI = "/api/song/enhance/player/url/v1"

        const val LYRIC_URI = "/api/song/lyric/v1"

        /** 实测该档位对免费歌给到 320kbps，VIP 歌自动降级为 standard + 试听片段。 */
        const val STREAM_LEVEL = "exhigh"
    }
}
