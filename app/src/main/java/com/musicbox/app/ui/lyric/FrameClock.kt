package com.musicbox.app.ui.lyric

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import com.musicbox.app.player.PositionSource

/**
 * 逐帧时钟（设计文档 §5.2 的核心决策）。
 *
 * 每帧用 withFrameNanos 唤醒一次，读一次 ExoPlayer 的真实播放位置（毫秒精度），
 * 据此驱动逐字动画的插值。**绝不**用播放器的状态回调（约 4~10Hz）——那必然阶跃、顿挫。
 *
 * 暂停时位置恒定，而 Compose 的 MutableState 用结构相等短路无效化，
 * 因此写入同值不会触发重组合——不必额外判断 isPlaying。
 */
@Composable
internal fun rememberFramePosition(source: PositionSource): State<Long> {
    val position = remember { mutableLongStateOf(source.positionMs()) }
    LaunchedEffect(source) {
        while (true) {
            withFrameNanos {
                position.longValue = source.positionMs()
            }
        }
    }
    return position
}
