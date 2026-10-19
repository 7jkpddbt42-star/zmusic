package com.musicbox.app.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette

/**
 * 从封面位图取一个代表色作为主题种子。
 *
 * 优先级：Vibrant → LightVibrant → Muted → DarkMuted → Dominant；全空时回退固定种子色。
 * 取色结果再过一遍 [normalizeSeed]，把亮度/饱和度收进可读区间，避免过暗或过曝。
 *
 * 注意：本文件依赖 android.graphics.Bitmap，不参与 JVM 单测（取色逻辑的可测部分在
 * [SeedScheme] 的 [normalizeSeed] / [adjustForContrast]）。
 */
fun coverSeed(bitmap: Bitmap): Color {
    val palette = Palette.from(bitmap).generate()
    val argb = palette.vibrantSwatch?.rgb
        ?: palette.lightVibrantSwatch?.rgb
        ?: palette.mutedSwatch?.rgb
        ?: palette.darkMutedSwatch?.rgb
        ?: palette.dominantSwatch?.rgb
        ?: ThemeSpec.DEFAULT_SEED_ARGB
    return normalizeSeed(Color(argb))
}
