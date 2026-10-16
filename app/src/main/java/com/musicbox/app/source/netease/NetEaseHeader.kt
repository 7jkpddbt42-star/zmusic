package com.musicbox.app.source.netease

import java.net.URLEncoder
import java.security.SecureRandom

/**
 * eapi 请求的 header / Cookie 构造。
 *
 * 事实依据（同 NetEaseCrypto）：eapi 分支里 Cookie 直接由 header 对象
 * 经 encodeURIComponent(k)=encodeURIComponent(v) 以 "; " 连接而成，
 * 且同一个 header 对象会被塞进加密 JSON 的 header 字段。
 */
object NetEaseHeader {

    const val UA = "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)"

    private const val OSVER = "Microsoft-Windows-10-Professional-build-19045-64bit"
    private const val APPVER = "3.1.17.204416"
    private const val VERSION_CODE = "140"
    private const val RESOLUTION = "1920x1080"
    private const val CHANNEL = "netease"

    private val random = SecureRandom()

    /** 生成 32 位大写 HEX 设备号，应由调用方持久化以保证身份稳定。 */
    fun newDeviceId(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
    }

    /** 构造 eapi header。未登录时 csrf 为空串。 */
    fun build(deviceId: String, csrf: String = ""): Map<String, String> {
        val now = System.currentTimeMillis()
        return linkedMapOf(
            "osver" to OSVER,
            "deviceId" to deviceId,
            "os" to "pc",
            "appver" to APPVER,
            "versioncode" to VERSION_CODE,
            "mobilename" to "",
            "buildver" to now.toString().take(10),
            "resolution" to RESOLUTION,
            "__csrf" to csrf,
            "channel" to CHANNEL,
            "requestId" to "${now}_${random.nextInt(1000).toString().padStart(4, '0')}",
        )
    }

    /**
     * 由 header 对象拼 Cookie。登录后额外带上 MUSIC_U（网易云鉴权的核心 cookie），
     * 使 VIP / 完整版音质生效。
     */
    fun cookieHeader(header: Map<String, String>, musicU: String? = null): String {
        val base = header.entries.joinToString("; ") { (key, value) ->
            encode(key) + "=" + encode(value)
        }
        return if (musicU.isNullOrBlank()) base else "$base; MUSIC_U=" + encode(musicU)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
