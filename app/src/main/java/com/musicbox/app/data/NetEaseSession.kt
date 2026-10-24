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
    /** 账号头像地址，来自 /nuser/account/get 或 /v1/user/detail。 */
    val avatarUrl: String? = null,
    /** 网易云累计听歌曲数（user/detail 的 listenSongs），0 表示尚未同步。 */
    val listenSongs: Int = 0,
    /** 网易云用户 id。个人内容接口（每日推荐歌单 / 我的歌单）需要它。 */
    val userId: Long = 0L,
) {
    val isLoggedIn: Boolean get() = !musicU.isNullOrBlank()

    companion object {
        /** 游客会话：可用于搜索 / 试听，不携带任何登录凭证。 */
        val Guest = NetEaseSession()
    }
}
