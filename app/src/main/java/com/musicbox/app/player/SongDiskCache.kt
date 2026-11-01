package com.musicbox.app.player

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * 最近播放音频的离线缓存（Media3 SimpleCache，256MB LRU 淘汰）。
 *
 * 为什么做成进程内单例：SimpleCache 对同一个目录只允许一个实例，重复创建会报
 * "Cache is already locked"；而 MediaSessionService 会被系统反复销毁重建，
 * 实例必须跨服务生命周期复用，销毁时再显式释放。
 */
internal object SongDiskCache {

    /** 256MB：按 LRU 淘汰，大约能留下百来首 128kbps 的整曲。 */
    const val MAX_BYTES: Long = 256L * 1024 * 1024

    private const val DIR_NAME = "song_audio"

    private val lock = Any()
    private var instance: SimpleCache? = null

    /** 取缓存实例；创建失败（目录损坏 / 磁盘满）返回 null，调用方退化为纯在线播放。 */
    fun get(context: Context): SimpleCache? = synchronized(lock) {
        instance ?: runCatching {
            SimpleCache(
                dirFor(context),
                LeastRecentlyUsedCacheEvictor(MAX_BYTES),
                StandaloneDatabaseProvider(context.applicationContext),
            )
        }.getOrNull()?.also { instance = it }
    }

    /** 服务销毁时释放：数据库句柄不关，下次创建就拿不到同一份缓存了。 */
    fun release() = synchronized(lock) { releaseLocked() }

    /** 已占用字节数，供设置页展示。缓存分片在子目录里，必须递归统计。 */
    fun sizeBytes(context: Context): Long =
        dirFor(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /**
     * 清空缓存。
     *
     * 实例已打开时只能「逐条摘除资源」，不能 release 再删目录：SimpleCache 释放后
     * 任何读写都会抛 IllegalStateException（不是 CacheException，缓存忽略兜底也接不住），
     * 正在播放的 CacheDataSource 下一首歌就会直接播放失败。摘除资源则只是让后续读取
     * 重新落回网络。实例没打开（服务未运行）时才真正删目录。
     */
    fun clear(context: Context): Boolean = synchronized(lock) {
        val open = instance
        if (open != null) {
            open.keys.toList().forEach { key -> runCatching { open.removeResource(key) } }
            true
        } else {
            runCatching {
                SimpleCache.delete(
                    dirFor(context),
                    StandaloneDatabaseProvider(context.applicationContext),
                )
            }.isSuccess
        }
    }

    private fun releaseLocked() {
        runCatching { instance?.release() }
        instance = null
    }

    private fun dirFor(context: Context): File =
        File(context.applicationContext.filesDir, DIR_NAME)
}
