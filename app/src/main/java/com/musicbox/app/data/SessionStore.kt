package com.musicbox.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 登录会话的持久化。写入即更新 StateFlow，UI 与音源层共读一份。 */
class SessionStore(private val store: KeyValueStore) {

    private val _session = MutableStateFlow(read())
    val session: StateFlow<NetEaseSession> = _session.asStateFlow()

    fun save(musicU: String, csrf: String, nickname: String?, userId: Long = 0L) {
        store.putString(KEY_MUSIC_U, musicU)
        store.putString(KEY_CSRF, csrf)
        store.putString(KEY_NICKNAME, nickname)
        store.putString(KEY_USER_ID, userId.takeIf { it > 0 }?.toString())
        _session.value = NetEaseSession(
            musicU = musicU,
            csrf = csrf,
            nickname = nickname,
            userId = userId,
        )
    }

    /**
     * 昵称、头像、听歌统计与 uid 都是"可选增强"：扫码成功后单独再取一次账号信息，
     * 拿到了就补进会话，拿不到不影响登录态。
     */
    fun updateProfile(
        nickname: String?,
        userId: Long,
        avatarUrl: String? = null,
        listenSongs: Int = 0,
    ) {
        if (!nickname.isNullOrBlank()) {
            store.putString(KEY_NICKNAME, nickname)
        }
        if (userId > 0) {
            store.putString(KEY_USER_ID, userId.toString())
        }
        if (!avatarUrl.isNullOrBlank()) {
            store.putString(KEY_AVATAR, avatarUrl)
        }
        if (listenSongs > 0) {
            store.putString(KEY_LISTEN_SONGS, listenSongs.toString())
        }
        _session.value = _session.value.copy(
            nickname = nickname?.takeIf { it.isNotBlank() } ?: _session.value.nickname,
            avatarUrl = avatarUrl?.takeIf { it.isNotBlank() } ?: _session.value.avatarUrl,
            listenSongs = listenSongs.takeIf { it > 0 } ?: _session.value.listenSongs,
            userId = userId.takeIf { it > 0 } ?: _session.value.userId,
        )
    }

    fun clear() {
        store.remove(KEY_MUSIC_U)
        store.remove(KEY_CSRF)
        store.remove(KEY_NICKNAME)
        store.remove(KEY_USER_ID)
        store.remove(KEY_AVATAR)
        store.remove(KEY_LISTEN_SONGS)
        _session.value = NetEaseSession.Guest
    }

    private fun read(): NetEaseSession = NetEaseSession(
        musicU = store.getString(KEY_MUSIC_U)?.takeIf { it.isNotBlank() },
        csrf = store.getString(KEY_CSRF).orEmpty(),
        nickname = store.getString(KEY_NICKNAME),
        avatarUrl = store.getString(KEY_AVATAR),
        listenSongs = store.getString(KEY_LISTEN_SONGS)?.toIntOrNull() ?: 0,
        userId = store.getString(KEY_USER_ID)?.toLongOrNull() ?: 0L,
    )

    private companion object {
        const val KEY_MUSIC_U = "login_music_u"
        const val KEY_CSRF = "login_csrf"
        const val KEY_NICKNAME = "login_nickname"
        const val KEY_USER_ID = "login_user_id"
        const val KEY_AVATAR = "login_avatar"
        const val KEY_LISTEN_SONGS = "login_listen_songs"
    }
}
