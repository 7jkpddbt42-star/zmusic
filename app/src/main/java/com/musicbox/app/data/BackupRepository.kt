package com.musicbox.app.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.musicbox.app.model.Song

/**
 * 备份文件内容。只放"换机后真正还想带走"的东西：
 * 本机收藏（喜欢）与最近播放列表。两者都自带歌曲元数据，恢复后能直接再播。
 * 网易云账号里的歌单本身在云端，登录即同步，不需要也不应该塞进文件。
 */
data class BackupPayload(
    val version: Int = VERSION,
    val exportedAtMs: Long = 0L,
    val likedSongs: List<Song> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
) {
    companion object {
        const val VERSION = 1
    }
}

/** 恢复结果，用于给用户一句明确的提示。 */
data class RestoreResult(val likedCount: Int, val historyCount: Int)

/**
 * 收藏 / 播放记录导出与恢复。
 *
 * 读取时不信任外部文件：版本号、空文件、非法 JSON、缺字段都走失败分支，
 * 绝不让一个坏文件把本机数据清空（恢复只做合并，不覆盖）。
 */
class BackupRepository(
    private val liked: LikedStore,
    private val history: HistoryRepository,
) {

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    fun export(): String = gson.toJson(
        BackupPayload(
            exportedAtMs = System.currentTimeMillis(),
            likedSongs = liked.songs.value,
            history = history.history.value,
        ),
        BackupPayload::class.java,
    )

    fun import(text: String): Result<RestoreResult> = runCatching {
        val payload = gson.fromJson(text, BackupPayload::class.java)
            ?: throw IllegalArgumentException("文件为空或格式不正确")
        require(payload.version <= BackupPayload.VERSION) { "备份版本过高，请先更新应用" }

        val likedSongs = payload.likedSongs.orEmptySafe()
        val entries = payload.history.orEmptySafe()

        val likedBefore = liked.ids.value.size
        liked.mergeBackup(likedSongs)
        val historyResult = history.merge(entries)

        RestoreResult(
            likedCount = liked.ids.value.size - likedBefore,
            historyCount = historyResult,
        )
    }

    /** Gson 反序列化时缺字段会留下 null（Kotlin 非空类型也拦不住），先兜成空列表。 */
    private fun <T> List<T>?.orEmptySafe(): List<T> = this ?: emptyList()

    companion object {
        /** 解析前先做一次极轻量的格式预检，把"选错文件"和"文件损坏"区分开。 */
        fun looksLikeBackup(text: String): Boolean =
            text.trimStart().startsWith("{") && text.contains("\"version\"")
    }
}
