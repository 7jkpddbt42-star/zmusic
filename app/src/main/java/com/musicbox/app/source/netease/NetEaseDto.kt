package com.musicbox.app.source.netease

import com.musicbox.app.model.Song
import com.google.gson.annotations.SerializedName

/** /eapi/cloudsearch/pc 的响应结构。字段名以真实响应为准（见计划 §1.5）。 */
internal data class SearchRespDto(
    @SerializedName("code") val code: Int = -1,
    @SerializedName("result") val result: SearchResultDto? = null,
)

internal data class SearchResultDto(
    @SerializedName("songCount") val songCount: Int = 0,
    @SerializedName("songs") val songs: List<SongDto>? = null,
)

internal data class SongDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("name") val name: String? = null,
    @SerializedName("ar") val artists: List<ArtistDto>? = null,
    @SerializedName("al") val album: AlbumDto? = null,
    @SerializedName("dt") val durationMs: Long = 0L,
)

internal data class ArtistDto(
    @SerializedName("name") val name: String? = null,
)

internal data class AlbumDto(
    @SerializedName("name") val name: String? = null,
    @SerializedName("picUrl") val picUrl: String? = null,
)

internal fun SongDto.toSong(providerId: String): Song = Song(
    id = id,
    name = name.orEmpty(),
    artists = artists?.mapNotNull { it.name } ?: emptyList(),
    album = album?.name,
    durationMs = durationMs,
    coverUrl = album?.picUrl,
    providerId = providerId,
)
