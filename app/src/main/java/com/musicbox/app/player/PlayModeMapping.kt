package com.musicbox.app.player

import androidx.media3.common.Player
import com.musicbox.app.model.PlayMode

/**
 * 播放模式 → ExoPlayer 原生开关。三种模式全部由 ExoPlayer 自己实现推进逻辑，
 * 我们不去手写队列索引（队列在 timeline 上，自动续播、通知栏上下首都因此可用）。
 */
internal fun PlayMode.applyTo(player: Player) {
    when (this) {
        // 顺序：播到队列末尾就停
        PlayMode.ORDER -> {
            player.shuffleModeEnabled = false
            player.repeatMode = Player.REPEAT_MODE_OFF
        }
        // 单曲循环：ExoPlayer 原生 REPEAT_MODE_ONE
        PlayMode.REPEAT_ONE -> {
            player.shuffleModeEnabled = false
            player.repeatMode = Player.REPEAT_MODE_ONE
        }
        // 随机：打乱顺序 + 队列放完继续随机
        PlayMode.SHUFFLE -> {
            player.shuffleModeEnabled = true
            player.repeatMode = Player.REPEAT_MODE_ALL
        }
    }
}
