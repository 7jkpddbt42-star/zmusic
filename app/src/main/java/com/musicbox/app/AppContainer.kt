package com.musicbox.app

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.Color
import com.musicbox.app.data.AppLog
import com.musicbox.app.data.BackgroundStore
import com.musicbox.app.data.FavoriteRepository
import com.musicbox.app.data.HistoryRepository
import com.musicbox.app.data.LyricDiskCache
import com.musicbox.app.data.SessionStore
import com.musicbox.app.data.SettingsRepository
import com.musicbox.app.data.SharedPrefsKeyValueStore
import com.musicbox.app.player.NetworkMonitor
import com.musicbox.app.source.FailoverProvider
import com.musicbox.app.source.MusicProvider
import com.musicbox.app.source.failover.FallbackProvider
import com.musicbox.app.source.netease.NetEaseLoginApi
import com.musicbox.app.source.netease.NetEaseHeader
import com.musicbox.app.source.netease.NetEaseProvider
import com.musicbox.app.source.netease.OkHttpTransport
import com.musicbox.app.ui.theme.ArtworkThemeState
import com.musicbox.app.ui.theme.normalizeSeed
import java.io.File

/**
 * 极简依赖装配。MI-4 之前不引入 DI 框架。
 * deviceId 持久化在私有 SharedPreferences，保证设备身份跨启动稳定。
 *
 * 注意：MainActivity 与 PlaybackService 会各自 new 一个 AppContainer。两者读同一份
 * SharedPreferences 与 filesDir，因此状态（会话、设置、缓存）天然一致。
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    private val prefs = appContext.getSharedPreferences("musicbox", Context.MODE_PRIVATE)

    private val kv = SharedPrefsKeyValueStore(prefs)

    private val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null)
            ?: NetEaseHeader.newDeviceId().also {
                prefs.edit().putString(KEY_DEVICE_ID, it).apply()
            }

    val deviceIdValue: String get() = deviceId

    val settings: SettingsRepository = SettingsRepository(kv)

    /** 播放页自选背景素材仓库：把用户选中的本机图片/视频复制到私有目录。 */
    val backgroundStore: BackgroundStore = BackgroundStore(appContext)

    val sessionStore: SessionStore = SessionStore(kv)

    /** 播放页封面取色的内存状态，供主题系统读取“封面取色”来源的 seed。 */
    val artworkTheme = ArtworkThemeState()

    /**
     * 系统壁纸代表色（Android 12+）。低版本无 [WallpaperManager.getWallpaperColors]，
     * 或取色异常时一律返回 null，调用方退回固定/封面 seed。
     */
    fun wallpaperSeed(): Color? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return try {
            val colors = WallpaperManager.getInstance(appContext)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            val primary = colors?.primaryColor ?: return null
            normalizeSeed(Color(primary.toArgb()))
        } catch (t: Throwable) {
            null
        }
    }

    val log: AppLog = AppLog.forDir(filesDir("logs"))

    val networkMonitor: NetworkMonitor = NetworkMonitor(appContext)

    val favorites: FavoriteRepository = FavoriteRepository.forDir(filesDir("data"))

    val history: HistoryRepository = HistoryRepository.forDir(filesDir("data"))

    val lyricDiskCache: LyricDiskCache = LyricDiskCache.forDir(filesDir("lyrics"))

    val loginApi: NetEaseLoginApi = NetEaseLoginApi(OkHttpTransport(), deviceId)

    private val netEaseProvider: MusicProvider = NetEaseProvider(
        transport = OkHttpTransport(),
        deviceId = deviceId,
        session = { sessionStore.session.value },
        appLog = log,
    )

    private val fallbackProvider: MusicProvider = FallbackProvider(
        transport = OkHttpTransport(),
        baseUrl = { settings.fallbackBaseUrl.value },
    )

    /** UI 侧统一用这个：主源优先，主源临时失效时自动试一次备用源。 */
    val provider: MusicProvider = FailoverProvider(netEaseProvider, fallbackProvider)

    private fun filesDir(name: String): File = File(appContext.filesDir, name)

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
    }
}
