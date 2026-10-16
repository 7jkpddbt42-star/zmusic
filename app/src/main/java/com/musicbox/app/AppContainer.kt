package com.musicbox.app

import android.content.Context
import com.musicbox.app.data.AppLog
import com.musicbox.app.data.FavoriteRepository
import com.musicbox.app.data.HistoryRepository
import com.musicbox.app.data.LyricDiskCache
import com.musicbox.app.data.SessionStore
import com.musicbox.app.data.SettingsRepository
import com.musicbox.app.data.SharedPrefsKeyValueStore
import com.musicbox.app.player.NetworkMonitor
import com.musicbox.app.source.MusicProvider
import com.musicbox.app.source.netease.NetEaseLoginApi
import com.musicbox.app.source.netease.NetEaseHeader
import com.musicbox.app.source.netease.NetEaseProvider
import com.musicbox.app.source.netease.OkHttpTransport
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

    val sessionStore: SessionStore = SessionStore(kv)

    val log: AppLog = AppLog.forDir(filesDir("logs"))

    val networkMonitor: NetworkMonitor = NetworkMonitor(appContext)

    val favorites: FavoriteRepository = FavoriteRepository.forDir(filesDir("data"))

    val history: HistoryRepository = HistoryRepository.forDir(filesDir("data"))

    val lyricDiskCache: LyricDiskCache = LyricDiskCache.forDir(filesDir("lyrics"))

    val loginApi: NetEaseLoginApi = NetEaseLoginApi(OkHttpTransport(), deviceId)

    val provider: MusicProvider = NetEaseProvider(
        transport = OkHttpTransport(),
        deviceId = deviceId,
        session = { sessionStore.session.value },
        appLog = log,
    )

    private fun filesDir(name: String): File = File(appContext.filesDir, name)

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
    }
}
