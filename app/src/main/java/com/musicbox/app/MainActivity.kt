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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.musicbox.app.data.BackupRepository
import com.musicbox.app.data.PlayerStyle
import com.musicbox.app.desktoplyric.DesktopLyricService
import com.musicbox.app.model.Song
import com.musicbox.app.player.DownloadState
import com.musicbox.app.player.DownloadStates
import com.musicbox.app.player.DownloadTasks
import com.musicbox.app.player.Downloads
import com.musicbox.app.player.SongDiskCache
import com.musicbox.app.player.SongDownloader
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
import com.musicbox.app.ui.components.MusicIcons
import com.musicbox.app.ui.components.player.DownloadAction
import com.musicbox.app.ui.components.player.LocalPlayerBackgroundBlur
import com.musicbox.app.ui.home.AllPlaylistsScreen
import com.musicbox.app.ui.home.HomeScreen
import com.musicbox.app.ui.home.HomeViewModel
import com.musicbox.app.ui.home.PlaylistDetailScreen
import com.musicbox.app.ui.home.PlaylistDetailUiState
import com.musicbox.app.ui.home.PlaylistTarget
import com.musicbox.app.ui.library.DownloadedScreen
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 备份文件名：z-music-backup-20261101.json，同一天多次导出会由系统自动加序号。 */
private fun backupFileName(): String =
    "z-music-backup-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.json"

/** 备份文件上限：正常备份只有几十 KB，超过 4 MB 的几乎一定是选错了文件。 */
private const val MAX_BACKUP_BYTES = 4 * 1024 * 1024

/**
 * 读取备份文本并对体积设上限。
 * 不用 `readBytes()`：万一用户在系统文件选择器里点了一个大视频，整份读进内存会直接 OOM。
 */
private fun readBackupText(input: java.io.InputStream): String? {
    val buffer = ByteArray(8 * 1024)
    val out = java.io.ByteArrayOutputStream()
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        out.write(buffer, 0, count)
        if (out.size() > MAX_BACKUP_BYTES) return null
    }
    return out.toString(Charsets.UTF_8.name())
}

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
        factory = viewModelFactory {
            initializer { SearchViewModel(container.provider, container.searchHistory) }
        },
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
                    likedStore = container.liked,
                )
            }
        },
    )

    val searchState by searchVm.state.collectAsStateWithLifecycle()
    // 最近搜索词只在空态可见，单独订阅，避免搜索过程带着它一起重组
    val searchHistoryEntries by searchVm.historyEntries.collectAsStateWithLifecycle()
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
    val sleepTimerLabel by playerVm.sleepTimerLabel.collectAsStateWithLifecycle()
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
    val smartTransition by container.settings.smartTransition.collectAsStateWithLifecycle()
    val audioFxEnabled by container.settings.audioFxEnabled.collectAsStateWithLifecycle()
    val audioFxBands by container.settings.audioFxBands.collectAsStateWithLifecycle()
    val customAudioFx by container.settings.customAudioFx.collectAsStateWithLifecycle()
    val audioQuality by container.settings.audioQuality.collectAsStateWithLifecycle()
    // 已下载列表只在「下载完成 / 删除」时变化，收在顶层不会带来逐帧重组
    val downloadedSongs by container.downloads.songs.collectAsStateWithLifecycle()
    val downloadTasks by DownloadTasks.tasks.collectAsStateWithLifecycle()
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

    // 备份导出：系统文稿界面选保存位置，内容是本机收藏与播放记录的 JSON。
    // 读写都放 IO 线程（记录最多 200 条、收藏可能更多），结果只用底部提示，不弹对话框。
    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) {
            scope.launch { snackbarHostState.showSnackbar("已取消导出") }
        } else {
            scope.launch {
                val text = withContext(Dispatchers.IO) { container.backup.export() }
                val written = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(text.toByteArray(Charsets.UTF_8))
                        } ?: error("无法写入所选位置")
                    }
                }
                written.fold(
                    onSuccess = { snackbarHostState.showSnackbar("备份已导出") },
                    onFailure = {
                        snackbarHostState.showSnackbar("导出失败：${it.message ?: "无法写入文件"}")
                    },
                )
            }
        }
    }

    // 备份恢复：只合并、不覆盖。先做格式预检，选错文件时给一句人话而不是抛解析异常。
    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { readBackupText(it) }
                    }.getOrNull()
                }
                when {
                    text.isNullOrBlank() ->
                        snackbarHostState.showSnackbar("无法读取所选文件（备份应小于 4 MB）")
                    !BackupRepository.looksLikeBackup(text) ->
                        snackbarHostState.showSnackbar("所选文件不是 z-music 备份")
                    else -> withContext(Dispatchers.IO) { container.backup.import(text) }.fold(
                        onSuccess = { result ->
                            snackbarHostState.showSnackbar(
                                "已恢复：新增收藏 ${result.likedCount} 首、播放记录 ${result.historyCount} 条",
                            )
                        },
                        onFailure = { error ->
                            snackbarHostState.showSnackbar(
                                "恢复失败：${error.message ?: "文件格式不正确"}",
                            )
                        },
                    )
                }
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
    var showDownloads by remember { mutableStateOf(false) }
    var showPlaylists by remember { mutableStateOf(false) }

    // 从通知栏 / 锁屏媒体卡片进入 → 直接展开播放页
    val openPlayerRequest by openPlayerRequests.collectAsStateWithLifecycle()
    LaunchedEffect(openPlayerRequest) {
        if (openPlayerRequest > 0) {
            showLyric = false
            showPlayer = true
        }
    }

    // 断网 / 恢复：顶部常驻胶囊提示（设计文档 §9）。
    // 断网时一直挂着，恢复后短暂显示「网络已恢复」再淡出，不打断任何操作。
    var networkBanner by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        var wasOnline = true
        container.networkMonitor.observe().collect { online ->
            when {
                !online -> networkBanner = "网络已断开，播放可能中断"
                !wasOnline -> {
                    networkBanner = "网络已恢复"
                    delay(1800)
                    networkBanner = null
                }
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

    // 输入法弹出时，迷你条与标签栏本来就被键盘盖住；若仍为它们留出底部空隙，
    // 页面（尤其搜索结果）会在键盘上方凭空少掉一截，页面里再补一次键盘高度就成双重留白。
    // 所以 IME 可见时把这段留白归零，只让页面按键盘高度避让。
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val contentBottomPadding = if (imeVisible) 0.dp else overlayBottomPadding

    // 标签页的滚动位置与「入场动画是否已播放」由壳层持有：
    // 页面切走时会被销毁，状态放在这里才能避免切回时滚回顶部、重播整页入场。
    val homeScroll = rememberScrollState()
    val mineScroll = rememberScrollState()
    var homeEntranceDone by remember { mutableStateOf(false) }
    var searchEntranceDone by remember { mutableStateOf(false) }
    var mineEntranceDone by remember { mutableStateOf(false) }

    // 歌单详情关闭后保留最后一份数据，供退出动画期间继续渲染
    var lastDetail by remember { mutableStateOf<PlaylistDetailUiState?>(null) }
    LaunchedEffect(homeDetail) {
        if (homeDetail != null) lastDetail = homeDetail
    }

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
                        // 遮罩只负责压住封面细节，页底色另叠一层（见下方 0.36），
                        // 两层合计约五成白，既保留封面的色彩氛围又不影响正文阅读
                        scrim = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // 页内玻璃采样页面级背景源；底部浮层仍采样壳层背景源
                CompositionLocalProvider(LocalGlassBackdrop provides pageBackdrop) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // 页面底色做成纵向渐变：顶部留更多封面色彩，视线进入内容后
                            // 逐步过渡到接近纸面的浅色。均匀半透明会把深色封面压成一整片
                            // 灰蒙层，渐变则像光从顶部漏进页面，列表区始终干净好读。
                            .background(
                                Brush.verticalGradient(
                                    0f to MaterialTheme.colorScheme.surface.copy(alpha = 0.30f),
                                    0.42f to MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                                    1f to MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
                                ),
                            )
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                                ),
                            )
                            .padding(bottom = contentBottomPadding),
                    ) {
                        // 记录各标签页已经播过入场，切回来时不再重播
                        LaunchedEffect(tab) {
                            when (tab) {
                                MainTab.HOME -> homeEntranceDone = true
                                MainTab.SEARCH -> searchEntranceDone = true
                                MainTab.MINE -> mineEntranceDone = true
                            }
                        }

                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = {
                                // 旧页快速淡出并极轻地上浮（视差），新页稍晚淡入、
                                // 从下方一小段距离浮起。两层只在很短的窗口内重叠，
                                // 既没有双层文字重影，也不是生硬硬切。
                                (
                                    fadeIn(
                                        tween(260, delayMillis = 50, easing = FastOutSlowInEasing),
                                    ) + slideInVertically(
                                        animationSpec = tween(
                                            durationMillis = 340,
                                            delayMillis = 50,
                                            easing = FastOutSlowInEasing,
                                        ),
                                        initialOffsetY = { height -> height / 64 },
                                    )
                                    togetherWith (
                                        fadeOut(tween(120, easing = FastOutLinearInEasing)) +
                                            slideOutVertically(
                                                animationSpec = tween(
                                                    durationMillis = 200,
                                                    easing = FastOutLinearInEasing,
                                                ),
                                                targetOffsetY = { height -> -height / 110 },
                                            )
                                        )
                                ).using(SizeTransform(clip = false))
                            },
                            label = "tabSwitch",
                        ) { targetTab ->
                            when (targetTab) {
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
                                onOpenSearch = { tab = MainTab.SEARCH },
                                modifier = Modifier,
                                scrollState = homeScroll,
                                animateEntrance = !homeEntranceDone,
                            )
                            MainTab.SEARCH -> SearchScreen(
                                state = searchState,
                                history = searchHistoryEntries,
                                onQueryChange = searchVm::onQueryChange,
                                onSearch = searchVm::search,
                                onQuickSearch = searchVm::quickSearch,
                                onClearHistory = searchVm::clearHistory,
                                nowPlayingSongId = playState.currentSongId,
                                onSongClick = { index ->
                                    playerVm.play(searchState.results, index)
                                    searchState.results.getOrNull(index)?.let { libraryVm.recordPlayed(it) }
                                },
                                modifier = Modifier,
                                animateEntrance = !searchEntranceDone,
                            )
                            MainTab.MINE -> MineScreen(
                                session = session,
                                history = history.map { it.song },
                                liked = homeState.liked,
                                playlists = homeState.playlists,
                                listeningSeconds = listeningSeconds,
                                downloadedCount = downloadedSongs.size,
                                onLogin = { showLogin = true },
                                onLogout = {
                                    container.sessionStore.clear()
                                    sharedSongStreamCache.clear()
                                },
                                onOpenSettings = { showSettings = true },
                                onOpenHistory = { showHistory = true },
                                onOpenDownloads = { showDownloads = true },
                                onOpenTarget = homeVm::openDetail,
                                onOpenPlaylists = { showPlaylists = true },
                                modifier = Modifier,
                                scrollState = mineScroll,
                                animateEntrance = !mineEntranceDone,
                            )
                            }
                        }

                        // 列表类覆盖页放在内容区内渲染：底部迷你播放条与导航保持可见、可点。
                        // 之前它们画在整个壳层之上，会把迷你条一起盖住。

                        // 最近播放（从「我的」进入）
                        AnimatedVisibility(visible = showHistory, enter = OverlayEnter, exit = OverlayExit) {
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

                        // 已下载（从「我的」进入）：点歌直接读本地文件，行尾可删除
                        AnimatedVisibility(visible = showDownloads, enter = OverlayEnter, exit = OverlayExit) {
                            BackHandler { showDownloads = false }
                            GlassOverlay(
                                background = backgroundSpec,
                                artworkUrl = playState.artworkUrl,
                            ) { overlayModifier ->
                                DownloadedScreen(
                                    songs = downloadedSongs,
                                    tasks = downloadTasks,
                                    nowPlayingSongId = playState.currentSongId,
                                    onSongClick = { song ->
                                        val songs = downloadedSongs.map { it.song }
                                        val index = songs.indexOfFirst { it.id == song.id }
                                        if (index >= 0) {
                                            playerVm.play(songs, index)
                                            libraryVm.recordPlayed(song)
                                        }
                                    },
                                    onDelete = { songId ->
                                        container.downloads.remove(songId)
                                        SongDownloader.clear(songId)
                                    },
                                    onClearAll = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                container.downloads.clearAll()
                                                SongDownloader.clearAll()
                                            }
                                            snackbarHostState.showSnackbar("已清空所有下载")
                                        }
                                    },
                                    // 下载中 → 暂停；已暂停 / 失败 → 继续（有 .part 时自动续传）
                                    onToggleTask = { task ->
                                        val paused = task.state is DownloadState.Paused
                                        val failed = task.state is DownloadState.Failed
                                        if (task.state is DownloadState.InProgress) {
                                            SongDownloader.pause(task.song.id)
                                        } else {
                                            SongDownloader.start(context, container.provider, task.song)
                                        }
                                        if (paused || failed) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("继续下载：${task.song.name}")
                                            }
                                        }
                                    },
                                    onCancelTask = { songId ->
                                        SongDownloader.cancel(songId)
                                        scope.launch { snackbarHostState.showSnackbar("已取消下载") }
                                    },
                                    onBack = { showDownloads = false },
                                    modifier = overlayModifier,
                                )
                            }
                        }

                        // 全部歌单（首页/「我的」只预览前几个，完整列表走这里的 LazyColumn）
                        AnimatedVisibility(visible = showPlaylists, enter = OverlayEnter, exit = OverlayExit) {
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
                        AnimatedVisibility(
                            visible = homeDetail != null,
                            enter = OverlayEnter,
                            exit = OverlayExit,
                        ) {
                            // 关闭时 homeDetail 立刻变 null，退出动画期间用最后一份数据顶着，
                            // 否则收起的一瞬间内容会闪空
                            val detail = homeDetail ?: lastDetail ?: return@AnimatedVisibility
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
                                    onDownloadAll = {
                                        val pending = detail.songs.filter {
                                            !container.downloads.isDownloaded(it.id)
                                        }
                                        pending.forEach {
                                            SongDownloader.start(context, container.provider, it)
                                        }
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                if (pending.isEmpty()) {
                                                    "这个歌单已经全部下载好了"
                                                } else {
                                                    "开始下载 ${pending.size} 首"
                                                },
                                            )
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

            // 迷你条按「是否有当前歌曲」显隐：收起期间保留最后一份有效状态，
            // 让退出动画还有内容可渲染，不会在最后一帧突然塌成空白。
            var lastBarState by remember { mutableStateOf<PlaybackUiState?>(null) }
            if (playState.hasCurrent) lastBarState = playState

            // 底部浮层：悬浮玻璃迷你条 + 导航胶囊，四周留白并避开手势条
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .onSizeChanged { overlayHeightPx = it.height },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 首曲开始播放时迷你条从零高度展开、淡入，页面底部留白同步过渡，
                // 不会让整页内容瞬间被顶起一截。
                AnimatedVisibility(
                    visible = playState.hasCurrent,
                    enter = expandVertically(
                        animationSpec = tween(260, easing = FastOutSlowInEasing),
                        expandFrom = Alignment.Bottom,
                    ) + fadeIn(tween(220, easing = FastOutSlowInEasing)),
                    exit = shrinkVertically(
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        shrinkTowards = Alignment.Bottom,
                    ) + fadeOut(tween(140, easing = FastOutLinearInEasing)),
                    label = "miniBar",
                ) {
                    lastBarState?.let { barState ->
                        NowPlayingBar(
                            state = barState,
                            source = connection,
                            onExpand = {
                                showLyric = false
                                showPlayer = true
                            },
                            onTogglePlay = playerVm::togglePlay,
                            lyricLine = currentLineText,
                        )
                    }
                }
                BottomTabs(
                    current = tab,
                    onSelect = { next ->
                        // 切换标签时回到标签根页面，避免详情页残留盖在新标签之上
                        homeVm.closeDetail()
                        showPlaylists = false
                        showHistory = false
                        showDownloads = false
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

    // 当前歌曲：下载与收藏都从这里取，保证两处的歌曲元数据一致
    val currentSong = remember(
        playState.currentSongId,
        playState.title,
        playState.artist,
        playState.album,
        playState.durationMs,
        playState.artworkUrl,
    ) {
        val id = playState.currentSongId
        val title = playState.title.orEmpty()
        if (id == null || title.isBlank()) {
            null
        } else {
            Song(
                id = id,
                name = title,
                artists = playState.artist?.split(" / ")?.filter { it.isNotBlank() }.orEmpty(),
                album = playState.album,
                durationMs = playState.durationMs,
                coverUrl = playState.artworkUrl,
                providerId = "netease",
            )
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
            currentSong?.let(homeVm::toggleLike)
        },
        onSelectQueueItem = playerVm::selectQueueItem,
        onOpenStyleSwitcher = {},
        onSelectStyle = { style ->
            container.settings.setPlayerStyle(style)
            scope.launch { snackbarHostState.showSnackbar("已切换到${style.label}") }
        },
        onNotify = { text -> scope.launch { snackbarHostState.showSnackbar(text) } },
        onArtworkLoaded = { container.artworkTheme.update(it) },
        // 倍速：立即生效并写回偏好，播放服务重建后按同一值恢复
        onSetSpeed = { speed ->
            playerVm.setSpeed(speed)
            container.settings.setPlaybackSpeedPercent((speed * 100).toInt())
        },
    )

    // 当前歌曲的下载入口。只订阅这一首的状态流，下载进度的每次刷新只重绘这一个按钮，
    // 不会牵着播放页整层重组。
    val downloadAction = remember(currentSong) {
        val song = currentSong ?: return@remember null
        DownloadAction(
            state = DownloadStates.stateFlow(context, song.id),
            onClick = {
                when (val state = DownloadStates.stateFlow(context, song.id).value) {
                    DownloadState.Done -> scope.launch {
                        snackbarHostState.showSnackbar("已下载到本机，可在「我的」→「已下载」里管理")
                    }
                    is DownloadState.InProgress -> {
                        SongDownloader.pause(song.id)
                        scope.launch { snackbarHostState.showSnackbar("已暂停下载") }
                    }
                    else -> {
                        SongDownloader.start(context, container.provider, song)
                        scope.launch {
                            val text = if (
                                DownloadStates.stateFlow(context, song.id).value is
                                DownloadState.Paused
                            ) {
                                "继续下载：${song.name}"
                            } else {
                                "开始下载：${song.name}"
                            }
                            snackbarHostState.showSnackbar(text)
                        }
                    }
                }
            },
        )
    }

    val playerLayer: @Composable () -> Unit = {
        // 背景模糊档位下发到所有播放页风格；设置在播放页打开时修改也会立即生效
        CompositionLocalProvider(LocalPlayerBackgroundBlur provides backgroundBlurPref.radiusDp.dp) {
            // 播放页整层：从底部滑入 / 向下滑出，与页面内的下滑收起手势同向。
            // 只做位移、不做整层淡入：播放层自带不透明背景，滑动时像一张完整的
            // 页面被推上来，不会出现两层互相透叠的发灰中间态。
            AnimatedVisibility(
                visible = showPlayer,
                enter = slideInVertically(
                    animationSpec = tween(420, easing = FastOutSlowInEasing),
                    initialOffsetY = { height -> height },
                ),
                exit = slideOutVertically(
                    animationSpec = tween(300, easing = FastOutLinearInEasing),
                    targetOffsetY = { height -> height },
                ),
                label = "playerLayer",
            ) {
                // 播放页只订阅结构态：位置由进度条逐帧读取。
                // 若这里直接收完整状态，250ms 的位置刷新会让 9 种风格整页重组，玻璃表面也要跟着重算。
                val playerState by remember(playerVm) {
                    playerVm.state
                        .map { it.copy(positionMs = 0L) }
                        .distinctUntilChanged()
                }.collectAsStateWithLifecycle(initialValue = PlaybackUiState())
                // 不透明底色：播放页 ↔ 歌词页交叉淡化时不会透出下面的主界面
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    // 播放页 ⇄ 歌词页：歌词页整层从底部滑上来盖住播放页。
                    // 不做两层互相淡出：两层同时半透明会透出底下的浅色 surface，
                    // 中途整页发白、播放页缩放还会在四周露出一圈白边。
                    // 播放页始终不透明留在底层，歌词页向上滑入，视觉上就是网易云/Apple Music 的歌词层。
                    val lyricFraction by animateFloatAsState(
                        targetValue = if (showLyric) 1f else 0f,
                        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
                        label = "lyricSlide",
                    )
                    Box(modifier = Modifier.fillMaxSize()) {
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
                            download = downloadAction,
                        )
                    }
                    // 歌词页：首帧淡入；关闭动画结束后才卸载，避免歌词列表状态被反复重建
                    if (showLyric || lyricFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val height = size.height
                                    translationY = (1f - lyricFraction) * height
                                },
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
                                isPlaying = playState.isPlaying,
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
            onPlayNext = playerVm::playQueueItemNext,
            onMove = playerVm::moveQueueItem,
            onCycleMode = playerVm::cyclePlayMode,
            sleepTimerLabel = sleepTimerLabel,
            onSleepTimer = playerVm::setSleepTimer,
        )
    }

    AnimatedVisibility(visible = showLogin, enter = OverlayEnter, exit = OverlayExit) {
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

    AnimatedVisibility(visible = showSettings, enter = OverlayEnter, exit = OverlayExit) {
        // 系统返回键回到上一页，而不是直接退出应用
        BackHandler { showSettings = false }
        SettingsScreen(
            showTranslation = showTranslationPref,
            showRomaji = showRomajiPref,
            lyricStyle = lyricStylePref,
            playerStyle = playerStylePref,
            fallbackBaseUrl = fallbackUrl,
            freeSourceEnabled = freeSourceEnabled,
            smartTransition = smartTransition,
            audioFxEnabled = audioFxEnabled,
            audioFxBands = audioFxBands,
            customAudioFx = customAudioFx,
            audioQuality = audioQuality,
            lyricCacheCount = container.lyricDiskCache.size(),
            backupLikedCount = likedSongIds.size,
            backupHistoryCount = history.size,
            onExportBackup = { exportBackupLauncher.launch(backupFileName()) },
            onImportBackup = { importBackupLauncher.launch(arrayOf("*/*")) },
            // 进设置页时算一次即可；清空后设置页会关闭，重开时会重新计算
            songCacheBytes = remember { SongDiskCache.sizeBytes(context) },
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
            onSmartTransition = container.settings::setSmartTransition,
            onAudioFxEnabled = { enabled ->
                container.settings.setAudioFxEnabled(enabled)
                playerVm.setAudioFx(enabled, audioFxBands)
            },
            onAudioFxBand = { index, db ->
                container.settings.setAudioFxBand(index, db)
                playerVm.setAudioFx(
                    enabled = audioFxEnabled,
                    bands = audioFxBands.toMutableList().apply { this[index] = db },
                )
            },
            onAudioFxPreset = { bands ->
                bands.forEachIndexed { index, db -> container.settings.setAudioFxBand(index, db) }
                playerVm.setAudioFx(enabled = audioFxEnabled, bands = bands)
            },
            onSaveCustomAudioFx = {
                container.settings.saveCustomAudioFx()
                scope.launch { snackbarHostState.showSnackbar("已存为预设") }
            },
            onClearCustomAudioFx = container.settings::clearCustomAudioFx,
            onAudioQuality = { quality ->
                container.settings.setAudioQuality(quality)
                // 已缓存的音频是按旧档位取的：清掉后下次切歌按新档位重新拉流
                sharedSongStreamCache.clear()
                scope.launch {
                    snackbarHostState.showSnackbar("音质已切换为「${quality.label}」，下次播放生效")
                }
            },
            onClearLyricCache = {
                container.lyricDiskCache.clear()
                showSettings = false
            },
            onClearSongCache = {
                // 删除最多 256MB 的缓存文件，放到 IO 线程避免设置页卡一下
                scope.launch {
                    withContext(Dispatchers.IO) { SongDiskCache.clear(context) }
                    showSettings = false
                }
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
    AnimatedVisibility(visible = showGlassDemo, enter = OverlayEnter, exit = OverlayExit) {
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
        NetworkBanner(
            text = networkBanner,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp),
        )
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
 * 断网胶囊：常驻在状态栏下方，不遮挡播放条与底部导航。
 * 只做淡入淡出，不做位移，避免与页面上的其它动效打架。
 */
@Composable
private fun NetworkBanner(text: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(180)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f))
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = MusicIcons.WifiOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = text.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
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
            // 列表覆盖页以可读性优先：遮罩比标签页更重，背景只留氛围不抢正文
            scrim = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
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

/**
 * 覆盖页统一的进出场：整页从下往上托起一小段，退出更快地落回。
 * 不做整层淡入，避免与下层标签页互相透叠；内容区另有 Entrance 逐块入场，
 * 整页先到位、内容再依次浮现，层次更干净。
 */
private val OverlayEnter: EnterTransition =
    slideInVertically(
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        initialOffsetY = { height -> height / 12 },
    )

private val OverlayExit: ExitTransition =
    slideOutVertically(
        animationSpec = tween(durationMillis = 280, easing = FastOutLinearInEasing),
        targetOffsetY = { height -> height / 12 },
    )
