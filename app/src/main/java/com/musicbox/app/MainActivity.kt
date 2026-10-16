package com.musicbox.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import com.musicbox.app.ui.MusicBoxTheme
import com.musicbox.app.ui.player.NowPlayingBar
import com.musicbox.app.ui.player.PlaybackConnection
import com.musicbox.app.ui.player.PlayerScreen
import com.musicbox.app.ui.player.PlayerViewModel
import com.musicbox.app.ui.player.QueueSheet
import com.musicbox.app.ui.search.SearchScreen
import com.musicbox.app.ui.search.SearchViewModel

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

    val searchState by searchVm.state.collectAsStateWithLifecycle()
    val playState by playerVm.state.collectAsStateWithLifecycle()
    val mode by playerVm.mode.collectAsStateWithLifecycle()
    val message by playerVm.message.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showPlayer by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    // 切歌 → 搜索列表高亮跟随
    LaunchedEffect(playState.currentSongId) {
        searchVm.onNowPlayingChanged(playState.currentSongId)
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NowPlayingBar(
                state = playState,
                onExpand = { showPlayer = true },
                onTogglePlay = playerVm::togglePlay,
            )
        },
    ) { innerPadding ->
        SearchScreen(
            state = searchState,
            onQueryChange = searchVm::onQueryChange,
            onSearch = searchVm::search,
            nowPlayingSongId = playState.currentSongId,
            onSongClick = { index -> playerVm.play(searchState.results, index) },
            modifier = Modifier.padding(innerPadding),
        )
    }

    if (showPlayer) {
        PlayerScreen(
            state = playState,
            modeLabel = mode.label,
            onCollapse = { showPlayer = false },
            onTogglePlay = playerVm::togglePlay,
            onNext = playerVm::next,
            onPrevious = playerVm::previous,
            onSeek = playerVm::seekTo,
            onCycleMode = playerVm::cyclePlayMode,
            onOpenQueue = { showQueue = true },
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
}
