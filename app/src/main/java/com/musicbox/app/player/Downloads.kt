package com.musicbox.app.player

import android.content.Context
import android.net.Uri
import com.google.gson.reflect.TypeToken
import com.musicbox.app.data.JsonFileStore
import com.musicbox.app.model.Song
import com.musicbox.app.source.MusicProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** 一条下载记录：歌曲元数据 + 本地文件体积 + 保存时间。 */
data class DownloadedSong(
    val song: Song,
    val sizeBytes: Long,
    val savedAtMs: Long,
)

/**
 * 单曲下载状态，供播放页按钮与「已下载」列表展示。
 * [InProgress.progress] 为 0..1；拿不到 Content-Length 时为负数（不确定进度）。
 * [Paused] 表示用户手动暂停：半截 `.part` 留在磁盘上，继续时用 Range 续传。
 */
sealed interface DownloadState {
    data class InProgress(val progress: Float) : DownloadState
    data class Paused(val progress: Float) : DownloadState
    data object Done : DownloadState
    data class Failed(val message: String) : DownloadState
}

/** 「已下载」页里正在下载 / 暂停 / 失败的条目。 */
data class DownloadTask(val song: Song, val state: DownloadState)

/**
 * 未完成下载的进程级清单（不含已下载完成的歌，那些走 [DownloadStore]）。
 * 列表页只订阅这一份，下载进度变化不会牵动歌曲列表本身。
 */
object DownloadTasks {

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    fun set(task: DownloadTask) {
        _tasks.value = _tasks.value.filterNot { it.song.id == task.song.id } + task
    }

    fun remove(songId: Long) {
        _tasks.value = _tasks.value.filterNot { it.song.id == songId }
    }

    fun clear() {
        _tasks.value = emptyList()
    }
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

    private val _songs = MutableStateFlow(loadIndex())
    val songs: StateFlow<List<DownloadedSong>> = _songs.asStateFlow()

    /**
     * 读索引并对一次账：索引里可能有被系统清理掉的残留条目；
     * 另外下载途中被杀进程会留下 `*.part`，这里一并清掉，不让半截文件长期占空间。
     */
    private fun loadIndex(): List<DownloadedSong> {
        sweepPartFiles()
        val raw = indexStore.read().orEmpty()
        val kept = raw
            .filter { fileFor(it.song.id).length() > 0 }
            .sortedByDescending { it.savedAtMs }
        if (kept.size != raw.size) indexStore.write(kept)
        return kept
    }

    private fun sweepPartFiles() {
        dir.listFiles { file -> file.name.endsWith(PART_SUFFIX) }?.forEach { file ->
            runCatching { file.delete() }
        }
    }

    fun fileFor(songId: Long): File = File(dir, "$songId.audio")

    /** 下载中的临时文件；下载失败或取消后由调用方清理。 */
    fun partFile(songId: Long): File = File(dir, "$songId.audio$PART_SUFFIX")

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
        _songs.value.forEach {
            runCatching { fileFor(it.song.id).delete() }
            runCatching { partFile(it.song.id).delete() }
        }
        sweepPartFiles()
        _songs.value = emptyList()
        indexStore.write(emptyList())
    }

    fun totalBytes(): Long = _songs.value.sumOf { it.sizeBytes }

    companion object {
        private const val INDEX_NAME = "index.json"
        internal const val PART_SUFFIX = ".part"

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

    /** 缓存上限：听歌量再大，也只需要最近几十首的状态。 */
    private const val MAX_CACHED = 64

    // 按插入顺序淘汰：先丢早已结束（值为 null）的条目，不碰正在下载的那些
    private val flows = LinkedHashMap<Long, MutableStateFlow<DownloadState?>>()
    private val lock = Any()

    private fun flowOf(songId: Long): MutableStateFlow<DownloadState?> = synchronized(lock) {
        val flow = flows.getOrPut(songId) { MutableStateFlow(null) }
        if (flows.size > MAX_CACHED) trim(songId)
        flow
    }

    /** 超出上限时从最老的开始丢空闲条目；[keep] 是刚被请求的那首，永远保留。 */
    private fun trim(keep: Long) {
        val iterator = flows.entries.iterator()
        while (flows.size > MAX_CACHED && iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key != keep && entry.value.value == null) iterator.remove()
        }
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

    /** 清空全部下载后把内存里的状态一起复位，否则播放页还会显示「已下载」。 */
    fun clearAll() {
        synchronized(lock) { flows.values.forEach { it.value = null } }
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

    // 真正能中断阻塞 IO 的是 OkHttp 的 Call：只 cancel Job 的话，读文件的循环会一直跑到结束
    private val calls = mutableMapOf<Long, Call>()

    private val lock = Any()

    // 最近一次进度，暂停时用它把「暂停在 37%」如实展示出来
    private val lastProgress = mutableMapOf<Long, Float>()

    // 「下载全部」会一次提交整张歌单：限制并发，避免同时开出几十上百条连接
    private val gate = Semaphore(3)

    /**
     * 开始（或继续）下载。[song] 只用来写索引，音频地址在下载时现取。
     * 磁盘上已有 `.part` 时自动用 Range 续传，所以「暂停 → 继续」「失败 → 重试」是同一个入口。
     */
    fun start(context: Context, provider: MusicProvider, song: Song) {
        val appContext = context.applicationContext
        val store = Downloads.get(appContext)
        if (store.isDownloaded(song.id)) return
        synchronized(lock) {
            if (jobs[song.id]?.isActive == true) return
            val resumed = store.partFile(song.id).length() > 0
            val initial = if (resumed) {
                (lastProgress[song.id] ?: 0f).coerceIn(0f, 0.99f)
            } else {
                0f
            }
            report(song, DownloadState.InProgress(initial))
            jobs[song.id] = scope.launch {
                val result = runCatching {
                    gate.withPermit { fetchWithRetry(appContext, provider, song) }
                }
                val error = result.exceptionOrNull()
                when {
                    result.isSuccess -> {
                        lastProgress.remove(song.id)
                        DownloadStates.set(song.id, DownloadState.Done)
                        DownloadTasks.remove(song.id)
                    }
                    // 用户暂停 / 取消：对应方法已经写好状态，这里不要再盖成「失败」
                    error is CancellationException -> Unit
                    else -> {
                        // 失败保留 .part：下次重试可以续传，不必从头再来
                        val message = humanMessage(error)
                        DownloadStates.set(song.id, DownloadState.Failed(message))
                        DownloadTasks.set(DownloadTask(song, DownloadState.Failed(message)))
                    }
                }
                synchronized(lock) {
                    jobs.remove(song.id)
                    calls.remove(song.id)
                }
            }
        }
    }

    /** 暂停：停掉 IO 但保留 `.part`，继续时从断点续传。 */
    fun pause(songId: Long) {
        val progress: Float
        synchronized(lock) {
            progress = lastProgress[songId] ?: 0f
            jobs.remove(songId)?.cancel()
            calls.remove(songId)?.cancel()
        }
        val task = DownloadTasks.tasks.value.firstOrNull { it.song.id == songId } ?: return
        DownloadStates.set(songId, DownloadState.Paused(progress))
        DownloadTasks.set(task.copy(state = DownloadState.Paused(progress)))
    }

    fun cancel(songId: Long) {
        synchronized(lock) {
            jobs.remove(songId)?.cancel()
            calls.remove(songId)?.cancel()
            lastProgress.remove(songId)
        }
        // 取消后回到「未下载」：半截文件不留在磁盘上，下次点下载是干净的一次
        Downloads.peek()?.partFile(songId)?.let { runCatching { it.delete() } }
        DownloadStates.set(songId, null)
        DownloadTasks.remove(songId)
    }

    /** 删除下载后同步状态，避免残留「已下载」或「失败」的展示。 */
    fun clear(songId: Long) {
        Downloads.peek()?.partFile(songId)?.let { runCatching { it.delete() } }
        DownloadStates.set(songId, null)
        DownloadTasks.remove(songId)
    }

    /** 清空全部下载：先停掉进行中的任务，再复位状态，避免「文件没了但按钮还显示已下载」。 */
    fun clearAll() {
        synchronized(lock) {
            jobs.values.forEach { it.cancel() }
            jobs.clear()
            calls.values.forEach { it.cancel() }
            calls.clear()
            lastProgress.clear()
        }
        DownloadStates.clearAll()
        DownloadTasks.clear()
    }

    private fun set(songId: Long, state: DownloadState) {
        DownloadStates.set(songId, state)
    }

    /** 同时更新「播放页按钮」与「已下载页任务行」两份状态。 */
    private fun report(song: Song, state: DownloadState) {
        set(song.id, state)
        DownloadTasks.set(DownloadTask(song, state))
    }

    /**
     * 失败重试：网络抖动导致的中断不该让用户再手点一次。
     * 退避 0.7s → 1.4s，共 2 次重试；被取消（isActive=false）时立刻停下，不做无意义的重试。
     */
    private suspend fun fetchWithRetry(context: Context, provider: MusicProvider, song: Song) {
        var backoff = 700L
        var lastError: Throwable? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                fetch(context, provider, song)
                return
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                coroutineContext.ensureActive()
                lastError = error
                if (attempt < MAX_ATTEMPTS - 1) {
                    delay(backoff)
                    backoff *= 2
                }
            }
        }
        throw lastError ?: IOException("下载失败")
    }

    /**
     * 取流地址 → 边下边写临时文件 → 成功后 rename 成正式文件并写索引。
     * 用 .part 临时文件：中途失败不会留下一个「看起来已下载」的半截文件；
     * 已有 .part 时用 `Range: bytes=N-` 续传，服务器不支持（返回 200）则从头写。
     */
    private suspend fun fetch(context: Context, provider: MusicProvider, song: Song) {
        val info = provider.getStreamUrl(song.id).getOrElse {
            throw IOException(it.message ?: "取流失败")
        }
        val store = Downloads.get(context)
        val target = store.fileFor(song.id)
        target.parentFile?.mkdirs()
        val tmp = store.partFile(song.id)
        val existing = tmp.length().takeIf { it > 0 } ?: 0L

        val request = Request.Builder()
            .url(info.url)
            .apply { if (existing > 0) header("Range", "bytes=$existing-") }
            .build()
        val call = client.newCall(request)
        synchronized(lock) { calls[song.id] = call }
        try {
            call.execute().use { response ->
                // 416：断点比服务端文件还长（文件变了），删掉半截文件让下一轮从头下载
                if (response.code == 416) {
                    runCatching { tmp.delete() }
                    throw IOException("续传失败")
                }
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                // 只有 206 才算真的续上了；返回 200 说明服务端不支持 Range，从头写
                val append = existing > 0 && response.code == 206
                if (!append) runCatching { tmp.delete() }

                val start = if (append) existing else 0L
                // OkHttp 5 起 Response.body 非空，不需要再判空
                val length = response.body.contentLength()
                val total = if (length > 0) start + length else -1L
                var written = start
                var lastShown = -1

                response.body.byteStream().use { input ->
                    java.io.FileOutputStream(tmp, append).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            written += count
                            val progress = if (total > 0) written.toFloat() / total else -1f
                            synchronized(lock) {
                                lastProgress[song.id] = progress.coerceIn(0f, 0.99f)
                            }
                            // 每 2% 才刷一次列表：进度太密会把整页重组拖慢
                            val step =
                                if (progress >= 0f) (progress * 50).toInt() else (written / 262144).toInt()
                            if (step != lastShown) {
                                lastShown = step
                                set(song.id, DownloadState.InProgress(progress))
                                DownloadTasks.set(
                                    DownloadTask(song, DownloadState.InProgress(progress)),
                                )
                            }
                        }
                        output.flush()
                    }
                }
            }
        } finally {
            synchronized(lock) { calls.remove(song.id, call) }
        }

        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        store.add(song, target.length())
    }

    /** 异常 → 一句人话。原始 message 可能是 "HTTP 503" 这类英文，不该直接怼给用户。 */
    private fun humanMessage(error: Throwable?): String {
        val raw = error?.message.orEmpty()
        return when {
            error is UnknownHostException -> "网络不可用，请稍后重试"
            error is SocketTimeoutException -> "连接超时，请稍后重试"
            raw.contains("ENOSPC", ignoreCase = true) -> "存储空间不足，清理后再试"
            raw.startsWith("HTTP") -> "服务器暂时不可用（$raw）"
            else -> "下载失败，请稍后重试"
        }
    }

    /** 单次下载最多尝试 3 次（首次 + 2 次重试）。 */
    private const val MAX_ATTEMPTS = 3
}
