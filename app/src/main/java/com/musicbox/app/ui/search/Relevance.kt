package com.musicbox.app.ui.search

import com.musicbox.app.model.Song

/**
 * 查询词与搜索结果的相关性判定（纯函数，可单测）。
 *
 * 动机（MI-1 实测遗留）：网易云 cloudsearch/pc 对乱码 / 无意义关键词**不会返回空列表**，
 * 而是给一批模糊匹配结果。所以"结果非空"不等于"搜到了"，需要额外判定，
 * 否则用户搜 `asdkjhqwe` 会看到一堆不相干的歌，空状态永远触发不了。
 *
 * 判定规则：把查询词按空白切成词元，只要**任一结果**的歌名或歌手包含**任一词元**
 * （不区分大小写）即视为"有真实匹配"。查询词没有词元（纯空白）时视为有匹配（交给正常流程）。
 */
fun hasRealMatch(query: String, songs: List<Song>): Boolean {
    val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return true
    return songs.any { song ->
        val haystack = (song.name + " " + song.artists.joinToString(" ")).lowercase()
        tokens.any { haystack.contains(it) }
    }
}
