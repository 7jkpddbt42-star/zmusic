package com.musicbox.app.model

/**
 * 播放模式。三种模式的差异全部由 ExoPlayer 原生能力承担：
 * 顺序 = repeatMode OFF、单曲循环 = repeatMode ONE、随机 = shuffle 打开 + repeatMode ALL。
 * 映射实现见 player/PlayModeMapping.kt。
 */
enum class PlayMode(val label: String) {
    ORDER("顺序"),
    REPEAT_ONE("单曲循环"),
    SHUFFLE("随机"),
    ;

    fun next(): PlayMode = entries[(ordinal + 1) % entries.size]
}
