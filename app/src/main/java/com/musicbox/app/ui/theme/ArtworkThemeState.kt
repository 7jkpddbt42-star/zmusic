package com.musicbox.app.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 当前封面取色结果的内存状态。播放页的 [ArtworkImage] 解码成功时写入一次，
 * 主题系统读取它作为「封面取色」来源的 seed。
 *
 * 只依赖播放页，因此主页在未进播放页时保持既有主题（分控主题下主页不受影响）。
 */
class ArtworkThemeState {

    private val _seed = MutableStateFlow<Color?>(null)
    val seed: StateFlow<Color?> = _seed.asStateFlow()

    fun update(bitmap: Bitmap) {
        _seed.value = coverSeed(bitmap)
    }
}
