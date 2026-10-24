package com.musicbox.app.source.netease

import com.google.gson.annotations.SerializedName

/** /eapi/login/qrcode/unikey 的响应。 */
internal data class QrUnikeyRespDto(
    @SerializedName("code") val code: Int = -1,
    @SerializedName("unikey") val unikey: String? = null,
)

/** /eapi/login/qrcode/client/login 的响应。803 时 cookie 里含 MUSIC_U / __csrf。 */
internal data class QrPollRespDto(
    @SerializedName("code") val code: Int = -1,
    @SerializedName("message") val message: String? = null,
    @SerializedName("cookie") val cookie: String? = null,
)

/** /eapi/nuser/account/get 的响应。 */
internal data class AccountRespDto(
    @SerializedName("code") val code: Int = -1,
    @SerializedName("profile") val profile: ProfileDto? = null,
)

internal data class ProfileDto(
    @SerializedName("userId") val userId: Long = 0L,
    @SerializedName("nickname") val nickname: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
)

/** /eapi/v1/user/detail/{uid} 的响应，只取听歌统计与头像。 */
internal data class UserDetailRespDto(
    @SerializedName("code") val code: Int = -1,
    @SerializedName("listenSongs") val listenSongs: Int = 0,
    @SerializedName("profile") val profile: ProfileDto? = null,
)

/** 登录 cookie 串 → 键值对。形如 `MUSIC_U=xxx; __csrf=yyy; NMTID=zzz`。 */
internal fun parseCookieString(cookie: String): Map<String, String> =
    cookie.split(";")
        .mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null else part.substring(0, idx).trim() to part.substring(idx + 1).trim()
        }
        .toMap()
