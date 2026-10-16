package com.musicbox.app.model

/**
 * 播放地址信息。
 *
 * isTrial 来自网易云取流响应的 freeTrialInfo：非 null 表示服务端只给了试听片段
 * （实测匿名态下 VIP 独占歌只给 30 秒、码率被压到 128k），UI 必须显式标注。
 */
data class StreamInfo(
    val url: String,
    val bitrateBps: Int,
    val isTrial: Boolean = false,
)
