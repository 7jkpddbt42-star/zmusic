package com.musicbox.app.source

import com.musicbox.app.model.LyricData
import com.musicbox.app.model.SearchType
import com.musicbox.app.model.Song
import com.musicbox.app.model.StreamInfo

/**
 * 主源 + 备用源的自动降级（设计文档 §9：接口失效 → 自动切备用源重试一次）。
 *
 * 只在**可重试类错误**（网络 / 接口 code / 解析）上试备用源；
 * `Unplayable`（无版权、需登录）与 `NotConfigured` 属于"确定不可播"，不再浪费一次请求。
 * 备用源也失败时，**保留主源错误**——它更贴近真实原因。
 */
class FailoverProvider(
    private val primary: MusicProvider,
    private val fallback: MusicProvider,
) : MusicProvider {

    override val id: String = primary.id

    override suspend fun search(
        keyword: String,
        type: SearchType,
        page: Int,
    ): Result<List<Song>> = degrade({ primary.search(keyword, type, page) }) {
        fallback.search(keyword, type, page)
    }

    override suspend fun getStreamUrl(songId: Long): Result<StreamInfo> =
        degrade({ primary.getStreamUrl(songId) }) { fallback.getStreamUrl(songId) }

    override suspend fun getLyric(songId: Long): Result<LyricData> =
        degrade({ primary.getLyric(songId) }) { fallback.getLyric(songId) }

    private suspend fun <T> degrade(
        primaryCall: suspend () -> Result<T>,
        fallbackCall: suspend () -> Result<T>,
    ): Result<T> {
        val primaryResult = primaryCall()
        if (primaryResult.isSuccess) return primaryResult
        val error = primaryResult.exceptionOrNull()
        if (!isRetryable(error)) return primaryResult
        val fallbackResult = fallbackCall()
        return if (fallbackResult.isSuccess) fallbackResult else primaryResult
    }

    private fun isRetryable(error: Throwable?): Boolean = when (error) {
        is SourceError.Network, is SourceError.Api, is SourceError.Parse -> true
        else -> false
    }
}
