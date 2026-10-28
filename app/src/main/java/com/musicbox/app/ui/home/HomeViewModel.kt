package com.musicbox.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicbox.app.data.NetEaseSession
import com.musicbox.app.data.SessionStore
import com.musicbox.app.model.PlaylistSummary
import com.musicbox.app.model.Song
import com.musicbox.app.source.netease.LoginApi
import com.musicbox.app.source.netease.PersonalApi
import com.musicbox.app.ui.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 「发现」页内容状态。 */
data class HomeUiState(
    val loading: Boolean = true,
    val daily: List<Song> = emptyList(),
    val liked: PlaylistSummary? = null,
    val playlists: List<PlaylistSummary> = emptyList(),
    val error: String? = null,
)

/**
 * 点开的歌单目标。
 *
 * [playlistId] 为空且 [isLiked] 为 false 时表示每日推荐（直接复用首页已加载的列表）；
 * [isLiked] 为 true 时表示「我喜欢的音乐」，即使还没同步到歌单摘要也会按 uid 重新解析。
 */
data class PlaylistTarget(
    val title: String,
    val coverUrl: String?,
    val subtitle: String,
    val playlistId: Long? = null,
    val isLiked: Boolean = false,
)

/** 歌单详情页状态。 */
data class PlaylistDetailUiState(
    val title: String,
    val coverUrl: String?,
    val subtitle: String,
    val loading: Boolean = false,
    val songs: List<Song> = emptyList(),
    val error: String? = null,
)

/**
 * 「发现」页：每日推荐 + 私人 FM + 我的歌单。
 *
 * 登录后自动补拉 uid（老会话可能没存），再加载网易云个人歌单；
 * 游客态仍可拿到每日推荐与私人 FM（实测接口可用），只是没有"我的歌单"。
 */
class HomeViewModel(
    private val personalApi: PersonalApi,
    private val loginApi: LoginApi,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _detail = MutableStateFlow<PlaylistDetailUiState?>(null)
    val detail: StateFlow<PlaylistDetailUiState?> = _detail.asStateFlow()

    private val _fmSongs = MutableStateFlow<List<Song>?>(null)
    val fmSongs: StateFlow<List<Song>?> = _fmSongs.asStateFlow()

    private val _fmLoading = MutableStateFlow(false)
    val fmLoading: StateFlow<Boolean> = _fmLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 当前账号已喜欢的歌曲 id（来自「我喜欢的音乐」歌单）。游客态为空。 */
    private val _likedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    val likedSongIds: StateFlow<Set<Long>> = _likedSongIds.asStateFlow()

    private var loadJob: Job? = null
    private var detailJob: Job? = null
    private var lastTarget: PlaylistTarget? = null

    /** 每日推荐按登录态缓存：uid 补拉会触发一次会话更新，避免重复请求。 */
    private var dailyCacheFor: String? = null
    private var dailyCache: List<Song> = emptyList()

    /** 账号统计（头像 / 累计听歌）按 musicU 缓存，避免会话更新触发重复请求。 */
    private var statsFetchedFor: String? = null

    /** 已喜欢歌曲集合按 musicU 缓存，只在首次登录 / 换号时拉一次。 */
    private var likedIdsFetchedFor: String? = null

    init {
        viewModelScope.launch {
            sessionStore.session.collect { load(it) }
        }
    }

    fun refresh() = load(sessionStore.session.value, forceDaily = true)

    private fun load(session: NetEaseSession, forceDaily: Boolean = false) {
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            var s = session
            if (s.isLoggedIn && s.userId <= 0) {
                loginApi.fetchAccount(s.musicU.orEmpty(), s.csrf).getOrNull()?.let { profile ->
                    sessionStore.updateProfile(
                        profile.nickname,
                        profile.userId,
                        profile.avatarUrl,
                        profile.listenSongs,
                    )
                    s = s.copy(
                        nickname = profile.nickname ?: s.nickname,
                        avatarUrl = profile.avatarUrl ?: s.avatarUrl,
                        userId = profile.userId,
                    )
                }
            }
            // 用户详情补全头像与累计听歌曲数；同一账号只拉一次，手动刷新会重新拉
            if (s.isLoggedIn && s.userId > 0 && (forceDaily || statsFetchedFor != s.musicU)) {
                loginApi.fetchUserDetail(s.userId, s.musicU.orEmpty(), s.csrf).getOrNull()?.let { detail ->
                    sessionStore.updateProfile(
                        detail.nickname,
                        detail.userId,
                        detail.avatarUrl,
                        detail.listenSongs,
                    )
                    s = s.copy(
                        nickname = detail.nickname ?: s.nickname,
                        avatarUrl = detail.avatarUrl ?: s.avatarUrl,
                        listenSongs = detail.listenSongs,
                    )
                }
                statsFetchedFor = s.musicU
            }

            val cacheKey = s.musicU.orEmpty()
            val daily = if (!forceDaily && dailyCacheFor == cacheKey && dailyCache.isNotEmpty()) {
                dailyCache
            } else {
                personalApi.dailyRecommendations().getOrElse { e ->
                    _state.update { it.copy(loading = false, error = e.toUserMessage()) }
                    return@launch
                }.also {
                    dailyCacheFor = cacheKey
                    dailyCache = it
                }
            }

            var liked: PlaylistSummary? = null
            var playlists: List<PlaylistSummary> = emptyList()
            if (s.isLoggedIn && s.userId > 0) {
                personalApi.userPlaylists(s.userId)
                    .onSuccess { all ->
                        // 正常按 specialType=5 识别；个别账号字段缺失时按名称兜底，
                        // 避免「我喜欢的音乐」因为一个可选字段缺失就整块消失。
                        liked = all.firstOrNull { it.isLiked }
                            ?: all.firstOrNull { it.name.contains(LIKED_NAME) }
                        playlists = all.filter { it.id != liked?.id }
                    }
                    .onFailure { e ->
                        // 以前这里 getOrDefault(emptyList()) 会把失败静默吞掉，
                        // 导致已登录用户看到一个空歌单列表且「我喜欢的」点了没反应。
                        _message.value = "个人歌单同步失败：${e.toUserMessage()}"
                    }
            }

            _state.value = HomeUiState(
                loading = false,
                daily = daily,
                liked = liked,
                playlists = playlists,
                error = null,
            )

            // 喜欢状态：登录后拉一次「我喜欢的音乐」曲目 id，供播放页心形按钮显示真实状态。
            // 游客态清空，避免残留上一个账号的数据。
            if (!s.isLoggedIn) {
                _likedSongIds.value = emptySet()
                likedIdsFetchedFor = null
            } else if (liked != null && likedIdsFetchedFor != s.musicU) {
                likedIdsFetchedFor = s.musicU
                personalApi.playlistSongs(liked.id).onSuccess { songs ->
                    _likedSongIds.value = songs.map { it.id }.toSet()
                }.onFailure { likedIdsFetchedFor = null }
            }
        }
    }

    /**
     * 喜欢 / 取消喜欢当前歌曲，写入网易云「我喜欢的音乐」。
     * 未登录时不发请求，直接提示。
     */
    fun toggleLike(songId: Long) {
        if (songId <= 0) return
        val s = sessionStore.session.value
        if (!s.isLoggedIn) {
            _message.value = "登录网易云后即可同步「我喜欢的音乐」"
            return
        }
        val nextLiked = songId !in _likedSongIds.value
        viewModelScope.launch {
            personalApi.likeSong(songId, nextLiked, s.userId)
                .onSuccess {
                    _likedSongIds.update { ids ->
                        if (nextLiked) ids + songId else ids - songId
                    }
                    _message.value = if (nextLiked) {
                        "已同步到网易云「我喜欢的音乐」"
                    } else {
                        "已从我喜欢的音乐移除"
                    }
                }
                .onFailure { e -> _message.value = e.toUserMessage() }
        }
    }

    /** 私人 FM：连续取几首组成一次队列。 */
    fun playFm() {
        if (_fmLoading.value) return
        viewModelScope.launch {
            _fmLoading.value = true
            personalApi.personalFm().onSuccess { songs ->
                if (songs.isEmpty()) {
                    _message.value = "私人FM暂时没有返回歌曲"
                } else {
                    _fmSongs.value = songs
                }
            }.onFailure { e ->
                _message.value = e.toUserMessage()
            }
            _fmLoading.value = false
        }
    }

    fun consumeFm() {
        _fmSongs.value = null
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun openDetail(target: PlaylistTarget) {
        lastTarget = target
        if (target.isLiked) {
            openLikedDetail(target)
            return
        }
        val id = target.playlistId
        if (id == null) {
            _detail.value = PlaylistDetailUiState(
                title = target.title,
                coverUrl = target.coverUrl,
                subtitle = target.subtitle,
                songs = _state.value.daily,
            )
            return
        }
        loadPlaylistSongs(target, id)
    }

    /**
     * 「我喜欢的音乐」不依赖首页是否已经同步到歌单摘要：
     * 现读会话补 uid → 现拉个人歌单找 liked id → 再取曲目。
     * 这样登录刚完成、歌单接口偶发失败时，点「我喜欢的」也不会没反应或被带去登录页。
     */
    private fun openLikedDetail(target: PlaylistTarget) {
        _detail.value = PlaylistDetailUiState(
            title = target.title,
            coverUrl = target.coverUrl,
            subtitle = target.subtitle,
            loading = true,
        )
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            var s = sessionStore.session.value
            if (!s.isLoggedIn) {
                _detail.value = null
                _message.value = "登录网易云后即可同步「我喜欢的音乐」"
                return@launch
            }
            if (s.userId <= 0) {
                loginApi.fetchAccount(s.musicU.orEmpty(), s.csrf).getOrNull()?.let { profile ->
                    sessionStore.updateProfile(
                        profile.nickname,
                        profile.userId,
                        profile.avatarUrl,
                        profile.listenSongs,
                    )
                    s = s.copy(userId = profile.userId)
                }
            }
            if (s.userId <= 0) {
                _detail.value = PlaylistDetailUiState(
                    title = target.title,
                    coverUrl = target.coverUrl,
                    subtitle = target.subtitle,
                    error = "账号信息还没同步完成，请稍后重试",
                )
                return@launch
            }
            val id = target.playlistId ?: personalApi.userPlaylists(s.userId)
                .getOrNull()
                ?.firstOrNull { it.isLiked || it.name.contains(LIKED_NAME) }
                ?.id
            if (id == null || id <= 0) {
                _detail.value = PlaylistDetailUiState(
                    title = target.title,
                    coverUrl = target.coverUrl,
                    subtitle = target.subtitle,
                    error = "暂时没有同步到「我喜欢的音乐」，请稍后重试",
                )
                return@launch
            }
            loadPlaylistSongs(target, id, inPlace = true)
        }
    }

    private fun loadPlaylistSongs(target: PlaylistTarget, id: Long, inPlace: Boolean = false) {
        if (!inPlace) {
            _detail.value = PlaylistDetailUiState(
                title = target.title,
                coverUrl = target.coverUrl,
                subtitle = target.subtitle,
                loading = true,
            )
            detailJob?.cancel()
        }
        detailJob = viewModelScope.launch {
            personalApi.playlistSongs(id)
                .onSuccess { songs ->
                    // 歌单详情可能带重复曲目或空 id，直接进队列会触发 LazyColumn 重复 key 崩溃
                    val cleaned = songs.filter { it.id > 0 }.distinctBy { it.id }
                    _detail.value = PlaylistDetailUiState(
                        title = target.title,
                        coverUrl = target.coverUrl ?: cleaned.firstOrNull()?.coverUrl,
                        subtitle = if (cleaned.isEmpty()) target.subtitle
                        else "${cleaned.size} 首 · ${target.title}",
                        songs = cleaned,
                    )
                }
                .onFailure { e ->
                    _detail.value = PlaylistDetailUiState(
                        title = target.title,
                        coverUrl = target.coverUrl,
                        subtitle = target.subtitle,
                        error = e.toUserMessage(),
                    )
                }
        }
    }

    fun retryDetail() {
        lastTarget?.let(::openDetail)
    }

    fun closeDetail() {
        detailJob?.cancel()
        _detail.value = null
    }
}

/** 「我喜欢的音乐」在个别账号响应里可能没有 specialType，用名称兜底识别。 */
private const val LIKED_NAME = "喜欢的音乐"
