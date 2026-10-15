package com.musicbox.app.model

/** 网易云搜索类型：单曲 / 专辑 / 歌手。数值即接口的 type 参数。 */
enum class SearchType(val apiValue: Int) {
    SONG(1),
    ALBUM(10),
    ARTIST(100),
}
