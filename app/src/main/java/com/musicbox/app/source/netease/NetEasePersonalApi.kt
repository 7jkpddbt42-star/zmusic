package com.musicbox.app.source.netease

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.musicbox.app.data.AppLog
import com.musicbox.app.data.NetEaseSession
import com.musicbox.app.model.PlaylistSummary
import com.musicbox.app.model.Song
import com.musicbox.app.source.SourceError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URLEncoder

/**
 * 网易云个人内容接口：每日推荐、私人 FM、我的歌单、歌单详情。
 *
 * 与 [NetEaseProvider] 共用同一套 eapi 签名与 Cookie 约定；游玩态（未登录）下
 * 每日推荐与私人 FM 实测仍返回内容，因此首页对游客也展示这两块，只有
 * "我的歌单 / 我喜欢的音乐"需要登录后的 uid。
 */
interface PersonalApi {
    suspend fun dailyRecommendations(): Result<List<Song>>

    /** 私人 FM 每次请求只返回一首，这里连续取 [count] 首组成一次播放队列。 */
    suspend fun personalFm(count: Int = 3): Result<List<Song>>

    suspend fun userPlaylists(userId: Long): Result<List<PlaylistSummary>>

    suspend fun playlistSongs(playlistId: Long): Result<List<Song>>

    /** 喜欢 / 取消喜欢，写入账号的「我喜欢的音乐」。 */
    suspend fun likeSong(trackId: Long, like: Boolean, userId: Long): Result<Unit>
}

class NetEasePersonalApi(
    private val transport: HttpTransport,
    private val deviceId: String,
    private val session: () -> NetEaseSession = { NetEaseSession.Guest },
    private val appLog: AppLog? = null,
    private val gson: Gson = Gson(),
) : PersonalApi {

    override suspend fun dailyRecommendations(): Result<List<Song>> = call(
        label = "dailyRecommend",
        uri = DAILY_URI,
    ) { body ->
        val dto = gson.fromJson(body, DailyRecommendRespDto::class.java)
            ?: throw SourceError.Parse("响应为空", null)
        if (dto.code != 200) throw SourceError.Api(dto.code, null)
        dto.data?.dailySongs.orEmpty().map { it.toSong(PROVIDER_ID) }
    }

    override suspend fun personalFm(count: Int): Result<List<Song>> = withContext(Dispatchers.IO) {
        val songs = LinkedHashMap<Long, Song>()
        repeat(count.coerceIn(1, FM_MAX_QUEUE)) {
            val result = call(label = "personalFm", uri = RADIO_URI) { body ->
                val dto = gson.fromJson(body, RadioRespDto::class.java)
                    ?: throw SourceError.Parse("响应为空", null)
                if (dto.code != 200) throw SourceError.Api(dto.code, null)
                dto.data.orEmpty().map { it.toSong(PROVIDER_ID) }
            }
            val batch = result.getOrElse { return@withContext Result.failure(it) }
            batch.filter { it.id > 0 }.forEach { songs[it.id] = it }
        }
        if (songs.isEmpty()) {
            Result.failure(SourceError.Parse("私人FM没有返回歌曲", null))
        } else {
            Result.success(songs.values.toList())
        }
    }

    override suspend fun userPlaylists(userId: Long): Result<List<PlaylistSummary>> {
        if (userId <= 0) return Result.failure(SourceError.Parse("缺少用户 id", null))
        return call(
            label = "userPlaylists",
            uri = USER_PLAYLIST_URI,
            payload = mapOf(
                "uid" to userId,
                "limit" to PLAYLIST_LIMIT,
                "offset" to 0,
                "includeVideo" to true,
            ),
        ) { body ->
            val dto = gson.fromJson(body, UserPlaylistRespDto::class.java)
                ?: throw SourceError.Parse("响应为空", null)
            if (dto.code != 200) throw SourceError.Api(dto.code, null)
            dto.playlist.orEmpty().map { it.toSummary() }
        }
    }

    override suspend fun playlistSongs(playlistId: Long): Result<List<Song>> {
        if (playlistId <= 0) return Result.failure(SourceError.Parse("缺少歌单 id", null))
        return call(
            label = "playlistSongs",
            uri = PLAYLIST_DETAIL_URI,
            payload = mapOf("id" to playlistId, "n" to PLAYLIST_LIMIT, "s" to 8),
        ) { body ->
            val dto = gson.fromJson(body, PlaylistDetailRespDto::class.java)
                ?: throw SourceError.Parse("响应为空", null)
            if (dto.code != 200) throw SourceError.Api(dto.code, null)
            dto.playlist?.tracks.orEmpty().map { it.toSong(PROVIDER_ID) }
        }
    }

    /**
     * 喜欢 / 取消喜欢。eapi 与网页端 `/api/song/like` 同一套参数：
     * trackId、userid（小写）、like，csrf 放在 body 里，同时在 header 里带 csrf。
     */
    override suspend fun likeSong(trackId: Long, like: Boolean, userId: Long): Result<Unit> {
        if (trackId <= 0) return Result.failure(SourceError.Parse("缺少歌曲 id", null))
        if (userId <= 0) return Result.failure(SourceError.Parse("缺少用户 id", null))
        return call(
            label = "songLike",
            uri = LIKE_URI,
            payload = mapOf(
                "trackId" to trackId,
                "userid" to userId,
                "like" to like,
                "csrf_token" to session().csrf,
            ),
        ) { body ->
            val dto = gson.fromJson(body, LikeRespDto::class.java)
            if (dto?.code != 200) throw SourceError.Api(dto?.code ?: -1, null)
        }
    }

    /**
     * 统一封装：读会话 → 构造 header → 签名 → post → 解析。
     * [payload] 只需放业务字段；e_r 与 header 由这里补齐。
     */
    private suspend fun <T> call(
        label: String,
        uri: String,
        payload: Map<String, Any> = emptyMap(),
        bodyMapper: (String) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            val s = session()
            val header = NetEaseHeader.build(deviceId, s.csrf)
            val bodyMap = linkedMapOf<String, Any>()
            bodyMap.putAll(payload)
            bodyMap["e_r"] = false
            bodyMap["header"] = header

            val params = NetEaseCrypto.eapiParams(uri, gson.toJson(bodyMap))
            val requestBody = "params=" + URLEncoder.encode(params, "UTF-8")
            val headers = mapOf(
                "User-Agent" to NetEaseHeader.UA,
                "Cookie" to NetEaseHeader.cookieHeader(header, s.musicU),
                "Referer" to "https://music.163.com",
            )
            val response = transport.post(EAPI_BASE + uri.replaceFirst("/api", "/eapi"), requestBody, headers)
            Result.success(bodyMapper(response.body))
        } catch (e: SourceError) {
            appLog?.log("netease", "$label 失败 code=${(e as? SourceError.Api)?.code ?: "-"}", e)
            Result.failure(e)
        } catch (e: JsonSyntaxException) {
            appLog?.log("netease", "$label 响应解析失败", e)
            Result.failure(SourceError.Parse("响应不是合法 JSON", e))
        } catch (e: IOException) {
            appLog?.log("netease", "$label 网络异常", e)
            Result.failure(SourceError.Network(e))
        }
    }

    private companion object {
        const val EAPI_BASE = "https://interfacepc.music.163.com"
        const val PROVIDER_ID = "netease"
        const val DAILY_URI = "/api/v3/discovery/recommend/songs"
        const val RADIO_URI = "/api/radio/get"
        const val USER_PLAYLIST_URI = "/api/user/playlist"
        const val PLAYLIST_DETAIL_URI = "/api/v6/playlist/detail"
        const val LIKE_URI = "/api/song/like"

        /** 单次 FM 播放队列上限（每次请求只回一首，避免过多次往返）。 */
        const val FM_MAX_QUEUE = 5

        /** 歌单接口的分页上限，足够覆盖普通用户歌单。 */
        const val PLAYLIST_LIMIT = 1000
    }
}

/** 喜欢 / 取消喜欢接口的响应：只关心 code。 */
private data class LikeRespDto(val code: Int? = null)
