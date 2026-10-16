package com.musicbox.app.lyric

/**
 * 歌词进程内缓存。纯 JVM 实现（LinkedHashMap + accessOrder），不依赖 Android，可单测。
 *
 * 只存**已解析**的 LyricTimeline：解析是一次性 CPU 开销，缓存解析结果可省去来回切歌的重复解析。
 * 歌词不像取流地址那样会过期（同一首歌的歌词基本不变），因此不设 TTL，只做容量淘汰。
 * 磁盘缓存留到 MI-4。
 */
class LyricCache(private val maxEntries: Int = 20) {

    // accessOrder = true：get 把条目挪到队尾，队首即「最久未使用」
    private val entries = LinkedHashMap<Long, LyricTimeline>(16, 0.75f, true)

    fun get(songId: Long): LyricTimeline? = synchronized(entries) { entries[songId] }

    fun put(songId: Long, timeline: LyricTimeline) = synchronized(entries) {
        entries[songId] = timeline
        while (entries.size > maxEntries) {
            entries.remove(entries.keys.first())
        }
    }

    fun size(): Int = synchronized(entries) { entries.size }
}

/**
 * 进程内共享实例。UI 与（未来的）预取共用一份，避免重复请求。
 * 单测请自行 new LyricCache()，不要用这个共享实例。
 */
internal val sharedLyricCache = LyricCache()
