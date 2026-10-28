package com.musicbox.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.musicbox.app.data.BackgroundSpec
import com.musicbox.app.data.BackgroundType
import com.musicbox.app.data.PlayerStyle
import com.musicbox.app.desktoplyric.DesktopLyricService
import com.musicbox.app.player.sharedSongStreamCache
import com.musicbox.app.ui.BottomTabs
import com.musicbox.app.ui.LocalPageSeed
import com.musicbox.app.ui.MainTab
import com.musicbox.app.ui.MusicBoxTheme
import com.musicbox.app.ui.components.liquidglass.GlassAmbientBackdrop
import com.musicbox.app.ui.components.liquidglass.LiquidGlassPreviewContent
import com.musicbox.app.ui.components.liquidglass.LocalGlassBackdrop
import com.musicbox.app.ui.components.liquidglass.glassBackdropSource
import com.musicbox.app.ui.components.liquidglass.rememberGlassBackdrop
import com.musicbox.app.ui.components.player.LocalPlayerBackgroundBlur
import com.musicbox.app.ui.home.AllPlaylistsScreen
import com.musicbox.app.ui.home.HomeScreen
import com.musicbox.app.ui.home.HomeViewModel
import com.musicbox.app.ui.home.PlaylistDetailScreen
import com.musicbox.app.ui.home.PlaylistTarget
import com.musicbox.app.ui.library.HistoryScreen
import com.musicbox.app.ui.library.LibraryViewModel
import com.musicbox.app.ui.login.LoginScreen
import com.musicbox.app.ui.login.LoginViewModel
import com.musicbox.app.ui.lyric.LyricScreen
import com.musicbox.app.ui.lyric.LyricViewModel
import com.musicbox.app.ui.mine.MineScreen
import com.musicbox.app.ui.player.NowPlayingBar
import com.musicbox.app.ui.player.PlaybackConnection
import com.musicbox.app.ui.player.PlaybackUiState
import com.musicbox.app.ui.player.PlayerScreen
import com.musicbox.app.ui.player.PlayerViewModel
import com.musicbox.app.ui.player.QueueSheet
import com.musicbox.app.ui.styles.PlayerActions
import com.musicbox.app.ui.search.SearchScreen
import com.musicbox.app.ui.search.SearchViewModel
import com.musicbox.app.ui.settings.SettingsScreen
import com.musicbox.app.ui.theme.SeedSource
import com.musicbox.app.ui.theme.ThemeSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** 按来源解析当前生效的主题 seed。壁纸取色在低版本/取色失败时返回 null。 */
private fun resolveSeed(
    source: SeedSource,
    fixedArgb: Int,
    coverSeed: Color?,
    container: AppContainer,
): Color? = when (source) {
    SeedSource.Cover -> coverSeed
    SeedSource.Wallpaper -> container.wallpaperSeed()
    SeedSource.Fixed -> Color(fixedArgb)
}

class MainActivity : ComponentActivity() {
    /** 从通知栏 / 锁屏媒体卡片点进来的次数，UI 收到后切到播放页。 */
    private val openPlayerRequests = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 全面屏：内容绘制到状态栏/导航栏下方，由各页的 WindowInsets 自行留白
        enableEdgeToEdge()
        consumeOpenPlayerIntent(intent)
        val container = AppContainer(this)
        setContent {
            val spec by container.settings.themeSpec.collectAsStateWithLifecycle()
            val coverSeed by container.artworkTheme.seed.collectAsStateWithLifecycle()
            // 主页 seed：按主 seedSource 解析（壁纸/固定色不依赖封面）
            val mainSeed = remember(spec.seedSource, spec.fixedSeedArgb, coverSeed) {
                resolveSeed(spec.seedSource, spec.fixedSeedArgb, coverSeed, container)
            }
            MusicBoxTheme(spec = spec, seed = mainSeed) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MusicBoxApp(container, spec, coverSeed, openPlayerRequests)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeOpenPlayerIntent(intent)
    }

    private fun consumeOpenPlayerIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true) {
            intent.removeExtra(EXTRA_OPEN_PLAYER)
            openPlayerRequests.value += 1
        }
    }

    companion object {
        /** 通知栏 / 锁屏媒体卡片点击后直达播放页。 */
        const val EXTRA_OPEN_PLAYER = "com.musicbox.app.extra.OPEN_PLAYER"
    }
}

@Composable
private fun MusicBoxApp(
    container: AppContainer,
    spec: ThemeSpec,
    coverSeed: Color?,
    openPlayerRequests: StateFlow<Int>,
) {
    val context = LocalContext.current

    // MediaController 的连接随 Composition 建立与释放；播放模式用上次的选择初始化
    val connection = remember {
        PlaybackConnection(
            context.applicationContext,
            initialMode = container.settings.playMode.value,
            onModeChange = container.settings::setPlayMode,
        )
    }
    DisposableEffect(Unit) {
        connection.connect()
        onDispose { connection.release() }
    }

    val searchVm: SearchViewModel = viewModel(
        factory = viewModelFactory { initializer { SearchViewModel(container.provider) } },
    )
    val playerVm: PlayerViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                PlayerViewModel(container.provider, connection, container.listeningStats)
            }
        },
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
            initializer { LibraryViewModel(container.history) }
        },
    )
    val loginVm: LoginViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LoginViewModel(container.loginApi, container.sessionStore) }
        },
    )
    val homeVm: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    personalApi = container.personalApi,
                    loginApi = container.loginApi,
                    sessionStore = container.sessionStore,
                )
            }
        },
    )

    val searchState by searchVm.state.collectAsStateWithLifecycle()
    // 位置每 250ms 变化一次，但页面结构（歌曲/标题/播放态/队列）不需要跟着重组。
    // 顶层只订阅去掉位置的结构态；播放页内部再单独订阅完整状态，
    // 避免主界面每秒被 4 次位置刷新带着重组。
    val playState by remember(playerVm) {
        playerVm.state
            .map { state -> if (state.positionMs == 0L) state else state.copy(positionMs = 0L) }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = PlaybackUiState())
    val mode by playerVm.mode.collectAsStateWithLifecycle()
    val message by playerVm.message.collectAsStateWithLifecycle()
    val lyricState by lyricVm.state.collectAsStateWithLifecycle()
    val currentLineText by lyricVm.currentLineText.collectAsStateWithLifecycle()
    // 播放页歌词预览：原文与译文分两排，迷你条继续用拼接后的 currentLineText
    val currentLineOriginal by lyricVm.currentLineOriginal.collectAsStateWithLifecycle()
    val currentLineTranslation by lyricVm.currentLineTranslation.collectAsStateWithLifecycle()
    val homeState by homeVm.state.collectAsStateWithLifecycle()
    val homeDetail by homeVm.detail.collectAsStateWithLifecycle()
    val fmSongs by homeVm.fmSongs.collectAsStateWithLifecycle()
    val fmLoading by homeVm.fmLoading.collectAsStateWithLifecycle()
    val homeMessage by homeVm.message.collectAsStateWithLifecycle()
    val showTranslationPref by container.settings.showTranslation.collectAsStateWithLifecycle()
    val showRomajiPref by container.settings.showRomaji.collectAsStateWithLifecycle()
    val lyricStylePref by container.settings.lyricStyle.collectAsStateWithLifecycle()
    val playerStylePref by container.settings.playerStyle.collectAsStateWithLifecycle()
    val fallbackUrl by container.settings.fallbackBaseUrl.collectAsStateWithLifecycle()
    val freeSourceEnabled by container.settings.freeSourceEnabled.collectAsStateWithLifecycle()
    val desktopLyricEnabled by container.settings.desktopLyricEnabled.collectAsStateWithLifecycle()
    val desktopLyricFontSp by container.settings.desktopLyricFontSp.collectAsStateWithLifecycle()
    val desktopLyricColorArgb by container.settings.desktopLyricColorArgb.collectAsStateWithLifecycle()
    val backgroundSpec by container.settings.backgroundSpec.collectAsStateWithLifecycle()
    val backgroundBlurPref by container.settings.backgroundBlur.collectAsStateWithLifecycle()
    val session by container.sessionStore.session.collectAsStateWithLifecycle()
    val listeningSeconds by container.listeningStats.totalSeconds.collectAsStateWithLifecycle()
    val history by libraryVm.history.collectAsStateWithLifecycle()
    // 网易云「我喜欢的音乐」曲目 id：播放页心形按钮的真实状态来源
    val likedSongIds by homeVm.likedSongIds.collectAsStateWithLifecycle()

    // 歌词开关：偏好持久化 → 注入歌词页
    LaunchedEffect(showTranslationPref) { lyricVm.setShowTranslation(showTranslationPref) }
    LaunchedEffect(showRomajiPref) { lyricVm.setShowRomaji(showRomajiPref) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 播放页背景素材选择器：选中后复制到私有目录并持久化；取消（uri 为 null）不做任何事
    val backgroundImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val path = container.backgroundStore.importImage(uri)
            if (path != null) {
                container.settings.setBackgroundImage(path)
            } else {
                scope.launch { snackbarHostState.showSnackbar("无法读取所选文件") }
            }
        }
    }
    val backgroundVideoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            val path = container.backgroundStore.importVideo(uri)
            if (path != null) {
                container.settings.setBackgroundVideo(path)
            } else {
                scope.launch { snackbarHostState.showSnackbar("无法读取所选文件") }
            }
        }
    }

    // 桌面歌词悬浮窗：无权限时跳系统「显示在其他应用上层」设置页，返回后回读状态再决定启停
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (Settings.canDrawOverlays(context)) {
            container.settings.setDesktopLyricEnabled(true)
            ContextCompat.startForegroundService(
                context,
                Intent(context, DesktopLyricService::class.java),
            )
        } else {
            scope.launch { snackbarHostState.showSnackbar("未授予悬浮窗权限，桌面歌词未开启") }
        }
    }

    // App 重进时恢复：开关已开且权限仍在 → 重新拉起悬浮窗服务
    LaunchedEffect(Unit) {
        if (desktopLyricEnabled && Settings.canDrawOverlays(context)) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DesktopLyricService::class.java),
            )
        }
    }

    var tab by remember { mutableStateOf(MainTab.HOME) }
    var showPlayer by remember { mutableStateOf(false) }
    var showLyric by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLogin by remember { mutableStateOf(false) }
    var showGlassDemo by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showPlaylists by remember { mutableStateOf(false) }

    // 从通知栏 / 锁屏媒体卡片进入 → 直接展开播放页
    val openPlayerRequest by openPlayerRequests.collectAsStateWithLifecycle()
    LaunchedEffect(openPlayerRequest) {
        if (openPlayerRequest > 0) {
            showLyric = false
            showPlayer = true
        }
    }

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
    // 歌词当前行跟随播放位置刷新：直接向连接层采样，不把 250ms 位置写回结构态
    LaunchedEffect(lyricState, showTranslationPref) {
        while (true) {
            lyricVm.updatePosition(connection.positionMs())
            delay(250)
        }
    }
    // 有歌 / 播没播 变化 → 刷新桌面歌词悬浮窗的可见性与播放态（行级，与位置解耦）
    LaunchedEffect(playState.hasCurrent, playState.isPlaying) {
        lyricVm.updatePlayback(playState.hasCurrent, playState.isPlaying)
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

    // 私人FM：取到歌单后直接起播，并记一条历史
    LaunchedEffect(fmSongs) {
        val songs = fmSongs ?: return@LaunchedEffect
        playerVm.play(songs, 0)
        songs.firstOrNull()?.let(libraryVm::recordPlayed)
        homeVm.consumeFm()
    }
    LaunchedEffect(homeMessage) {
        homeMessage?.let {
            snackbarHostState.showSnackbar(it)
            homeVm.consumeMessage()
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

    // 壳层玻璃背景：页面环境背景 + 标签内容整体作为背景源，底部浮层玻璃实时采样它
    val shellBackdrop = rememberGlassBackdrop()
    // 页面级背景源：只录环境背景，供页内玻璃控件（搜索栏 / 列表面板）采样，
    // 避免它们采样到自身产生鬼影
    val pageBackdrop = rememberGlassBackdrop()
    // 底部浮层（迷你条 + 四标签）实测高度：内容区据此向下留出空隙
    var overlayHeightPx by remember { mutableStateOf(0) }
    val overlayBottomPadding = with(LocalDensity.current) { overlayHeightPx.toDp() }

    // 状态栏/导航栏图标：浅色主题用深色图标；播放页/歌词页/验收页背景始终压暗，固定用浅色图标
    val darkSystemSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    SystemBarsIcons(lightIcons = darkSystemSurface || showPlayer || showLyric || showGlassDemo)

    CompositionLocalProvider(
        LocalGlassBackdrop provides shellBackdrop,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 内容区：整体是玻璃背景源
            Box(modifier = Modifier.fillMaxSize().glassBackdropSource(shellBackdrop)) {
                // 环境背景：铺满整屏（含状态栏与底部浮层之下），四个标签页共享同一份背景源；
                // 纯色背景会让玻璃模糊不可见，所以用当前背景/封面的真实纹理
                Box(modifier = Modifier.fillMaxSize().glassBackdropSource(pageBackdrop)) {
                    GlassAmbientBackdrop(
                        background = backgroundSpec,
                        artworkUrl = playState.artworkUrl,
                        scrim = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // 页内玻璃采样页面级背景源；底部浮层仍采样壳层背景源
                CompositionLocalProvider(LocalGlassBackdrop provides pageBackdrop) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                                ),
                            )
                            .padding(bottom = overlayBottomPadding),
                    ) {
                        when (tab) {
                            MainTab.HOME -> HomeScreen(
                                state = homeState,
                                session = session,
                                history = history.map { it.song },
                                fmLoading = fmLoading,
                                nowPlayingSongId = playState.currentSongId,
                                onLogin = { showLogin = true },
                                onRefresh = homeVm::refresh,
                                onPlayFm = homeVm::playFm,
                                onOpenTarget = homeVm::openDetail,
                                onOpenPlaylists = { showPlaylists = true },
                                onOpenHistory = { showHistory = true },
                                onPlayList = { songs, index ->
                                    playerVm.play(songs, index)
                                    songs.getOrNull(index)?.let(libraryVm::recordPlayed)
                                },
                                modifier = Modifier,
                            )
                            MainTab.SEARCH -> SearchScreen(
                                state = searchState,
                                onQueryChange = searchVm::onQueryChange,
                                onSearch = searchVm::search,
                                onQuickSearch = searchVm::quickSearch,
                                nowPlayingSongId = playState.currentSongId,
                                onSongClick = { index ->
                                    playerVm.play(searchState.results, index)
                                    searchState.results.getOrNull(index)?.let { libraryVm.recordPlayed(it) }
                                },
                                modifier = Modifier,
                            )
                            MainTab.MINE -> MineScreen(
                                session = session,
                                history = history.map { it.song },
                                liked = homeState.liked,
                                playlists = homeState.playlists,
                                listeningSeconds = listeningSeconds,
                                onLogin = { showLogin = true },
                                onLogout = {
                                    container.sessionStore.clear()
                                    sharedSongStreamCache.clear()
                                },
                                onOpenSettings = { showSettings = true },
                                onOpenHistory = { showHistory = true },
                                onOpenTarget = homeVm::openDetail,
                                onOpenPlaylists = { showPlaylists = true },
                                modifier = Modifier,
                            )
                        }

                        // 列表类覆盖页放在内容区内渲染：底部迷你播放条与导航保持可见、可点。
                        // 之前它们画在整个壳层之上，会把迷你条一起盖住。

                        // 最近播放（从「我的」进入）
                        if (showHistory) {
                            BackHandler { showHistory = false }
                            GlassOverlay(
                                background = backgroundSpec,
                                artworkUrl = playState.artworkUrl,
                            ) { overlayModifier ->
                                val songs = history.map { it.song }
                                HistoryScreen(
                                    songs = songs,
                                    nowPlayingSongId = playState.currentSongId,
                                    onSongClick = { song ->
                                        val index = songs.indexOfFirst { it.id == song.id }
                                        if (index >= 0) {
                                            playerVm.play(songs, index)
                                            libraryVm.recordPlayed(song)
                                        }
                                    },
                                    onClear = libraryVm::clearHistory,
                                    onBack = { showHistory = false },
                                    modifier = overlayModifier,
                                )
                            }
                        }

                        // 全部歌单（首页/「我的」只预览前几个，完整列表走这里的 LazyColumn）
                        if (showPlaylists) {
                            BackHandler { showPlaylists = false }
                            GlassOverlay(
                                background = backgroundSpec,
                                artworkUrl = playState.artworkUrl,
                            ) { overlayModifier ->
                                AllPlaylistsScreen(
                                    playlists = homeState.playlists,
                                    onOpenPlaylist = { playlist ->
                                        homeVm.openDetail(
                                            PlaylistTarget(
                                                title = playlist.name,
                                                coverUrl = playlist.coverUrl,
                                                subtitle = "${playlist.trackCount} 首 · 我的歌单",
                                                playlistId = playlist.id,
                                            ),
                                        )
                                    },
                                    onBack = { showPlaylists = false },
                                    modifier = overlayModifier,
                                )
                            }
                        }

                        // 歌单详情（每日推荐 / 我喜欢的音乐 / 个人歌单）
                        homeDetail?.let { detail ->
                            BackHandler { homeVm.closeDetail() }
                            GlassOverlay(
                                background = backgroundSpec,
                                artworkUrl = playState.artworkUrl,
                            ) { overlayModifier ->
                                PlaylistDetailScreen(
                                    state = detail,
                                    nowPlayingSongId = playState.currentSongId,
                                    onBack = homeVm::closeDetail,
                                    onPlayAll = {
                                        if (detail.songs.isNotEmpty()) {
                                            playerVm.play(detail.songs, 0)
                                            libraryVm.recordPlayed(detail.songs[0])
                                        }
                                    },
                                    onSongClick = { index ->
                                        playerVm.play(detail.songs, index)
                                        detail.songs.getOrNull(index)?.let(libraryVm::recordPlayed)
                                    },
                                    onRetry = homeVm::retryDetail,
                                    modifier = overlayModifier,
                                )
                            }
                        }
                    }
                }
            }

            // 底部浮层：悬浮玻璃迷你条 + 导航胶囊，四周留白并避开手势条
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .onSizeChanged { overlayHeightPx = it.height },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NowPlayingBar(
                    state = playState,
                    source = connection,
                    onExpand = {
                        showLyric = false
                        showPlayer = true
                    },
                    onTogglePlay = playerVm::togglePlay,
                    lyricLine = currentLineText,
                )
                BottomTabs(
                    current = tab,
                    onSelect = { next ->
                        // 切换标签时回到标签根页面，避免详情页残留盖在新标签之上
                        homeVm.closeDetail()
                        showPlaylists = false
                        showHistory = false
                        tab = next
                    },
                )
            }

        }
    }

    // 分控主题：开启时播放页/歌词页用 player seed 覆写 LocalPageSeed，主页保持主 seed
    val playerSeed = remember(
        spec.separatePlayerTheme,
        spec.playerSeedSource,
        spec.playerFixedSeedArgb,
        coverSeed,
    ) {
        if (spec.separatePlayerTheme) {
            resolveSeed(spec.playerSeedSource, spec.playerFixedSeedArgb, coverSeed, container)
        } else {
            null
        }
    }

    // 播放页动作契约：风格布局只通过这些回调操作播放器，不接触控制器
    val playerActions = PlayerActions(
        onCollapse = { showPlayer = false },
        onTogglePlay = playerVm::togglePlay,
        onPrevious = playerVm::previous,
        onNext = playerVm::next,
        onSeek = playerVm::seekTo,
        onCycleMode = playerVm::cyclePlayMode,
        onOpenQueue = { showQueue = true },
        onOpenLyric = { showLyric = true },
        onToggleLike = {
            playState.currentSongId?.let(homeVm::toggleLike)
        },
        onSelectQueueItem = playerVm::selectQueueItem,
        onOpenStyleSwitcher = {},
        onSelectStyle = { style ->
            container.settings.setPlayerStyle(style)
            scope.launch { snackbarHostState.showSnackbar("已切换到${style.label}") }
        },
        onNotify = { text -> scope.launch { snackbarHostState.showSnackbar(text) } },
        onArtworkLoaded = { container.artworkTheme.update(it) },
    )

    val playerLayer: @Composable () -> Unit = {
        // 背景模糊档位下发到所有播放页风格；设置在播放页打开时修改也会立即生效
        CompositionLocalProvider(LocalPlayerBackgroundBlur provides backgroundBlurPref.radiusDp.dp) {
            // 播放页整层：淡入淡出，不再从底部滑入
            AnimatedVisibility(
                visible = showPlayer,
                enter = fadeIn(tween(320)),
                exit = fadeOut(tween(260)),
            ) {
                // 完整播放状态（含 250ms 位置）只在播放页可见时订阅：
                // 关闭时自动取消，主界面不会被位置刷新牵连重组。
                val playerState by playerVm.state.collectAsStateWithLifecycle()
                // 不透明底色：播放页 ↔ 歌词页交叉淡化时不会透出下面的主界面
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    // 播放页与歌词页互斥，切换时淡入淡出（不做横向滑动）
                    AnimatedVisibility(
                        visible = !showLyric,
                        enter = fadeIn(tween(300)),
                        exit = fadeOut(tween(240)),
                    ) {
                        PlayerScreen(
                            state = playerState,
                            source = connection,
                            background = backgroundSpec,
                            modeLabel = mode.label,
                            isLiked = playerState.currentSongId?.let { it in likedSongIds } == true,
                            lyricLine = currentLineOriginal,
                            lyricTranslation = currentLineTranslation,
                            playerStyle = playerStylePref,
                            actions = playerActions,
                        )
                    }
                    // 歌词页：与播放页交叉淡化
                    AnimatedVisibility(
                        visible = showLyric,
                        enter = fadeIn(tween(320)),
                        exit = fadeOut(tween(240)),
                    ) {
                        LyricScreen(
                            state = lyricState,
                            source = connection,
                            title = playState.title,
                            artist = playState.artist,
                            background = backgroundSpec,
                            artworkUrl = playState.artworkUrl,
                            showTranslation = showTranslationPref,
                            showRomaji = showRomajiPref,
                            lyricStyle = lyricStylePref,
                            onToggleTranslation = {
                                container.settings.setShowTranslation(!showTranslationPref)
                            },
                            onToggleRomaji = {
                                container.settings.setShowRomaji(!showRomajiPref)
                            },
                            onSeek = playerVm::seekTo,
                            onCollapse = { showLyric = false },
                        )
                    }
                }
            }
        }
    }

    if (playerSeed != null) {
        CompositionLocalProvider(LocalPageSeed provides playerSeed) {
            // seed 传 null：由 LocalPageSeed 覆写为播放页专属 seed
            MusicBoxTheme(spec = spec, seed = null) { playerLayer() }
        }
    } else {
        playerLayer()
    }

    if (showQueue) {
        QueueSheet(
            state = playState,
            modeLabel = mode.label,
            background = backgroundSpec,
            artworkUrl = playState.artworkUrl,
            onDismiss = { showQueue = false },
            onSelect = { index -> playerVm.selectQueueItem(index) },
            onRemove = playerVm::removeQueueItem,
            onCycleMode = playerVm::cyclePlayMode,
        )
    }

    if (showLogin) {
        // 系统返回键回到上一页，而不是直接退出应用
        BackHandler { loginVm.reset(); showLogin = false }
        LoginScreen(
            viewModel = loginVm,
            background = backgroundSpec,
            artworkUrl = playState.artworkUrl,
            onLoggedIn = {
                showLogin = false
                tab = MainTab.HOME
            },
            onBack = {
                loginVm.reset()
                showLogin = false
            },
        )
    }

    if (showSettings) {
        // 系统返回键回到上一页，而不是直接退出应用
        BackHandler { showSettings = false }
        SettingsScreen(
            showTranslation = showTranslationPref,
            showRomaji = showRomajiPref,
            lyricStyle = lyricStylePref,
            playerStyle = playerStylePref,
            fallbackBaseUrl = fallbackUrl,
            freeSourceEnabled = freeSourceEnabled,
            lyricCacheCount = container.lyricDiskCache.size(),
            themeSpec = spec,
            background = backgroundSpec,
            backgroundBlur = backgroundBlurPref,
            artworkUrl = playState.artworkUrl,
            desktopLyricEnabled = desktopLyricEnabled,
            desktopLyricFontSp = desktopLyricFontSp,
            desktopLyricColorArgb = desktopLyricColorArgb,
            onToggleTranslation = container.settings::setShowTranslation,
            onToggleRomaji = container.settings::setShowRomaji,
            onLyricStyle = container.settings::setLyricStyle,
            onPlayerStyle = container.settings::setPlayerStyle,
            onSaveFallbackUrl = container.settings::setFallbackBaseUrl,
            onFreeSourceEnabled = { enabled ->
                container.settings.setFreeSourceEnabled(enabled)
                // 旧地址是按旧模式取的：清空后当前歌立刻按新模式重新取流（进度保留）
                sharedSongStreamCache.clear()
                playerVm.reloadCurrentStream()
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (enabled) "已开启免费听歌模式，正在重新取流" else "已切换回普通模式",
                    )
                }
            },
            onClearLyricCache = {
                container.lyricDiskCache.clear()
                showSettings = false
            },
            onThemeMode = container.settings::setThemeMode,
            onSeedSource = container.settings::setSeedSource,
            onFixedSeedArgb = container.settings::setFixedSeedArgb,
            onSeparatePlayerTheme = container.settings::setSeparatePlayerTheme,
            onPlayerSeedSource = container.settings::setPlayerSeedSource,
            onPlayerFixedSeedArgb = container.settings::setPlayerFixedSeedArgb,
            onDesktopLyricEnabled = { want ->
                when {
                    // 关闭：清偏好并停服务
                    !want -> {
                        container.settings.setDesktopLyricEnabled(false)
                        context.stopService(Intent(context, DesktopLyricService::class.java))
                    }
                    // 已授权：直接开
                    Settings.canDrawOverlays(context) -> {
                        container.settings.setDesktopLyricEnabled(true)
                        ContextCompat.startForegroundService(
                            context,
                            Intent(context, DesktopLyricService::class.java),
                        )
                    }
                    // 未授权：跳系统授权页，返回后由 launcher 回调决定
                    else -> overlayPermissionLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }
            },
            onDesktopLyricFontSp = container.settings::setDesktopLyricFontSp,
            onDesktopLyricColorArgb = container.settings::setDesktopLyricColorArgb,
            backgroundLabel = when (backgroundSpec.type) {
                BackgroundType.Cover -> "当前：封面模糊"
                BackgroundType.Image -> "当前：自定义图片"
                BackgroundType.Video -> "当前：自定义视频"
            },
            onPickBackgroundImage = {
                backgroundImagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onPickBackgroundVideo = { backgroundVideoPicker.launch("video/*") },
            onResetBackground = {
                container.settings.resetBackground()
                container.backgroundStore.clearAll()
            },
            onBackgroundBlur = container.settings::setBackgroundBlur,
            logText = container.log::readAll,
            onClearLog = container.log::clear,
            onBack = { showSettings = false },
            onOpenGlassDemo = { showGlassDemo = true },
        )
    }

    // 全屏纹理验收页：同窗口覆盖，状态栏图标按深色背景切浅色（无独立 Dialog 窗口）
    if (showGlassDemo) {
        BackHandler { showGlassDemo = false }
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF14141A))) {
            LiquidGlassPreviewContent()
            TextButton(
                onClick = { showGlassDemo = false },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 12.dp),
            ) {
                Text("关闭", color = Color.White)
            }
        }
    }

    // 提示条画在最顶层：播放页、歌词页、登录页、设置页都会盖住壳层，
    // 提示（例如喜欢同步的结果或失败原因）必须能显示在这些页面之上。
    Box(modifier = Modifier.fillMaxSize()) {
        TopToastHost(
            state = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = overlayBottomPadding)
                .padding(horizontal = 24.dp),
        )
    }
}

/**
 * 顶层提示条。Material 的 SnackbarHost 在播放页之上实测不显示，这里自绘一个
 * 淡入淡出的胶囊提示，保证任何页面上的操作反馈都可见。
 */
@Composable
private fun TopToastHost(
    state: SnackbarHostState,
    modifier: Modifier = Modifier,
    durationMs: Long = 3200,
) {
    val data = state.currentSnackbarData
    // 退出动画期间 data 已为 null，用最后一条文案撑住内容
    var message by remember { mutableStateOf("") }
    if (data != null && data.visuals.message.isNotBlank()) message = data.visuals.message
    LaunchedEffect(data) {
        if (data != null) {
            delay(durationMs)
            data.dismiss()
        }
    }
    AnimatedVisibility(
        visible = data != null,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(180)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.94f))
                .padding(horizontal = 20.dp, vertical = 13.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }
}

/**
 * 全屏玻璃覆盖页（最近播放 / 歌单详情）。
 * 自带环境背景源：覆盖在主界面上时，页内玻璃只采样这一层背景，不会把被遮住的
 * 列表糊进玻璃里。
 */
@Composable
private fun GlassOverlay(
    background: BackgroundSpec,
    artworkUrl: String?,
    content: @Composable (Modifier) -> Unit,
) {
    val backdrop = rememberGlassBackdrop()
    Box(modifier = Modifier.fillMaxSize()) {
        GlassAmbientBackdrop(
            background = background,
            artworkUrl = artworkUrl,
            scrim = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
            modifier = Modifier.fillMaxSize().glassBackdropSource(backdrop),
        )
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            // 覆盖页已渲染在内容区内（该区已处理状态栏/刘海与底部浮层留白），
            // 这里只铺满自身区域，避免重复 inset 造成二次留白。
            content(Modifier.fillMaxSize())
        }
    }
}

/**
 * 全面屏系统栏图标明暗：浅色界面用深色图标，深色界面/压暗页面用浅色图标。
 * enableEdgeToEdge() 已把系统栏设为透明，这里只负责图标对比度。
 */
@Composable
private fun SystemBarsIcons(lightIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = view.context.findActivity()?.window ?: return
    // 只在目标取值变化时下发一次，避免每次重组都覆盖其它窗口（如验收页 Dialog）的设定
    LaunchedEffect(lightIcons) {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !lightIcons
            isAppearanceLightNavigationBars = !lightIcons
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context: Context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
