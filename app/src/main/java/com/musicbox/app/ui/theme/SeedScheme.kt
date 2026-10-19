package com.musicbox.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** 主题模式：跟随系统 / 固定浅色 / 固定深色。 */
enum class ThemeMode(val key: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
    ;

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: System
    }
}

/** 取色来源：当前封面 / 系统壁纸 / 固定色。 */
enum class SeedSource(val key: String) {
    Cover("cover"),
    Wallpaper("wallpaper"),
    Fixed("fixed"),
    ;

    companion object {
        fun fromKey(key: String?): SeedSource = entries.firstOrNull { it.key == key } ?: Cover
    }
}

/**
 * 主题偏好快照。纯数据、无 Android 依赖，可在 JVM 单测里断言。
 *
 * [separatePlayerTheme] 为真时，播放页/歌词页用 [playerSeedSource]/[playerFixedSeedArgb]
 * 单独计算 seed，主页沿用主 seed（分控主题）。
 */
data class ThemeSpec(
    val mode: ThemeMode = ThemeMode.System,
    val seedSource: SeedSource = SeedSource.Cover,
    val fixedSeedArgb: Int = DEFAULT_SEED_ARGB,
    val separatePlayerTheme: Boolean = false,
    val playerSeedSource: SeedSource = SeedSource.Cover,
    val playerFixedSeedArgb: Int = DEFAULT_SEED_ARGB,
) {
    companion object {
        /** 兜底种子色（低饱和紫），与原本硬编码暗色的观感接近。 */
        const val DEFAULT_SEED_ARGB: Int = 0xFF7C5CFF.toInt()
    }
}

// ---------- 颜色工具（HSL / 感知亮度） ----------

/** WCAG 感知亮度：0.2126R + 0.7152G + 0.0722B。 */
fun perceivedLuminance(color: Color): Float =
    0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue

/** 色相 0~360，饱和度/亮度 0~1。 */
private data class Hsl(val h: Float, val s: Float, val l: Float)

private fun rgbToHsl(r: Float, g: Float, b: Float): Hsl {
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f
    val d = max - min
    val s = if (d == 0f) 0f else d / (1f - kotlin.math.abs(2f * l - 1f)).coerceAtLeast(1e-4f)
    val h = when {
        d == 0f -> 0f
        max == r -> 60f * (((g - b) / d) % 6f)
        max == g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }
    return Hsl(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
}

private fun hslToRgb(h: Float, s: Float, l: Float): Triple<Float, Float, Float> {
    val hh = ((h % 360f) + 360f) % 360f / 60f
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs((hh % 2f) - 1f))
    val (r1, g1, b1) = when {
        hh < 1f -> Triple(c, x, 0f)
        hh < 2f -> Triple(x, c, 0f)
        hh < 3f -> Triple(0f, c, x)
        hh < 4f -> Triple(0f, x, c)
        hh < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Triple(
        (r1 + m).coerceIn(0f, 1f),
        (g1 + m).coerceIn(0f, 1f),
        (b1 + m).coerceIn(0f, 1f),
    )
}

private fun tone(h: Float, s: Float, l: Float): Color {
    val (r, g, b) = hslToRgb(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
    return Color(r, g, b, 1f)
}

/**
 * 把任意种子色规整到"深/浅底都能用"的中间亮度区间，避免取色结果过暗或过曝。
 * 供 [CoverPalette] 在选出色值后调用。
 */
fun normalizeSeed(color: Color): Color {
    val hsl = rgbToHsl(color.red, color.green, color.blue)
    val (r, g, b) = hslToRgb(hsl.h, hsl.s.coerceIn(0f, 0.9f), hsl.l.coerceIn(0.35f, 0.78f))
    return Color(r, g, b, color.alpha)
}

/**
 * 对比度校正：保证 [color] 在 [dark]（深/浅主题）下与底色有足够对比。
 * 深色主题下亮度不足则向白提亮；浅色主题下亮度过高则向黑压暗；随后把过高的饱和度拉回。
 *
 * 底色亮度按经验取：深色底 ~0.05、浅色底 ~0.95。
 */
fun adjustForContrast(color: Color, dark: Boolean, minContrast: Float): Color {
    val bgLum = if (dark) 0.05f else 0.95f
    // 由 WCAG 对比度公式反解出目标亮度：(L+0.05)/(bg+0.05) = minContrast
    val target = if (dark) {
        (minContrast * (bgLum + 0.05f)) - 0.05f
    } else {
        ((bgLum + 0.05f) / minContrast) - 0.05f
    }.coerceIn(0f, 1f)

    var r = color.red
    var g = color.green
    var b = color.blue
    val current = perceivedLuminance(color)
    if (dark && current < target) {
        val t = ((target - current) / (1f - current).coerceAtLeast(1e-3f)).coerceIn(0f, 1f)
        r += (1f - r) * t
        g += (1f - g) * t
        b += (1f - b) * t
    } else if (!dark && current > target) {
        val t = ((current - target) / current.coerceAtLeast(1e-3f)).coerceIn(0f, 1f)
        r *= (1f - t)
        g *= (1f - t)
        b *= (1f - t)
    }

    val hsl = rgbToHsl(r, g, b)
    val maxSat = if (dark) 0.85f else 0.9f
    val (rr, gg, bb) = if (hsl.s > maxSat) hslToRgb(hsl.h, maxSat, hsl.l) else Triple(r, g, b)
    return Color(rr.coerceIn(0f, 1f), gg.coerceIn(0f, 1f), bb.coerceIn(0f, 1f), color.alpha)
}

private const val MIN_CONTRAST = 4.5f

/**
 * 由种子色生成完整的 [ColorScheme]。种子为 null 时退回 Material3 默认方案
 * （与当前硬编码暗色的观感一致，保证默认不比现在差）。
 */
fun buildScheme(seed: Color?, dark: Boolean): ColorScheme {
    if (seed == null) {
        return if (dark) darkColorScheme() else lightColorScheme()
    }
    val base = normalizeSeed(seed)
    val hsl = rgbToHsl(base.red, base.green, base.blue)
    val hue = hsl.h
    val sat = hsl.s.coerceIn(0.15f, 0.9f)

    val primary = adjustForContrast(tone(hue, sat, if (dark) 0.74f else 0.46f), dark, MIN_CONTRAST)
    val onPrimary = tone(hue, sat * 0.4f, if (dark) 0.12f else 0.99f)
    val primaryContainer = tone(hue, sat * 0.7f, if (dark) 0.32f else 0.90f)
    val onPrimaryContainer = if (dark) tone(hue, sat * 0.6f, 0.92f) else tone(hue, sat * 0.7f, 0.15f)
    val secondary = adjustForContrast(tone(hue + 24f, sat * 0.8f, if (dark) 0.72f else 0.44f), dark, 4.0f)
    val tertiary = adjustForContrast(tone(hue - 36f, sat * 0.8f, if (dark) 0.74f else 0.46f), dark, 4.0f)
    val surface = tone(hue, if (dark) 0.05f else 0.06f, if (dark) 0.10f else 0.98f)
    val onSurface = if (dark) tone(hue, 0.10f, 0.92f) else tone(hue, 0.20f, 0.11f)
    val surfaceVariant = tone(hue, if (dark) 0.10f else 0.12f, if (dark) 0.20f else 0.90f)
    val onSurfaceVariant = if (dark) tone(hue, 0.08f, 0.75f) else tone(hue, 0.14f, 0.30f)
    val outline = tone(hue, 0.08f, if (dark) 0.60f else 0.45f)
    val containerHigh = tone(hue, if (dark) 0.08f else 0.10f, if (dark) 0.16f else 0.92f)

    return if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            tertiary = tertiary,
            background = surface,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainerHigh = containerHigh,
            outline = outline,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            tertiary = tertiary,
            background = surface,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainerHigh = containerHigh,
            outline = outline,
        )
    }
}
