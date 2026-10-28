package com.musicbox.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicbox.app.data.SessionStore
import com.musicbox.app.player.sharedSongStreamCache
import com.musicbox.app.source.SourceError
import com.musicbox.app.source.netease.LoginApi
import com.musicbox.app.source.netease.QrPollResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 扫码登录界面状态。 */
sealed interface LoginUiState {
    data object Idle : LoginUiState
    /** 正在申请二维码。 */
    data object Loading : LoginUiState
    /** 二维码已就绪，等待扫码。[notice] 不为空时在二维码上方显示一条提示（如"已自动刷新"）。 */
    data class Waiting(val qrContent: String, val notice: String? = null) : LoginUiState
    /** 已扫码，等待手机确认。 */
    data object Scanned : LoginUiState
    /** 二维码过期，可点"刷新"。 */
    data object Expired : LoginUiState
    /** 登录成功。 */
    data class Success(val nickname: String?) : LoginUiState
    /** 出错（网络/接口）。 */
    data class Failed(val message: String) : LoginUiState
}

/**
 * 扫码登录状态机。
 * 流程：createQrKey → Waiting → 每 2s 轮询 → Scanned → Success。
 * 二维码过期（800）→ 自动重新取 key。轮询失败不清空二维码，等下一次轮询自愈。
 */
class LoginViewModel(
    private val api: LoginApi,
    private val sessionStore: SessionStore,
    private val pollIntervalMs: Long = 2_000L,
) : ViewModel() {

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    /** 打开登录页时调用。幂等：已在等扫码就不重复申请。 */
    fun start() {
        if (_state.value is LoginUiState.Waiting || _state.value is LoginUiState.Scanned) return
        if (sessionStore.session.value.isLoggedIn) {
            _state.value = LoginUiState.Success(sessionStore.session.value.nickname)
            return
        }
        requestQrKey()
    }

    /** "刷新二维码"。 */
    fun refresh() {
        pollJob?.cancel()
        requestQrKey()
    }

    fun reset() {
        pollJob?.cancel()
        _state.value = LoginUiState.Idle
    }

    private fun requestQrKey(notice: String? = null) {
        pollJob?.cancel()
        _state.value = LoginUiState.Loading
        pollJob = viewModelScope.launch {
            api.createQrKey()
                .onSuccess { key ->
                    _state.value = LoginUiState.Waiting(QR_PREFIX + key, notice)
                    pollLoop(key)
                }
                .onFailure { _state.value = LoginUiState.Failed("二维码获取失败，请检查网络") }
        }
    }

    private suspend fun pollLoop(unikey: String) {
        while (viewModelScope.isActive) {
            delay(pollIntervalMs)
            val result = api.poll(unikey)
            val error = result.exceptionOrNull()
            when {
                // 纯网络抖动：保留二维码，下一轮重试即可自愈，不打扰用户。
                error is SourceError.Network -> Unit
                // 其它失败（接口异常 / 解析失败）不会自愈，必须让用户看到真实原因并结束轮询。
                error != null -> {
                    _state.value = LoginUiState.Failed("登录失败：${error.message ?: "未知错误"}")
                    return
                }
                else -> when (val r = result.getOrThrow()) {
                    QrPollResult.Waiting -> Unit
                    QrPollResult.Scanned -> _state.value = LoginUiState.Scanned
                    QrPollResult.Expired -> {
                        // 过期后自动换新码，但必须显式告知用户旧码已作废，避免"白扫"。
                        requestQrKey(EXPIRED_NOTICE) // 会 cancel 本 job
                        return
                    }
                    is QrPollResult.Success -> {
                        sessionStore.save(r.musicU, r.csrf, null)
                        // 游客态取流拿到的多是 30 秒试听地址，登录后必须作废重取
                        sharedSongStreamCache.clear()
                        _state.value = LoginUiState.Success(null)
                        // 昵称 / 头像 / 累计听歌是可选增强，单独再取一次；失败不影响登录成功
                        api.fetchAccount(r.musicU, r.csrf).getOrNull()?.let { profile ->
                            sessionStore.updateProfile(
                                profile.nickname,
                                profile.userId,
                                profile.avatarUrl,
                                profile.listenSongs,
                            )
                            _state.value = LoginUiState.Success(profile.nickname)
                            // 账号接口不带听歌统计，再取一次用户详情补全
                            api.fetchUserDetail(profile.userId, r.musicU, r.csrf).getOrNull()?.let { detail ->
                                sessionStore.updateProfile(
                                    detail.nickname,
                                    detail.userId,
                                    detail.avatarUrl,
                                    detail.listenSongs,
                                )
                            }
                        }
                        return
                    }
                }
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val QR_PREFIX = "https://music.163.com/login?codekey="

        /** 二维码过期自动刷新后的提示文案。 */
        const val EXPIRED_NOTICE = "二维码已过期，已为你刷新，请重新扫描"
    }
}
