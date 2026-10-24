package com.musicbox.app.source.netease

/** 一次 HTTP 往返的结果。[setCookies] 用于扫码登录 803 时从响应头取 MUSIC_U。 */
data class TransportResponse(
    val code: Int,
    val body: String,
    val setCookies: List<String> = emptyList(),
)

/**
 * 网络传输抽象。抽出来是为了让 NetEaseProvider 能在单测里注入假实现，
 * 从而不依赖外网跑完整链路。
 */
interface HttpTransport {
    fun post(url: String, body: String, headers: Map<String, String>): TransportResponse

    /** 备用源走 GET（协议与 NetEaseCloudMusicApi 一致）。 */
    fun get(url: String, headers: Map<String, String>): TransportResponse
}
