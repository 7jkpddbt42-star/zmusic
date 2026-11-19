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
    /** 本地滚动日志。默认 null：既有单测无需注入，不破坏既有调用。 */
    private val appLog: com.musicbox.app.data.AppLog? = null,
    /** 取流音质档位（standard / higher / exhigh）。每次请求现读，改设置后立即生效。 */
    private val streamLevel: () -> String = { STREAM_LEVEL },
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
            appLog?.log("netease", "search 失败 code=${(e as? SourceError.Api)?.code ?: "-"}", e)
            Result.failure(e)
        } catch (e: IOException) {
            appLog?.log("netease", "search 网络异常", e)
            Result.failure(SourceError.Network(e))
        }
    }

    override suspend fun getStreamUrl(songId: Long): Result<StreamInfo> = withContext(Dispatchers.IO) {
        // 网易云会按接口/网络环境做风控：新版 v1 取流接口近期稳定返回 -460，
        // 单通道会让整首歌直接不可播。这里按「加密新版 → 加密旧版 → 明文新版 → 明文旧版」
        // 依次尝试，任一通道拿到可播地址即返回，全部失败才把最后一个错误抛给上层。
        var lastError: Throwable = SourceError.Parse("取流响应缺少 data", null)
        var unplayable: SourceError.Unplayable? = null
        val channels = listOf<Pair<String, (Long) -> Result<StreamInfo>>>(
            "eapi-v1" to ::streamViaEapiV1,
            "eapi-legacy" to ::streamViaEapiLegacy,
            "plain-v1" to ::streamViaPlainV1,
            "plain-legacy" to ::streamViaPlainLegacy,
        )
        for ((name, channel) in channels) {
            val result = try {
                channel(songId)
            } catch (e: SourceError) {
                Result.failure(e)
            } catch (e: Exception) {
                // 传输层异常（含测试替身未实现的通道）一律当网络错误，继续试下一条
                Result.failure(SourceError.Network(e))
            }
            val info = result.getOrNull()
            if (info != null) {
                if (name != "eapi-v1") {
                    appLog?.log("netease", "getStreamUrl 通道降级命中 $name songId=$songId")
                }
                return@withContext Result.success(info)
            }
            val error = result.exceptionOrNull() ?: lastError
            if (error is SourceError.Unplayable && unplayable == null) unplayable = error
            lastError = error
            appLog?.log("netease", "getStreamUrl 通道 $name 失败 songId=$songId", error)
        }
        // 有通道明确说「这首歌没有可播地址」时优先返回它，比笼统的网络错误更准确
        Result.failure(unplayable ?: lastError)
    }

    /** 通道 1：新版加密取流（接口带 level，登录态下音质最好）。 */
    private fun streamViaEapiV1(songId: Long): Result<StreamInfo> {
        val s = session()
        val header = NetEaseHeader.build(deviceId, s.csrf)
        val payload = linkedMapOf<String, Any>(
            // ids 必须是「字符串形式的 JSON 数组」，实测写成数字数组拿不到结果
            "ids" to "[$songId]",
            "level" to streamLevel(),
            "encodeType" to "mp3",
            "e_r" to false,
            "header" to header,
        )
        return parseStream(eapiPost(SONG_URL_V1_URI, payload, header, s.musicU))
    }

    /** 通道 2：旧版加密取流（只认 br，不受新版 v1 的 -460 风控影响）。 */
    private fun streamViaEapiLegacy(songId: Long): Result<StreamInfo> {
        val s = session()
        val header = NetEaseHeader.build(deviceId, s.csrf)
        val payload = linkedMapOf<String, Any>(
            "ids" to "[$songId]",
            "br" to legacyBitrate(streamLevel()),
            "e_r" to false,
            "header" to header,
        )
        return parseStream(eapiPost(SONG_URL_LEGACY_URI, payload, header, s.musicU))
    }

    /** 通道 3：明文新版接口（不走 eapi 签名，实测可绕过 -460 风控）。 */
    private fun streamViaPlainV1(songId: Long): Result<StreamInfo> {
        val s = session()
        val header = NetEaseHeader.build(deviceId, s.csrf)
        val url = PLAIN_BASE + SONG_URL_V1_URI +
            "?ids=" + URLEncoder.encode("[$songId]", "UTF-8") +
            "&level=" + URLEncoder.encode(streamLevel(), "UTF-8") +
            "&encodeType=mp3"
        return parseStream(transport.get(url, plainHeaders(header, s.musicU)).body)
    }

    /** 通道 4：明文旧版接口，最后的兜底。 */
    private fun streamViaPlainLegacy(songId: Long): Result<StreamInfo> {
        val s = session()
        val header = NetEaseHeader.build(deviceId, s.csrf)
        val url = PLAIN_BASE + SONG_URL_LEGACY_URI +
            "?ids=" + URLEncoder.encode("[$songId]", "UTF-8") +
            "&br=" + legacyBitrate(streamLevel())
        return parseStream(transport.get(url, plainHeaders(header, s.musicU)).body)
    }

    /** eapi 签名 + POST，返回应答正文。 */
    private fun eapiPost(
        uri: String,
        payload: Map<String, Any>,
        header: Map<String, String>,
        musicU: String?,
    ): String {
        val params = NetEaseCrypto.eapiParams(uri, gson.toJson(payload))
        val body = "params=" + URLEncoder.encode(params, "UTF-8")
        val headers = mapOf(
            "User-Agent" to NetEaseHeader.UA,
            "Cookie" to NetEaseHeader.cookieHeader(header, musicU),
            "Referer" to "https://music.163.com",
        )
        return transport.post(EAPI_BASE + uri.replace("/api", "/eapi"), body, headers).body
    }

    /** 明文接口同样携带同一组 header Cookie，避免被当成无身份请求。 */
    private fun plainHeaders(header: Map<String, String>, musicU: String?): Map<String, String> = mapOf(
        "User-Agent" to NetEaseHeader.UA,
        "Cookie" to NetEaseHeader.cookieHeader(header, musicU),
        "Referer" to "https://music.163.com",
    )

    private fun parseStream(body: String): Result<StreamInfo> {
        val dto = try {
            gson.fromJson(body, SongUrlRespDto::class.java)
        } catch (e: JsonSyntaxException) {
            throw SourceError.Parse("响应不是合法 JSON", e)
        } ?: throw SourceError.Parse("响应为空", null)

        if (dto.code != 200) throw SourceError.Api(dto.code, null)

        val info = dto.data?.firstOrNull()?.toStreamInfo()
            ?: throw SourceError.Unplayable("当前歌曲暂无可用播放地址")
        return Result.success(info)
    }

    /** 设置里的档位名 → 旧版接口的 br 参数。 */
    private fun legacyBitrate(level: String): Int = when (level) {
        "standard" -> 128_000
        "higher" -> 192_000
        else -> 320_000
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
            appLog?.log("netease", "getLyric 失败 code=${(e as? SourceError.Api)?.code ?: "-"}", e)
            Result.failure(e)
        } catch (e: IOException) {
            appLog?.log("netease", "getLyric 网络异常", e)
            Result.failure(SourceError.Network(e))
        }
    }

    private companion object {
        const val EAPI_BASE = "https://interfacepc.music.163.com"

        /** 明文接口所在域名（不走 eapi 签名）。 */
        const val PLAIN_BASE = "https://music.163.com"

        const val SEARCH_URI = "/api/cloudsearch/pc"
        const val SEARCH_PAGE_SIZE = 30

        const val SONG_URL_V1_URI = "/api/song/enhance/player/url/v1"
        const val SONG_URL_LEGACY_URI = "/api/song/enhance/player/url"

        const val LYRIC_URI = "/api/song/lyric/v1"

        /** 实测该档位对免费歌给到 320kbps，VIP 歌自动降级为 standard + 试听片段。 */
        const val STREAM_LEVEL = "exhigh"
    }
}
