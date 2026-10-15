package com.musicbox.app.source.netease

/** 一次 HTTP 往返的结果。 */
data class TransportResponse(val code: Int, val body: String)

/**
 * 网络传输抽象。抽出来是为了让 NetEaseProvider 能在单测里注入假实现，
 * 从而不依赖外网跑完整链路。
 */
interface HttpTransport {
    fun post(url: String, body: String, headers: Map<String, String>): TransportResponse
}
