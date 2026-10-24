package com.musicbox.app.model

/**
 * 歌单摘要（网易云个人歌单 / 我喜欢的音乐）。
 * [specialType] 为 5 表示网易云的「我喜欢的音乐」歌单。
 */
data class PlaylistSummary(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val trackCount: Int,
    val specialType: Int = 0,
) {
    val isLiked: Boolean get() = specialType == 5
}
