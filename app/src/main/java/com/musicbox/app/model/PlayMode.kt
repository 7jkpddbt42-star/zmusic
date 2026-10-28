package com.musicbox.app.model

/**
 * 播放模式。三种模式的差异全部由 ExoPlayer 原生能力承担：
 * 顺序 = repeatMode OFF、单曲循环 = repeatMode ONE、随机 = shuffle 打开 + repeatMode ALL。
 * 映射实现见 player/PlayModeMapping.kt。
 */
enum class PlayMode(val key: String, val label: String) {
    ORDER("order", "顺序"),
    REPEAT_ONE("repeat_one", "单曲循环"),
    SHUFFLE("shuffle", "随机"),
    ;

    fun next(): PlayMode = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromKey(key: String?): PlayMode = entries.firstOrNull { it.key == key } ?: ORDER

        /** 播放页各风格只拿到中文文案，这里反查枚举，用来选对应的模式图标。 */
        fun fromLabel(label: String?): PlayMode =
            entries.firstOrNull { it.label == label } ?: ORDER
    }
}
