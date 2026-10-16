package com.musicbox.app.player

/**
 * 逐帧可读的播放位置（毫秒）。
 *
 * 存在的唯一理由：UI 的逐字歌词时钟每帧都要问一次"现在播到哪了"，
 * 而它不该知道 ExoPlayer / MediaController 的存在（设计文档 §4.2 分层）。
 * 注意：**不要**用 PlaybackConnection.state.positionMs 那个 250ms 的
 * 状态流来驱动逐字动画——那个频率会明显阶跃（设计文档 §5.2）。
 */
fun interface PositionSource {
    fun positionMs(): Long
}
