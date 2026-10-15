package com.musicbox.app.model

/** 统一歌曲模型，各音源都要映射到它。 */
data class Song(
    val id: Long,
    val name: String,
    val artists: List<String>,
    val album: String?,
    val durationMs: Long,
    val coverUrl: String?,
    val providerId: String,
)
