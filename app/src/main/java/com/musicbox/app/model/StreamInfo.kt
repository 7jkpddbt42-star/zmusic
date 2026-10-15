package com.musicbox.app.model

/** 播放地址信息。MI-2 起由音源填充。 */
data class StreamInfo(
    val url: String,
    val bitrateBps: Int,
)
