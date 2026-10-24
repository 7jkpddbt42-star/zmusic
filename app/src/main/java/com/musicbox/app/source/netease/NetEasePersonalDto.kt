package com.musicbox.app.source.netease

import com.google.gson.annotations.SerializedName
import com.musicbox.app.model.PlaylistSummary
import com.musicbox.app.model.Song

/** /eapi/v3/discovery/recommend/songs 的响应。 */
internal data class DailyRecommendRespDto(
    @SerializedName("code") val code: Int = -1,
    @SerializedName("data") val data: DailyRecommendDataDto? = null,
)

internal data class DailyRecommendDataDto(
    @SerializedName("dailySongs") val dailySongs: List<SongDto>? = null,
)

/** /eapi/radio/get 的响应。私人 FM 每次返回一首。 */
internal data class RadioRespDto(
    @SerializedName("code") val code: Int = -1,
    @SerializedName("data") val data: List<RadioSongDto>? = null,
)

internal data class RadioSongDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("name") val name: String? = null,
    @SerializedName("artists") val artists: List<RadioArtistDto>? = null,
    @SerializedName("album") val album: RadioAlbumDto? = null,
    @SerializedName("duration") val durationMs: Long = 0L,
)

internal data class RadioArtistDto(
    @SerializedName("name") val name: String? = null,
)

internal data class RadioAlbumDto(
    @SerializedName("name") val name: String? = null,
    @SerializedName("picUrl") val picUrl: String? = null,
)

internal fun RadioSongDto.toSong(providerId: String): Song = Song(
    id = id,
    name = name.orEmpty(),
    artists = artists?.mapNotNull { it.name } ?: emptyList(),
    album = album?.name,
    durationMs = durationMs,
    coverUrl = album?.picUrl,
    providerId = providerId,
)

/** /eapi/user/playlist 的响应。 */
internal data class UserPlaylistRespDto(
    @SerializedName("code") val code: Int = -1,
    @SerializedName("playlist") val playlist: List<PlaylistDto>? = null,
)

internal data class PlaylistDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("name") val name: String? = null,
    @SerializedName("coverImgUrl") val coverUrl: String? = null,
    @SerializedName("trackCount") val trackCount: Int = 0,
    @SerializedName("specialType") val specialType: Int = 0,
)

internal fun PlaylistDto.toSummary(): PlaylistSummary = PlaylistSummary(
    id = id,
    name = name.orEmpty(),
    coverUrl = coverUrl,
    trackCount = trackCount,
    specialType = specialType,
)

/** /eapi/v6/playlist/detail 的响应。 */
internal data class PlaylistDetailRespDto(
    @SerializedName("code") val code: Int = -1,
    @SerializedName("playlist") val playlist: PlaylistDetailDto? = null,
)

internal data class PlaylistDetailDto(
    @SerializedName("name") val name: String? = null,
    @SerializedName("coverImgUrl") val coverUrl: String? = null,
    @SerializedName("trackCount") val trackCount: Int = 0,
    @SerializedName("tracks") val tracks: List<SongDto>? = null,
)
