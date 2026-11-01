package com.musicbox.app.data

import android.content.Context
import com.google.gson.reflect.TypeToken
import com.musicbox.app.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * 本机「喜欢」仓库。
 *
 * 播放页心形、通知栏喜欢按钮、备份导出三处共用这一份数据：
 * - 登录后由首页把「我喜欢的音乐」曲目同步进来（[mergeRemote]）；
 * - 未登录也能本地收藏，不依赖账号；
 * - 只存歌曲元数据（[Song]），换机恢复后仍能看出喜欢的是哪首歌。
 */
class LikedStore(private val store: JsonFileStore<List<Song>>) {

    private val _songs = MutableStateFlow(store.read().orEmpty())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _ids = MutableStateFlow(idSetOf(_songs.value))
    val ids: StateFlow<Set<Long>> = _ids.asStateFlow()

    fun isLiked(songId: Long): Boolean = songId in _ids.value

    /** 切换喜欢状态，返回切换后的值。 */
    fun toggle(song: Song): Boolean {
        val nextLiked = song.id !in _ids.value
        if (nextLiked) add(song) else remove(song.id)
        return nextLiked
    }

    fun add(song: Song) {
        if (song.id in _ids.value) return
        write(listOf(song) + _songs.value)
    }

    fun remove(songId: Long) {
        if (songId !in _ids.value) return
        write(_songs.value.filterNot { it.id == songId })
    }

    /**
     * 用账号「我喜欢的音乐」同步本地列表。
     * 本地独有的条目保留：游客态收藏的歌不会因为一次登录就消失。
     */
    fun mergeRemote(remote: List<Song>) {
        val remoteIds = remote.mapTo(mutableSetOf()) { it.id }
        val localOnly = _songs.value.filterNot { it.id in remoteIds }
        write(remote + localOnly)
    }

    /** 恢复备份：按 id 合并，已有条目以本地为准。 */
    fun mergeBackup(entries: List<Song>) {
        val existing = _ids.value
        val added = entries.filterNot { it.id in existing }
        if (added.isNotEmpty()) write(_songs.value + added)
    }

    fun clear() = write(emptyList())

    private fun write(songs: List<Song>) {
        _songs.value = songs
        _ids.value = idSetOf(songs)
        store.write(songs)
    }

    private fun idSetOf(songs: List<Song>): Set<Long> = songs.mapTo(mutableSetOf()) { it.id }

    companion object {
        val listType: java.lang.reflect.Type =
            object : TypeToken<List<Song>>() {}.type

        fun forDir(dir: File): LikedStore =
            LikedStore(JsonFileStore(File(dir, "liked_songs.json"), listType))
    }
}

/** 进程级单例：界面与播放服务各自建容器，但喜欢列表必须是同一份。 */
object Liked {
    private val lock = Any()
    private var store: LikedStore? = null

    fun get(context: Context): LikedStore = synchronized(lock) {
        store ?: LikedStore.forDir(File(context.applicationContext.filesDir, "data"))
            .also { store = it }
    }

    /** 已初始化的仓库；尚未初始化时返回 null。 */
    fun peek(): LikedStore? = synchronized(lock) { store }

    /** 不依赖 Context 的查询入口，播放服务这类可能早于容器初始化的地方用它。 */
    fun isLikedNow(songId: Long): Boolean = peek()?.isLiked(songId) == true
}
