package com.musicbox.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.musicbox.app.desktoplyric.DesktopLyricService
import com.musicbox.app.model.Song
import com.musicbox.app.ui.BottomTabs
import com.musicbox.app.ui.LocalPageSeed
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
import com.musicbox.app.ui.theme.SeedSource
import com.musicbox.app.ui.theme.ThemeSpec
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    MusicBoxApp(container, spec, coverSeed)
                }
            }
        }
    }
}

@Composable
private fun MusicBoxApp(container: AppContainer, spec: ThemeSpec, coverSeed: Color?) {
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
    val currentLineText by lyricVm.currentLineText.collectAsStateWithLifecycle()
    val showTranslationPref by container.settings.showTranslation.collectAsStateWithLifecycle()
    val showRomajiPref by container.settings.showRomaji.collectAsStateWithLifecycle()
    val fallbackUrl by container.settings.fallbackBaseUrl.collectAsStateWithLifecycle()
    val desktopLyricEnabled by container.settings.desktopLyricEnabled.collectAsStateWithLifecycle()
    val desktopLyricFontSp by container.settings.desktopLyricFontSp.collectAsStateWithLifecycle()
    val desktopLyricColorArgb by container.settings.desktopLyricColorArgb.collectAsStateWithLifecycle()
    val session by container.sessionStore.session.collectAsStateWithLifecycle()
    val favorites by libraryVm.favorites.collectAsStateWithLifecycle()
    val history by libraryVm.history.collectAsStateWithLifecycle()

    // 歌词开关：偏好持久化 → 注入歌词页
    LaunchedEffect(showTranslationPref) { lyricVm.setShowTranslation(showTranslationPref) }
    LaunchedEffect(showRomajiPref) { lyricVm.setShowRomaji(showRomajiPref) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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

    var tab by remember { mutableStateOf(MainTab.SEARCH) }
    var showPlayer by remember { mutableStateOf(false) }
    var showLyric by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLogin by remember { mutableStateOf(false) }
    // 播放页沉浸模式：长按播放键切换；关闭播放页时自动退出
    var immersive by remember { mutableStateOf(false) }

    LaunchedEffect(showPlayer) {
        if (!showPlayer) immersive = false
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
    // 用已有的 250ms 播放位置驱动迷你条当前行文本（不额外引入逐帧时钟）
    LaunchedEffect(playState.positionMs, lyricState, showTranslationPref) {
        lyricVm.updatePosition(playState.positionMs)
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
            // 沉浸模式隐藏底部栏位（迷你播放条 + 四标签），用纵向展开/收缩 + 淡入淡出过渡
            AnimatedVisibility(
                visible = !immersive,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    NowPlayingBar(
                        state = playState,
                        onExpand = {
                            showLyric = false
                            showPlayer = true
                        },
                        onTogglePlay = playerVm::togglePlay,
                        lyricLine = currentLineText,
                    )
                    BottomTabs(current = tab, onSelect = { tab = it })
                }
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

    val playerLayer: @Composable () -> Unit = {
        // 播放页整层：从底部滑入 / 滑出 + 淡入淡出，替换原本的瞬时切换
        AnimatedVisibility(
            visible = showPlayer,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Box {
                // 播放页：从底部滑入；进入歌词页时滑出（与歌词页互斥）
                AnimatedVisibility(
                    visible = !showLyric,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                ) {
                    PlayerScreen(
                        state = playState,
                        modeLabel = mode.label,
                        isFavorite = playState.currentSongId?.let(libraryVm::isFavorite) == true,
                        immersive = immersive,
                        onToggleImmersive = { immersive = !immersive },
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
                        onArtworkLoaded = { container.artworkTheme.update(it) },
                    )
                }
                // 歌词页：从右侧滑入 / 滑出，与播放页互斥
                AnimatedVisibility(
                    visible = showLyric,
                    enter = slideInHorizontally { it },
                    exit = slideOutHorizontally { it },
                ) {
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
            themeSpec = spec,
            desktopLyricEnabled = desktopLyricEnabled,
            desktopLyricFontSp = desktopLyricFontSp,
            desktopLyricColorArgb = desktopLyricColorArgb,
            onToggleTranslation = container.settings::setShowTranslation,
            onToggleRomaji = container.settings::setShowRomaji,
            onSaveFallbackUrl = container.settings::setFallbackBaseUrl,
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
            logText = container.log::readAll,
            onClearLog = container.log::clear,
            onBack = { showSettings = false },
        )
    }
}
