package com.musicbox.app.player

import java.net.URLEncoder

/**
 * 队列里每首 MediaItem 的占位 URI 编解码。
 *
 * 为什么要占位而不是直接写真实地址：真实地址要么提前批量取（请求数爆炸），
 * 要么等到播放时才知道（ExoPlayer 拿不到就播不了）。用占位 URI + ResolvingDataSource，
 * 队列可以完整地待在 ExoPlayer 的 timeline 上（通知栏上下首、自动续播、随机全部原生可用），
 * 真实地址只在真正 open 那一刻才去换。见 PlaybackService。
 */
object SongUri {

    private const val SCHEME = "musicbox"
    private const val HOST = "song"

    /**
     * @param nonce 可选随机串：切换音源模式后用它构造一条新的占位 URI，
     *              强制 ExoPlayer 重建媒体源、按新模式重新取流。
     */
    fun placeholder(songId: Long, nonce: String? = null): String {
        val base = "$SCHEME://$HOST/$songId"
        return if (nonce == null) base else base + "?n=" + enc(nonce)
    }

    /** 占位 URI → songId；不是占位 URI（例如已经是 http 地址）时返回 null。 */
    fun songIdOf(uri: String): Long? {
        val prefix = "$SCHEME://$HOST/"
        if (!uri.startsWith(prefix)) return null
        return uri.removePrefix(prefix).substringBefore('?').toLongOrNull()
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
