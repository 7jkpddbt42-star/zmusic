package com.musicbox.app

import android.content.Context
import com.musicbox.app.source.MusicProvider
import com.musicbox.app.source.netease.NetEaseHeader
import com.musicbox.app.source.netease.NetEaseProvider
import com.musicbox.app.source.netease.OkHttpTransport

/**
 * 极简依赖装配。MI-4 之前不引入 DI 框架。
 * deviceId 持久化在私有 SharedPreferences，保证设备身份跨启动稳定。
 */
class AppContainer(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("musicbox", Context.MODE_PRIVATE)

    private val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null)
            ?: NetEaseHeader.newDeviceId().also {
                prefs.edit().putString(KEY_DEVICE_ID, it).apply()
            }

    val provider: MusicProvider = NetEaseProvider(
        transport = OkHttpTransport(),
        deviceId = deviceId,
    )

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
    }
}
