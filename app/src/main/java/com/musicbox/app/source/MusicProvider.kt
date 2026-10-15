package com.musicbox.app.source

import com.musicbox.app.model.LyricData
import com.musicbox.app.model.SearchType
import com.musicbox.app.model.Song
import com.musicbox.app.model.StreamInfo

/**
 * 音源统一抽象（设计文档 §4.3）。每个平台一个实现，音源之间互不依赖。
 * 所有方法返回 Result<T>，失败时异常类型固定为 [SourceError]。
 */
interface MusicProvider {

    val id: String

    /** 搜索。type 指定歌曲/专辑/歌手，page 从 1 开始。 */
    suspend fun search(keyword: String, type: SearchType, page: Int): Result<List<Song>>

    /** 取播放地址。MI-2 实现。 */
    suspend fun getStreamUrl(songId: Long): Result<StreamInfo>

    /** 取歌词（逐字 YRC + 译文 + 罗马音）。MI-3 实现。 */
    suspend fun getLyric(songId: Long): Result<LyricData>
}
