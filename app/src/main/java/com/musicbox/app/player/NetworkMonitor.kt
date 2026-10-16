package com.musicbox.app.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 在线状态监测（设计文档 §9：网络中断 → 缓冲指示 + 自动重连提示）。
 *
 * 判定标准：存在默认网络且具备 [NetworkCapabilities.NET_CAPABILITY_INTERNET]。
 *
 * 有意**不**要求 [NetworkCapabilities.NET_CAPABILITY_VALIDATED]：该标志由系统的连通性
 * 探测服务（默认探测 google 的 generate_204）决定，在国内网络 / 模拟器上探测经常失败，
 * 网络会被标记为 PARTIAL_CONNECTIVITY 而拿不到 VALIDATED。此时网易云等国内接口其实可用，
 * 若以 VALIDATED 判定会误报"网络已断开"。因此这里只判断"有没有可用的默认网络"。
 */
class NetworkMonitor(context: Context) {

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun isOnline(): Boolean = isUsable(cm.activeNetwork?.let { cm.getNetworkCapabilities(it) })

    private fun isUsable(caps: NetworkCapabilities?): Boolean =
        caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

    /** 在线状态变化流（首帧即当前值）。 */
    fun observe(): Flow<Boolean> = callbackFlow {
        trySend(isOnline())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(isOnline()) }
            override fun onLost(network: Network) { trySend(isOnline()) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(isUsable(caps))
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
