package com.musicbox.app.source.netease

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
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

/**
 * 抽出来是为了让登录状态机可单测。
 *
 * 三个方法都是 **suspend**：实现内部会切到 IO 线程发网络请求，避免在 Main 线程阻塞
 * （登录页的轮询跑在 viewModelScope 即 Main 调度器上）。
 */
interface LoginApi {
    suspend fun createQrKey(): Result<String>
    suspend fun poll(unikey: String): Result<QrPollResult>
    suspend fun fetchNickname(musicU: String, csrf: String): Result<String?>
}

/**
 * 扫码登录接口。**复用 eapi 签名**，不需要 weapi / RSA（实测结论见计划 §1.1）。
 * 与 NetEaseProvider 一样只做"组 JSON → 签名 → 发请求 → 解析"。
 */
class NetEaseLoginApi(
    private val transport: HttpTransport,
    private val deviceId: String,
    private val gson: Gson = Gson(),
) : LoginApi {

    /** 取二维码 key。二维码内容用 `https://music.163.com/login?codekey=<unikey>`。 */
    override suspend fun createQrKey(): Result<String> = call(
        uri = UNIKEY_URI,
        payload = linkedMapOf<String, Any>("type" to 1, "e_r" to false, "header" to NetEaseHeader.build(deviceId)),
    ) { body ->
        val dto = gson.fromJson(body, QrUnikeyRespDto::class.java)
        val key = dto?.unikey
        if (dto?.code != 200 || key.isNullOrBlank()) {
            throw SourceError.Api(dto?.code ?: -1, null)
        }
        key
    }

    /** 轮询扫码状态。调用方每 2 秒轮一次。 */
    override suspend fun poll(unikey: String): Result<QrPollResult> = call(
        uri = POLL_URI,
        payload = linkedMapOf<String, Any>(
            "key" to unikey,
            "type" to 1,
            "e_r" to false,
            "header" to NetEaseHeader.build(deviceId),
        ),
    ) { body ->
        val dto = gson.fromJson(body, QrPollRespDto::class.java)
        when (dto?.code) {
            801 -> QrPollResult.Waiting
            800 -> QrPollResult.Expired
            802 -> QrPollResult.Scanned
            803 -> {
                val cookie = parseCookieString(dto.cookie.orEmpty())
                val musicU = cookie["MUSIC_U"]
                    ?: throw SourceError.Parse("登录成功但响应缺少 MUSIC_U", null)
                QrPollResult.Success(musicU = musicU, csrf = cookie["__csrf"].orEmpty())
            }
            else -> throw SourceError.Api(dto?.code ?: -1, null)
        }
    }

    /** 取昵称（可选增强）。失败不影响登录态。 */
    override suspend fun fetchNickname(musicU: String, csrf: String): Result<String?> = call(
        uri = ACCOUNT_URI,
        payload = linkedMapOf<String, Any>("e_r" to false, "header" to NetEaseHeader.build(deviceId, csrf)),
        musicU = musicU,
    ) { body ->
        gson.fromJson(body, AccountRespDto::class.java)?.profile?.nickname
    }

    /** 统一封装：构造 header → 签名 → post → 解析。bodyMapper 里抛 SourceError 即失败。 */
    private suspend fun <T> call(
        uri: String,
        payload: Map<String, Any>,
        musicU: String? = null,
        bodyMapper: (String) -> T,
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
            val response = transport.post(EAPI_BASE + uri.replaceFirst("/api", "/eapi"), body, headers)
            Result.success(bodyMapper(response.body))
        } catch (e: SourceError) {
            Result.failure(e)
        } catch (e: JsonSyntaxException) {
            Result.failure(SourceError.Parse("响应不是合法 JSON", e))
        } catch (e: IOException) {
            Result.failure(SourceError.Network(e))
        }
    }

    private companion object {
        const val EAPI_BASE = "https://interfacepc.music.163.com"
        const val UNIKEY_URI = "/api/login/qrcode/unikey"
        const val POLL_URI = "/api/login/qrcode/client/login"
        const val ACCOUNT_URI = "/api/nuser/account/get"
    }
}
