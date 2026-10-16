package com.musicbox.app.data

import com.google.gson.Gson
import com.musicbox.app.model.LyricData
import java.io.File

/**
 * 歌词磁盘缓存：按 songId 存**原始 LyricData JSON**（不是解析后的时间轴）。
 * 存原始文本的好处是：以后解析器升级（比如支持新格式）时，已缓存数据能直接受益，
 * 不必让用户重新联网拉一遍。读出来再解析，解析是纯 CPU 开销。
 */
class LyricDiskCache(
    private val dir: File,
    private val gson: Gson = Gson(),
) {

    fun get(songId: Long): LyricData? {
        val file = fileFor(songId)
        if (!file.exists()) return null
        return try {
            gson.fromJson(file.readText(), LyricData::class.java)
        } catch (e: Exception) {
            file.delete() // 损坏文件直接丢弃，下次重新拉
            null
        }
    }

    fun put(songId: Long, data: LyricData) {
        dir.mkdirs()
        val target = fileFor(songId)
        val tmp = File(dir, "$songId.json$TMP_SUFFIX")
        tmp.writeText(gson.toJson(data))
        if (target.exists() && !target.delete()) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
            return
        }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    fun size(): Int = dir.listFiles { f -> f.name.endsWith(".json") }?.size ?: 0

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun fileFor(songId: Long) = File(dir, "$songId.json")

    companion object {
        private const val TMP_SUFFIX = ".tmp"

        fun forDir(dir: File): LyricDiskCache = LyricDiskCache(dir)
    }
}
