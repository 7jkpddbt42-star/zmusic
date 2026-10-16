package com.musicbox.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.musicbox.app.model.Song
import com.musicbox.app.ui.BottomTabs
import com.musicbox.app.ui.MainTab
import com.musicbox.app.ui.MusicBoxTheme
import com.musicbox.app.ui.library.FavoriteScreen
import com.musicbox.app.ui.library.HistoryScreen
import com.musicbox.app.ui.library.LibraryViewModel
import com.musicbox.app.ui.login.LoginScreen
import com.musicbox.app.ui.login.LoginViewModel
import com.musicbox.app.ui.lyric.LyricScreen
import com.musicbox.app.ui.lyric.LyricViewModel
import com.musicbox.app.ui.mine.MineScreen
import com.musicbox.app.ui.player.NowPlayingBar
import com.musicbox.app.ui.player.PlaybackConnection
import com.musicbox.app.ui.player.PlayerScreen
import com.musicbox.app.ui.player.PlayerViewModel
import com.musicbox.app.ui.player.QueueSheet
import com.musicbox.app.ui.search.SearchScreen
import com.musicbox.app.ui.search.SearchViewModel
import com.musicbox.app.ui.settings.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = AppContainer(this)
        setContent {
            MusicBoxTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MusicBoxApp(container)
                }
            }
        }
    }
}

@Composable
private fun MusicBoxApp(container: AppContainer) {
    val context = LocalContext.current

    // MediaController 的连接随 Composition 建立与释放
    val connection = remember { PlaybackConnection(context.applicationContext) }
    DisposableEffect(Unit) {
        connection.connect()
        onDispose { connection.release() }
    }

    val searchVm: SearchViewModel = viewModel(
        factory = viewModelFactory { initializer { SearchViewModel(container.provider) } },
    )
    val playerVm: PlayerViewModel = viewModel(
        factory = viewModelFactory { initializer { PlayerViewModel(container.provider, connection) } },
    )
    val lyricVm: LyricViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                LyricViewModel(
                    provider = container.provider,
                    diskCache = container.lyricDiskCache,
                    connectivity = container.networkMonitor.observe(),
                )
            }
        },
    )
    val libraryVm: LibraryViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LibraryViewModel(container.favorites, container.history) }
        },
    )
    val loginVm: LoginViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LoginViewModel(container.loginApi, container.sessionStore) }
        },
    )

    val searchState by searchVm.state.collectAsStateWithLifecycle()
    val playState by playerVm.state.collectAsStateWithLifecycle()
    val mode by playerVm.mode.collectAsStateWithLifecycle()
    val message by playerVm.message.collectAsStateWithLifecycle()
    val lyricState by lyricVm.state.collectAsStateWithLifecycle()
    val showTranslationPref by container.settings.showTranslation.collectAsStateWithLifecycle()
    val showRomajiPref by container.settings.showRomaji.collectAsStateWithLifecycle()
    val fallbackUrl by container.settings.fallbackBaseUrl.collectAsStateWithLifecycle()
    val session by container.sessionStore.session.collectAsStateWithLifecycle()
    val favorites by libraryVm.favorites.collectAsStateWithLifecycle()
    val history by libraryVm.history.collectAsStateWithLifecycle()

    // 歌词开关：偏好持久化 → 注入歌词页
    LaunchedEffect(showTranslationPref) { lyricVm.setShowTranslation(showTranslationPref) }
    LaunchedEffect(showRomajiPref) { lyricVm.setShowRomaji(showRomajiPref) }

    val snackbarHostState = remember { SnackbarHostState() }
    var tab by remember { mutableStateOf(MainTab.SEARCH) }
    var showPlayer by remember { mutableStateOf(false) }
    var showLyric by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLogin by remember { mutableStateOf(false) }

    // 断网 / 恢复：明确提示（设计文档 §9）
    var wasOnline by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        container.networkMonitor.observe().collect { online ->
            if (!online) {
                snackbarHostState.showSnackbar("网络已断开，播放可能中断")
            } else if (!wasOnline) {
                snackbarHostState.showSnackbar("网络已恢复")
            }
            wasOnline = online
        }
    }

    // 切歌 → 搜索列表高亮 + 歌词加载
    LaunchedEffect(playState.currentSongId) {
        searchVm.onNowPlayingChanged(playState.currentSongId)
    }
    LaunchedEffect(playState.currentSongId) {
        lyricVm.load(playState.currentSongId)
    }

    // 一次性提示
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            playerVm.consumeMessage()
        }
    }
    LaunchedEffect(playState.errorMessage) {
        playState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            playerVm.clearError()
        }
    }

    // Android 13+ 通知栏需要运行时权限（拒绝也不影响播放，只少一条通知）
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 拒绝就让用户自己去设置里开，不阻塞播放 */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun playSong(song: Song) {
        val index = searchState.results.indexOfFirst { it.id == song.id }
        if (index >= 0) {
            playerVm.play(searchState.results, index)
        } else {
            playerVm.play(listOf(song), 0)
        }
        libraryVm.recordPlayed(song)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                NowPlayingBar(
                    state = playState,
                    onExpand = {
                        showLyric = false
                        showPlayer = true
                    },
                    onTogglePlay = playerVm::togglePlay,
                )
                BottomTabs(current = tab, onSelect = { tab = it })
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when (tab) {
            MainTab.SEARCH -> SearchScreen(
                state = searchState,
                onQueryChange = searchVm::onQueryChange,
                onSearch = searchVm::search,
                nowPlayingSongId = playState.currentSongId,
                onSongClick = { index ->
                    playerVm.play(searchState.results, index)
                    searchState.results.getOrNull(index)?.let { libraryVm.recordPlayed(it) }
                },
                isFavorite = libraryVm::isFavorite,
                onToggleFavorite = { libraryVm.toggleFavorite(it) },
                modifier = contentModifier,
            )
            MainTab.FAVORITE -> FavoriteScreen(
                songs = favorites.map { it.song },
                nowPlayingSongId = playState.currentSongId,
                onSongClick = { playSong(it) },
                modifier = contentModifier,
            )
            MainTab.HISTORY -> HistoryScreen(
                songs = history.map { it.song },
                nowPlayingSongId = playState.currentSongId,
                onSongClick = { playSong(it) },
                onClear = libraryVm::clearHistory,
                modifier = contentModifier,
            )
            MainTab.MINE -> MineScreen(
                session = session,
                onLogin = { showLogin = true },
                onLogout = { container.sessionStore.clear() },
                onOpenSettings = { showSettings = true },
                favoriteCount = favorites.size,
                historyCount = history.size,
                modifier = contentModifier,
            )
        }
    }

    if (showPlayer && showLyric) {
        LyricScreen(
            state = lyricState,
            source = connection,
            title = playState.title,
            artist = playState.artist,
            showTranslation = showTranslationPref,
            showRomaji = showRomajiPref,
            onToggleTranslation = {
                container.settings.setShowTranslation(!showTranslationPref)
            },
            onToggleRomaji = {
                container.settings.setShowRomaji(!showRomajiPref)
            },
            onSeek = playerVm::seekTo,
            onCollapse = { showLyric = false },
        )
    } else if (showPlayer) {
        PlayerScreen(
            state = playState,
            modeLabel = mode.label,
            isFavorite = playState.currentSongId?.let(libraryVm::isFavorite) == true,
            onToggleFavorite = {
                val id = playState.currentSongId ?: return@PlayerScreen
                val song = searchState.results.firstOrNull { it.id == id }
                    ?: history.firstOrNull { it.song.id == id }?.song
                    ?: favorites.firstOrNull { it.song.id == id }?.song
                if (song != null) libraryVm.toggleFavorite(song)
            },
            onCollapse = { showPlayer = false },
            onTogglePlay = playerVm::togglePlay,
            onNext = playerVm::next,
            onPrevious = playerVm::previous,
            onSeek = playerVm::seekTo,
            onCycleMode = playerVm::cyclePlayMode,
            onOpenQueue = { showQueue = true },
            onOpenLyric = { showLyric = true },
        )
    }

    if (showQueue) {
        QueueSheet(
            state = playState,
            modeLabel = mode.label,
            onDismiss = { showQueue = false },
            onSelect = { index -> playerVm.selectQueueItem(index) },
            onRemove = playerVm::removeQueueItem,
            onCycleMode = playerVm::cyclePlayMode,
        )
    }

    if (showLogin) {
        LoginScreen(
            viewModel = loginVm,
            onLoggedIn = {
                showLogin = false
                tab = MainTab.MINE
            },
            onBack = {
                loginVm.reset()
                showLogin = false
            },
        )
    }

    if (showSettings) {
        SettingsScreen(
            showTranslation = showTranslationPref,
            showRomaji = showRomajiPref,
            fallbackBaseUrl = fallbackUrl,
            lyricCacheCount = container.lyricDiskCache.size(),
            onToggleTranslation = container.settings::setShowTranslation,
            onToggleRomaji = container.settings::setShowRomaji,
            onSaveFallbackUrl = container.settings::setFallbackBaseUrl,
            onClearLyricCache = {
                container.lyricDiskCache.clear()
                showSettings = false
            },
            logText = container.log::readAll,
            onClearLog = container.log::clear,
            onBack = { showSettings = false },
        )
    }
}
