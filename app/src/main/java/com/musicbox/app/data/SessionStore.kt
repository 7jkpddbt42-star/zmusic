package com.musicbox.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 登录会话的持久化。写入即更新 StateFlow，UI 与音源层共读一份。 */
class SessionStore(private val store: KeyValueStore) {

    private val _session = MutableStateFlow(read())
    val session: StateFlow<NetEaseSession> = _session.asStateFlow()

    fun save(musicU: String, csrf: String, nickname: String?) {
        store.putString(KEY_MUSIC_U, musicU)
        store.putString(KEY_CSRF, csrf)
        store.putString(KEY_NICKNAME, nickname)
        _session.value = NetEaseSession(musicU, csrf, nickname)
    }

    /** 昵称是"可选增强"：拿到了就补，拿不到不影响登录态。 */
    fun updateNickname(nickname: String?) {
        if (nickname.isNullOrBlank()) return
        store.putString(KEY_NICKNAME, nickname)
        _session.value = _session.value.copy(nickname = nickname)
    }

    fun clear() {
        store.remove(KEY_MUSIC_U)
        store.remove(KEY_CSRF)
        store.remove(KEY_NICKNAME)
        _session.value = NetEaseSession.Guest
    }

    private fun read(): NetEaseSession = NetEaseSession(
        musicU = store.getString(KEY_MUSIC_U)?.takeIf { it.isNotBlank() },
        csrf = store.getString(KEY_CSRF).orEmpty(),
        nickname = store.getString(KEY_NICKNAME),
    )

    private companion object {
        const val KEY_MUSIC_U = "login_music_u"
        const val KEY_CSRF = "login_csrf"
        const val KEY_NICKNAME = "login_nickname"
    }
}
