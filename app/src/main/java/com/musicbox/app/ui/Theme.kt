package com.musicbox.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.musicbox.app.ui.theme.ThemeMode
import com.musicbox.app.ui.theme.ThemeSpec
import com.musicbox.app.ui.theme.buildScheme

/**
 * 当前页面的 seed 覆盖。为 null 时沿用主 seed；「播放页独立主题」开启时，
 * MainActivity 会在播放页/歌词页外层提供 player seed，本页主题随之切换，主页不受影响。
 */
val LocalPageSeed = compositionLocalOf<Color?> { null }

/**
 * 应用主题。按 [spec] 的模式决定深/浅，用 [seed] 生成 [androidx.compose.material3.ColorScheme]。
 * seed 为 null 时退回 Material3 默认方案（保证默认观感）。
 */
@Composable
internal fun MusicBoxTheme(spec: ThemeSpec, seed: Color?, content: @Composable () -> Unit) {
    val pageSeed = LocalPageSeed.current
    val effectiveSeed = pageSeed ?: seed
    val dark = when (spec.mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    MaterialTheme(colorScheme = buildScheme(effectiveSeed, dark), content = content)
}
