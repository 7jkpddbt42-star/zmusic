package com.musicbox.app.source.netease

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.musicbox.app.data.AppLog
import com.musicbox.app.source.SourceError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URLEncoder

/** 轮询结果。语义见计划 §1.1。 */
sealed interface QrPollResult {
    /** 801：等待用户扫码。 */
    data object Waiting : QrPollResult
    /** 800：二维码已过期，需要重新取 key。 */
    data object Expired : QrPollResult
    /** 802：已扫码，等待用户在手机上确认。 */
    data object Scanned : QrPollResult
    /** 803：登录成功，携带 MUSIC_U / __csrf。 */
    data class Success(val musicU: String, val csrf: String) : QrPollResult
}

/** 账号基础信息。uid 用于拉取"我的歌单"等个人内容。 */
data class AccountProfile(
    val userId: Long,
    val nickname: String?,
    val avatarUrl: String? = null,
    /** 累计听歌曲数（user/detail 的 listenSongs），0 表示未知。 */
    val listenSongs: Int = 0,
)

/**
 * 抽出来是为了让登录状态机可单测。
 *
 * 三个方法都是 **suspend**：实现内部会切到 IO 线程发网络请求，避免在 Main 线程阻塞
 * （登录页的轮询跑在 viewModelScope 即 Main 调度器上）。
 */
interface LoginApi {
    suspend fun createQrKey(): Result<String>
    suspend fun poll(unikey: String): Result<QrPollResult>
    suspend fun fetchAccount(musicU: String, csrf: String): Result<AccountProfile?>

    /** 用户详情（头像 / 累计听歌）。默认空实现，测试替身不必实现。 */
    suspend fun fetchUserDetail(uid: Long, musicU: String, csrf: String): Result<AccountProfile?> =
        Result.success(null)
}

/**
 * 扫码登录接口。**复用 eapi 签名**，不需要 weapi / RSA（实测结论见计划 §1.1）。
 * 与 NetEaseProvider 一样只做"组 JSON → 签名 → 发请求 → 解析"。
 */
class NetEaseLoginApi(
    private val transport: HttpTransport,
    private val deviceId: String,
    private val appLog: AppLog? = null,
    private val gson: Gson = Gson(),
) : LoginApi {

    /** 取二维码 key。二维码内容用 `https://music.163.com/login?codekey=<unikey>`。 */
    override suspend fun createQrKey(): Result<String> = call(
        uri = UNIKEY_URI,
        payload = linkedMapOf<String, Any>("type" to 3, "e_r" to false, "header" to NetEaseHeader.build(deviceId)),
    ) { response ->
        val dto = gson.fromJson(response.body, QrUnikeyRespDto::class.java)
        val key = dto?.unikey
        if (dto?.code != 200 || key.isNullOrBlank()) {
            throw SourceError.Api(dto?.code ?: -1, null)
        }
        key
    }

    /** 轮询扫码状态。调用方每 2 秒轮一次。 */
    override suspend fun poll(unikey: String): Result<QrPollResult> {
        val payload = linkedMapOf<String, Any>(
            "key" to unikey,
            // 必须与取 unikey 时一致；3 是端上扫码登录的客户端类型，803 才会下发 cookie。
            "type" to 3,
            "e_r" to false,
            "header" to NetEaseHeader.build(deviceId),
        )
        val first = call(uri = POLL_URI, payload = payload, baseUrl = EAPI_BASE, bodyMapper = ::mapPollBody)
        val error = first.exceptionOrNull()
        // interfacepc 不下发 Set-Cookie 时，换标准 eapi 域名再取一次凭证。
        if (error is SourceError.Parse && error.message == MISSING_CREDENTIAL) {
            return call(
                uri = POLL_URI,
                payload = payload,
                baseUrl = FALLBACK_EAPI_BASE,
                bodyMapper = ::mapPollBody,
            )
        }
        return first
    }

    private fun mapPollBody(response: TransportResponse): QrPollResult {
        val dto = gson.fromJson(response.body, QrPollRespDto::class.java)
        return when (dto?.code) {
            801 -> QrPollResult.Waiting
            800 -> QrPollResult.Expired
            802 -> QrPollResult.Scanned
            803 -> {
                // 凭证位置随接口实现不同：weapi 放 JSON 的 cookie 字段，
                // eapi 走 Set-Cookie 响应头，两处都取一遍。
                val bodyCookies = parseCookieString(dto.cookie.orEmpty())
                val headerCookies = response.setCookies.fold(emptyMap<String, String>()) { acc, raw ->
                    acc + parseCookieString(raw)
                }
                val cookie = bodyCookies + headerCookies
                val musicU = cookie["MUSIC_U"]
                if (musicU.isNullOrBlank()) {
                    appLog?.log(
                        "login",
                        "803 未取到 MUSIC_U：body=${bodyCookies.keys}，" +
                            "set-cookie=${response.setCookies.map { it.substringBefore('=') }}",
                    )
                    throw SourceError.Parse(MISSING_CREDENTIAL, null)
                }
                QrPollResult.Success(musicU = musicU, csrf = cookie["__csrf"].orEmpty())
            }
            else -> throw SourceError.Api(dto?.code ?: -1, null)
        }
    }

    /** 取账号信息（昵称 + uid + 头像，可选增强）。失败不影响登录态。 */
    override suspend fun fetchAccount(musicU: String, csrf: String): Result<AccountProfile?> = call(
        uri = ACCOUNT_URI,
        payload = linkedMapOf<String, Any>("e_r" to false, "header" to NetEaseHeader.build(deviceId, csrf)),
        musicU = musicU,
    ) { response ->
        val profile = gson.fromJson(response.body, AccountRespDto::class.java)?.profile
        profile?.takeIf { it.userId > 0 }?.let {
            AccountProfile(it.userId, it.nickname, it.avatarUrl)
        }
    }

    /** 用户详情：补全头像与累计听歌曲数。响应缺字段时按缺省值返回。 */
    override suspend fun fetchUserDetail(
        uid: Long,
        musicU: String,
        csrf: String,
    ): Result<AccountProfile?> = call(
        uri = "/api/v1/user/detail/$uid",
        payload = linkedMapOf<String, Any>("e_r" to false, "header" to NetEaseHeader.build(deviceId, csrf)),
        musicU = musicU,
    ) { response ->
        val dto = gson.fromJson(response.body, UserDetailRespDto::class.java)
        if (dto?.code != 200) throw SourceError.Api(dto?.code ?: -1, null)
        AccountProfile(
            userId = dto.profile?.userId?.takeIf { it > 0 } ?: uid,
            nickname = dto.profile?.nickname,
            avatarUrl = dto.profile?.avatarUrl,
            listenSongs = dto.listenSongs,
        )
    }

    /** 统一封装：构造 header → 签名 → post → 解析。bodyMapper 里抛 SourceError 即失败。 */
    private suspend fun <T> call(
        uri: String,
        payload: Map<String, Any>,
        musicU: String? = null,
        baseUrl: String = EAPI_BASE,
        bodyMapper: (TransportResponse) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            val header = payload["header"] as Map<String, String>
            val params = NetEaseCrypto.eapiParams(uri, gson.toJson(payload))
            val body = "params=" + URLEncoder.encode(params, "UTF-8")
            val headers = mapOf(
                "User-Agent" to NetEaseHeader.UA,
                "Cookie" to NetEaseHeader.cookieHeader(header, musicU),
                "Referer" to "https://music.163.com",
            )
            // uri 形如 /api/login/qrcode/unikey → /eapi/login/qrcode/unikey
            val response = transport.post(baseUrl + uri.replaceFirst("/api", "/eapi"), body, headers)
            Result.success(bodyMapper(response))
        } catch (e: SourceError) {
            appLog?.log("login", "$uri 失败", e)
            Result.failure(e)
        } catch (e: JsonSyntaxException) {
            appLog?.log("login", "$uri 响应解析失败", e)
            Result.failure(SourceError.Parse("响应不是合法 JSON", e))
        } catch (e: IOException) {
            appLog?.log("login", "$uri 网络异常", e)
            Result.failure(SourceError.Network(e))
        }
    }

    private companion object {
        const val EAPI_BASE = "https://interfacepc.music.163.com"

        /** 备用 eapi 域名：interfacepc 未返回登录 cookie 时回退到这里再取一次。 */
        const val FALLBACK_EAPI_BASE = "https://music.163.com"

        /** 803 但 JSON / Set-Cookie 都没有 MUSIC_U。 */
        const val MISSING_CREDENTIAL = "登录成功但未取到登录凭证，请重试"

        const val UNIKEY_URI = "/api/login/qrcode/unikey"
        const val POLL_URI = "/api/login/qrcode/client/login"
        const val ACCOUNT_URI = "/api/nuser/account/get"
    }
}
