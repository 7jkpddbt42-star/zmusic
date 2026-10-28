package com.musicbox.app.source.free

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.musicbox.app.data.AppLog
import com.musicbox.app.model.LyricData
import com.musicbox.app.model.SearchType
import com.musicbox.app.model.Song
import com.musicbox.app.model.StreamInfo
import com.musicbox.app.source.MusicProvider
import com.musicbox.app.source.netease.HttpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * 「免费听歌模式」的音源包装（设置里可随时开关）。
 *
 * 普通模式：完全直通网易云官方取流。
 * 免费模式：官方只给试听片段（VIP / 付费曲）时，再拿网易云歌曲 id 去公开第三方接口
 * （gdstudio）换完整地址；换不到就回落官方结果，绝不比普通模式更差。
 *
 * 现状与边界（均已实测）：
 * - gdstudio 按 id 能返回网易云 CDN 的完整音轨，VIP 曲已验证可以播过 30 秒试听限制；
 * - 少数曲目它只返回空地址（版权限制，换任何码率都一样），这时仍走官方试听片段；
 * - 它的 search 接口只支持网易云自家 source，所以不做「按歌名跨平台找同曲」的兜底；
 * - 网易云官方外链 /song/media/outer/url 对游客 302 到 /404，不能当通道用。
 *
 * 第三方应答必须先「探测」再采用：只有真返回音频 Content-Type 才认，避免把 404 网页当音源。
 */
class FreeSourceProvider(
    private val official: MusicProvider,
    private val transport: HttpTransport,
    private val enabled: () -> Boolean,
    private val appLog: AppLog? = null,
) : MusicProvider {

    override val id: String = "free"

    override suspend fun search(
        keyword: String,
        type: SearchType,
        page: Int,
    ): Result<List<Song>> = official.search(keyword, type, page)

    override suspend fun getLyric(songId: Long): Result<LyricData> = official.getLyric(songId)

    override suspend fun getStreamUrl(songId: Long): Result<StreamInfo> =
        withContext(Dispatchers.IO) {
            // 官方链路永远先走：登录 VIP 时它本身就是最优解，免费源只在必要时兜底
            val officialResult = official.getStreamUrl(songId)
            if (!enabled()) return@withContext officialResult

            val officialInfo = officialResult.getOrNull()
            if (officialInfo != null && !officialInfo.isTrial) return@withContext officialResult

            val freeUrl = freeStreamUrl(songId)
            if (freeUrl == null) {
                appLog?.log("free", "songId=$songId 免费源未取到完整地址，回落官方结果")
                return@withContext officialResult
            }
            Result.success(
                StreamInfo(url = freeUrl, bitrateBps = FREE_BITRATE_BPS, isTrial = false),
            )
        }

    /** 用网易云 id 向 gdstudio 换直链，探测确认是音频后才采用。 */
    private fun freeStreamUrl(songId: Long): String? {
        val body = fetch("$GDSTUDIO?types=url&source=netease&id=$songId&br=320") ?: return null
        val url = extractAudioUrls(body).take(MAX_PROBES).firstOrNull { probe(it) }
        appLog?.log("free", "gdstudio songId=$songId ${if (url != null) "命中" else "无结果"}")
        return url
    }

    /**
     * 取应答正文。第三方接口偶发瞬时连不上（实测 gdstudio 有一次 ConnectException，
     * 几秒后同样的请求就正常了），所以网络层失败时补一次重试。
     */
    private fun fetch(url: String): String? {
        repeat(FETCH_ATTEMPTS) { attempt ->
            try {
                val response = transport.get(url, HEADERS)
                if (response.code in 200..299) return response.body
                appLog?.log("free", "gdstudio HTTP ${response.code} ${response.body.take(80)}")
                return null
            } catch (e: IOException) {
                appLog?.log("free", "gdstudio 请求失败（第 ${attempt + 1} 次）", e)
                if (attempt < FETCH_ATTEMPTS - 1) Thread.sleep(RETRY_DELAY_MS)
            }
        }
        return null
    }

    /** 用 Range 请求确认目标真的是音频流（而不是网页 / 404 / 图片）。 */
    private fun probe(url: String): Boolean = try {
        val response = transport.get(url, HEADERS + ("Range" to "bytes=0-1023"))
        val contentType = response.contentType.orEmpty().lowercase()
        val isAudio = contentType.startsWith("audio") ||
            contentType.contains("mpeg") ||
            contentType.contains("octet-stream")
        val ok = response.code in 200..299 && isAudio
        if (!ok) appLog?.log("free", "探测失败 code=${response.code} type=$contentType")
        ok
    } catch (e: IOException) {
        false
    }

    /**
     * 从应答里挖出音频地址：应答可能是纯文本 URL，也可能是结构未知的 JSON。
     * 优先取 url / link 这类直链字段，再退回「看着像音频」的字符串，
     * 最终都以 [probe] 的探测结果为准。
     */
    private fun extractAudioUrls(body: String): List<String> {
        val text = body.trim()
        if (text.startsWith("http://") || text.startsWith("https://")) {
            return listOf(text.substringBefore('\n').trim())
        }
        val root: JsonElement = try {
            JsonParser.parseString(text)
        } catch (e: Exception) {
            return emptyList()
        }
        val keyed = mutableListOf<String>()
        val guessed = mutableListOf<String>()
        collectAudioUrls(root, keyed, guessed, key = null)
        return (keyed + guessed).distinct()
    }

    private fun collectAudioUrls(
        element: JsonElement,
        keyed: MutableList<String>,
        guessed: MutableList<String>,
        key: String?,
    ) {
        when {
            element.isJsonPrimitive -> {
                if (element.asJsonPrimitive.isString) {
                    val value = element.asString.trim()
                    if (!value.startsWith("http://") && !value.startsWith("https://")) return
                    if (key in DIRECT_LINK_KEYS) keyed += value else if (looksLikeAudio(value)) guessed += value
                }
            }
            element.isJsonObject -> element.asJsonObject.entrySet().forEach { entry ->
                collectAudioUrls(entry.value, keyed, guessed, entry.key)
            }
            element.isJsonArray -> element.asJsonArray.forEach { collectAudioUrls(it, keyed, guessed, key) }
        }
    }

    private fun looksLikeAudio(value: String): Boolean {
        val lower = value.lowercase()
        val path = lower.substringBefore('?')
        return AUDIO_EXTENSIONS.any { path.endsWith(it) } ||
            AUDIO_HOST_HINTS.any { lower.contains(it) }
    }

    private companion object {
        const val GDSTUDIO = "https://music-api.gdstudio.xyz/api.php"

        /** 第三方直链普遍是 128kbps 左右的 mp3，按该档位上报，冷启动不夸大。 */
        const val FREE_BITRATE_BPS = 128_000

        /** 最多探测几个候选地址，避免异常应答把请求数放大。 */
        const val MAX_PROBES = 3

        /** 网络层失败重试次数与间隔：只给瞬时抖动留余量，不做长等待。 */
        const val FETCH_ATTEMPTS = 2
        const val RETRY_DELAY_MS = 350L

        val DIRECT_LINK_KEYS = setOf("url", "link", "src", "musicUrl", "playUrl")

        val AUDIO_EXTENSIONS = listOf(".mp3", ".m4a", ".flac", ".aac", ".ogg", ".wav", ".opus")

        val AUDIO_HOST_HINTS = listOf(
            "music.126.net",
            "kuwo.cn",
            "kugou.com",
            "qq.com/music",
            "/audio",
        )

        val HEADERS = mapOf(
            "User-Agent" to
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36",
            "Accept" to "application/json, text/plain, */*",
        )
    }
}
