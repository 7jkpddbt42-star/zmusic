package com.musicbox.app.player

import android.content.Context
import android.net.Uri
import com.google.gson.reflect.TypeToken
import com.musicbox.app.data.JsonFileStore
import com.musicbox.app.model.Song
import com.musicbox.app.source.MusicProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 一条下载记录：歌曲元数据 + 本地文件体积 + 保存时间。 */
data class DownloadedSong(
    val song: Song,
    val sizeBytes: Long,
    val savedAtMs: Long,
)

/**
 * 单曲下载状态，供播放页按钮与「已下载」列表展示。
 * [InProgress.progress] 为 0..1；拿不到 Content-Length 时为负数（不确定进度）。
 */
sealed interface DownloadState {
    data class InProgress(val progress: Float) : DownloadState
    data object Done : DownloadState
    data class Failed(val message: String) : DownloadState
}

/**
 * 已下载歌曲仓库。音频文件直接落在 `filesDir/downloads/<songId>.audio`，
 * 索引是同目录下的 index.json（走 [JsonFileStore] 的原子写）。
 *
 * 为什么不用 Media3 的下载缓存：这里需要「用户显式下载、显式删除」的强语义，
 * 直接管文件更直观，也避免和「边播边缓存」的 LRU 缓存重复占空间。
 */
class DownloadStore(private val dir: File) {

    private val indexStore = JsonFileStore<List<DownloadedSong>>(File(dir, INDEX_NAME), listType)

    private val _songs = MutableStateFlow(
        // 索引里可能有被系统清理掉的残留条目，读进来时就对一次账
        indexStore.read().orEmpty()
            .filter { fileFor(it.song.id).length() > 0 }
            .sortedByDescending { it.savedAtMs },
    )
    val songs: StateFlow<List<DownloadedSong>> = _songs.asStateFlow()

    fun fileFor(songId: Long): File = File(dir, "$songId.audio")

    /** 本地文件存在且非空才返回 file:// URI，供播放链路直接命中。 */
    fun localUri(songId: Long): Uri? =
        fileFor(songId).takeIf { it.length() > 0 }?.let { Uri.fromFile(it) }

    fun isDownloaded(songId: Long): Boolean = fileFor(songId).length() > 0

    fun add(song: Song, sizeBytes: Long, savedAtMs: Long = System.currentTimeMillis()) {
        val next = listOf(DownloadedSong(song, sizeBytes, savedAtMs)) +
            _songs.value.filterNot { it.song.id == song.id }
        _songs.value = next
        indexStore.write(next)
    }

    fun remove(songId: Long) {
        runCatching { fileFor(songId).delete() }
        val next = _songs.value.filterNot { it.song.id == songId }
        _songs.value = next
        indexStore.write(next)
    }

    fun clearAll() {
        _songs.value.forEach { runCatching { fileFor(it.song.id).delete() } }
        _songs.value = emptyList()
        indexStore.write(emptyList())
    }

    fun totalBytes(): Long = _songs.value.sumOf { it.sizeBytes }

    companion object {
        private const val INDEX_NAME = "index.json"

        val listType: java.lang.reflect.Type =
            object : TypeToken<List<DownloadedSong>>() {}.type
    }
}

/** 进程级单例：播放服务与界面各自 new 容器，但下载目录与索引必须是同一份。 */
object Downloads {

    private const val DIR_NAME = "downloads"

    private val lock = Any()
    private var store: DownloadStore? = null

    fun get(context: Context): DownloadStore = synchronized(lock) {
        store ?: DownloadStore(File(context.applicationContext.filesDir, DIR_NAME))
            .also { store = it }
    }

    /** 已初始化的仓库；尚未初始化时返回 null（容器构造时会初始化）。 */
    fun peek(): DownloadStore? = synchronized(lock) { store }

    /** 不依赖 Context 的查询入口，ViewModel 这类没上下文的层用它。 */
    fun isDownloadedNow(songId: Long): Boolean = peek()?.isDownloaded(songId) == true
}

/**
 * 单曲下载状态的对外视图：内存里的进度 + 磁盘上「已下载」的并集。
 *
 * 每首歌一个 StateFlow，并且是进程级缓存：播放页只订阅当前这一首，
 * 进度变化不会连同整页一起重组。
 */
object DownloadStates {

    private val flows = mutableMapOf<Long, MutableStateFlow<DownloadState?>>()
    private val lock = Any()

    private fun flowOf(songId: Long): MutableStateFlow<DownloadState?> = synchronized(lock) {
        flows.getOrPut(songId) { MutableStateFlow(null) }
    }

    /** 订阅某首歌的状态；磁盘上已有文件时初始值就是 [DownloadState.Done]。 */
    fun stateFlow(context: Context, songId: Long): StateFlow<DownloadState?> {
        val flow = flowOf(songId)
        if (flow.value == null && Downloads.get(context).isDownloaded(songId)) {
            flow.value = DownloadState.Done
        }
        return flow
    }

    fun set(songId: Long, state: DownloadState?) {
        flowOf(songId).value = state
    }
}

/**
 * 下载执行器。整个下载过程在 IO 协程里跑，进度写进 [states]，界面只订阅状态。
 *
 * 放在进程级 object 里而不是 ViewModel / 容器：旋转屏幕、退出播放页都不该打断下载，
 * 只要进程还活着就继续下载完。
 */
object SongDownloader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jobs = mutableMapOf<Long, Job>()
    private val lock = Any()

    /** 开始下载。[song] 只用来写索引，音频地址在下载时现取。 */
    fun start(context: Context, provider: MusicProvider, song: Song) {
        val appContext = context.applicationContext
        val store = Downloads.get(appContext)
        if (store.isDownloaded(song.id)) return
        synchronized(lock) {
            if (jobs[song.id]?.isActive == true) return
            DownloadStates.set(song.id, DownloadState.InProgress(0f))
            jobs[song.id] = scope.launch {
                val result = runCatching { fetch(appContext, provider, song) }
                if (result.isSuccess) {
                    DownloadStates.set(song.id, DownloadState.Done)
                } else {
                    val message = result.exceptionOrNull()?.message ?: "下载失败"
                    DownloadStates.set(song.id, DownloadState.Failed(message))
                }
                synchronized(lock) { jobs.remove(song.id) }
            }
        }
    }

    fun cancel(songId: Long) {
        synchronized(lock) {
            jobs.remove(songId)?.cancel()
        }
        // 取消后回到「未下载」：临时文件不会被当成正式文件
        DownloadStates.set(songId, null)
    }

    /** 删除下载后同步状态，避免残留「已下载」或「失败」的展示。 */
    fun clear(songId: Long) {
        DownloadStates.set(songId, null)
    }

    private fun set(songId: Long, state: DownloadState) {
        DownloadStates.set(songId, state)
    }

    /**
     * 取流地址 → 边下边写临时文件 → 成功后 rename 成正式文件并写索引。
     * 用 .part 临时文件：中途失败不会留下一个「看起来已下载」的半截文件。
     */
    private suspend fun fetch(context: Context, provider: MusicProvider, song: Song) {
        val info = provider.getStreamUrl(song.id).getOrElse {
            throw IOException(it.message ?: "取流失败")
        }
        val target = Downloads.get(context).fileFor(song.id)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.part")

        client.newCall(Request.Builder().url(info.url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("响应为空")
            val total = body.contentLength()

            body.byteStream().use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        read += count
                        if (total > 0) {
                            set(song.id, DownloadState.InProgress(read.toFloat() / total))
                        }
                    }
                    output.flush()
                }
            }
        }

        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        Downloads.get(context).add(song, target.length())
    }
}
