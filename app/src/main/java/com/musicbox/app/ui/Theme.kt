package com.musicbox.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.musicbox.app.ui.theme.AppTypography
import com.musicbox.app.ui.theme.ThemeMode
import com.musicbox.app.ui.theme.ThemeSpec
import com.musicbox.app.ui.theme.buildScheme

/**
 * 当前页面的 seed 覆盖。为 null 时沿用主 seed；「播放页独立主题」开启时，
 * MainActivity 会在播放页/歌词页外层提供 player seed，本页主题随之切换，主页不受影响。
 */
val LocalPageSeed = compositionLocalOf<Color?> { null }

/** 全局圆角：与液态玻璃胶囊/卡片的 24–30dp 圆角保持同一套语言。 */
private val MusicBoxShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

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
    MaterialTheme(
        colorScheme = buildScheme(effectiveSeed, dark),
        shapes = MusicBoxShapes,
        typography = AppTypography,
        content = content,
    )
}
