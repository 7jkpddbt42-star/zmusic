package com.musicbox.app.data

/**
 * 网易云登录会话。
 * - 游客态：musicU 为 null、csrf 为空串（MI-1 起一直如此）。
 * - 登录态：musicU / csrf 来自扫码成功响应里的 cookie 串。
 */
data class NetEaseSession(
    val musicU: String? = null,
    val csrf: String = "",
    val nickname: String? = null,
) {
    val isLoggedIn: Boolean get() = !musicU.isNullOrBlank()

    companion object {
        /** 游客会话：可用于搜索 / 试听，不携带任何登录凭证。 */
        val Guest = NetEaseSession()
    }
}
