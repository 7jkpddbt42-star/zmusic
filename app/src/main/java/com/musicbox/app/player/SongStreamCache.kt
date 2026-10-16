package com.musicbox.app.player

import com.musicbox.app.model.StreamInfo

/**
 * 取流结果短期缓存。纯 JVM 实现（LinkedHashMap + accessOrder），不依赖 Android，
 * 因此可以在单测里跑。TTL 默认 10 分钟，比实测的 expi=1200 秒留了余量。
 */
class SongStreamCache(
    private val maxEntries: Int = 20,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private class Entry(val info: StreamInfo, val expiresAtMs: Long)

    // accessOrder = true：get 会把条目挪到队尾，队首即「最久未使用」，用于 LRU 淘汰
    private val entries = LinkedHashMap<Long, Entry>(16, 0.75f, true)

    fun get(songId: Long): StreamInfo? = synchronized(entries) {
        val entry = entries[songId] ?: return null
        if (entry.expiresAtMs <= clock()) {
            entries.remove(songId)
            return null
        }
        entry.info
    }

    fun put(songId: Long, info: StreamInfo) = synchronized(entries) {
        entries[songId] = Entry(info, clock() + ttlMs)
        while (entries.size > maxEntries) {
            val oldest = entries.keys.first()
            entries.remove(oldest)
        }
    }

    fun size(): Int = synchronized(entries) { entries.size }

    companion object {
        const val DEFAULT_TTL_MS = 10 * 60 * 1000L
    }
}

/**
 * 进程内共享实例。本项目是单进程个人自用 App，Service 与 UI 读同一份取流结果
 * 比自建跨组件通道更简单（也让「试听片段」标记无需额外协议即可被 UI 看到）。
 * 单测请自行 new SongStreamCache()，不要用这个共享实例。
 */
internal val sharedSongStreamCache = SongStreamCache()
