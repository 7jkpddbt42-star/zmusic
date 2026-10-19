package com.musicbox.app.data

import com.musicbox.app.ui.theme.SeedSource
import com.musicbox.app.ui.theme.ThemeMode
import com.musicbox.app.ui.theme.ThemeSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 用户偏好。译文/罗马音默认开关在 MI-3 是内存态，本里程碑起持久化。 */
class SettingsRepository(private val store: KeyValueStore) {

    private val _showTranslation = MutableStateFlow(store.getBoolean(KEY_TRANSLATION, true))
    val showTranslation: StateFlow<Boolean> = _showTranslation.asStateFlow()

    private val _showRomaji = MutableStateFlow(store.getBoolean(KEY_ROMAJI, false))
    val showRomaji: StateFlow<Boolean> = _showRomaji.asStateFlow()

    private val _fallbackBaseUrl = MutableStateFlow(store.getString(KEY_FALLBACK_URL).orEmpty())
    val fallbackBaseUrl: StateFlow<String> = _fallbackBaseUrl.asStateFlow()

    /** 主题偏好整包暴露：由若干设置 key 组成，UI 只订阅这一个 flow，接线更简单。 */
    private val _themeSpec = MutableStateFlow(readThemeSpec())
    val themeSpec: StateFlow<ThemeSpec> = _themeSpec.asStateFlow()

    private val _desktopLyricEnabled =
        MutableStateFlow(store.getBoolean(KEY_DESKTOP_LYRIC_ENABLED, false))
    val desktopLyricEnabled: StateFlow<Boolean> = _desktopLyricEnabled.asStateFlow()

    private val _desktopLyricFontSp =
        MutableStateFlow(store.getInt(KEY_DESKTOP_LYRIC_FONT_SP, DEFAULT_DESKTOP_LYRIC_FONT_SP))
    val desktopLyricFontSp: StateFlow<Int> = _desktopLyricFontSp.asStateFlow()

    private val _desktopLyricColorArgb = MutableStateFlow(
        store.getInt(KEY_DESKTOP_LYRIC_COLOR, DEFAULT_DESKTOP_LYRIC_COLOR),
    )
    val desktopLyricColorArgb: StateFlow<Int> = _desktopLyricColorArgb.asStateFlow()

    fun setShowTranslation(value: Boolean) {
        store.putBoolean(KEY_TRANSLATION, value)
        _showTranslation.value = value
    }

    fun setShowRomaji(value: Boolean) {
        store.putBoolean(KEY_ROMAJI, value)
        _showRomaji.value = value
    }

    fun setFallbackBaseUrl(value: String) {
        val trimmed = value.trim()
        store.putString(KEY_FALLBACK_URL, trimmed)
        _fallbackBaseUrl.value = trimmed
    }

    fun setThemeMode(mode: ThemeMode) {
        store.putString(KEY_THEME_MODE, mode.key)
        _themeSpec.update { it.copy(mode = mode) }
    }

    fun setSeedSource(source: SeedSource) {
        store.putString(KEY_SEED_SOURCE, source.key)
        _themeSpec.update { it.copy(seedSource = source) }
    }

    fun setFixedSeedArgb(argb: Int) {
        store.putInt(KEY_FIXED_SEED, argb)
        _themeSpec.update { it.copy(fixedSeedArgb = argb) }
    }

    fun setSeparatePlayerTheme(value: Boolean) {
        store.putBoolean(KEY_SEPARATE_PLAYER, value)
        _themeSpec.update { it.copy(separatePlayerTheme = value) }
    }

    fun setPlayerSeedSource(source: SeedSource) {
        store.putString(KEY_PLAYER_SEED_SOURCE, source.key)
        _themeSpec.update { it.copy(playerSeedSource = source) }
    }

    fun setPlayerFixedSeedArgb(argb: Int) {
        store.putInt(KEY_PLAYER_FIXED_SEED, argb)
        _themeSpec.update { it.copy(playerFixedSeedArgb = argb) }
    }

    fun setDesktopLyricEnabled(value: Boolean) {
        store.putBoolean(KEY_DESKTOP_LYRIC_ENABLED, value)
        _desktopLyricEnabled.value = value
    }

    fun setDesktopLyricFontSp(value: Int) {
        store.putInt(KEY_DESKTOP_LYRIC_FONT_SP, value)
        _desktopLyricFontSp.value = value
    }

    fun setDesktopLyricColorArgb(argb: Int) {
        store.putInt(KEY_DESKTOP_LYRIC_COLOR, argb)
        _desktopLyricColorArgb.value = argb
    }

    /** 悬浮窗坐标。未设置过时两值均为 [POSITION_UNSET]，由服务侧给默认位置。 */
    fun desktopLyricPosition(): Pair<Int, Int> =
        store.getInt(KEY_DESKTOP_LYRIC_X, POSITION_UNSET) to
            store.getInt(KEY_DESKTOP_LYRIC_Y, POSITION_UNSET)

    fun setDesktopLyricPosition(x: Int, y: Int) {
        store.putInt(KEY_DESKTOP_LYRIC_X, x)
        store.putInt(KEY_DESKTOP_LYRIC_Y, y)
    }

    private fun readThemeSpec(): ThemeSpec = ThemeSpec(
        mode = ThemeMode.fromKey(store.getString(KEY_THEME_MODE)),
        seedSource = SeedSource.fromKey(store.getString(KEY_SEED_SOURCE)),
        fixedSeedArgb = store.getInt(KEY_FIXED_SEED, ThemeSpec.DEFAULT_SEED_ARGB),
        separatePlayerTheme = store.getBoolean(KEY_SEPARATE_PLAYER, false),
        playerSeedSource = SeedSource.fromKey(store.getString(KEY_PLAYER_SEED_SOURCE)),
        playerFixedSeedArgb = store.getInt(KEY_PLAYER_FIXED_SEED, ThemeSpec.DEFAULT_SEED_ARGB),
    )

    private companion object {
        const val KEY_TRANSLATION = "show_translation"
        const val KEY_ROMAJI = "show_romaji"
        const val KEY_FALLBACK_URL = "fallback_base_url"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_SEED_SOURCE = "seed_source"
        const val KEY_FIXED_SEED = "fixed_seed_argb"
        const val KEY_SEPARATE_PLAYER = "separate_player_theme"
        const val KEY_PLAYER_SEED_SOURCE = "player_seed_source"
        const val KEY_PLAYER_FIXED_SEED = "player_fixed_seed_argb"
        const val KEY_DESKTOP_LYRIC_ENABLED = "desktop_lyric_enabled"
        const val KEY_DESKTOP_LYRIC_FONT_SP = "desktop_lyric_font_sp"
        const val KEY_DESKTOP_LYRIC_COLOR = "desktop_lyric_color_argb"
        const val KEY_DESKTOP_LYRIC_X = "desktop_lyric_x"
        const val KEY_DESKTOP_LYRIC_Y = "desktop_lyric_y"

        /** 桌面歌词默认字号（sp）/ 颜色（白色）/ 坐标未设置哨兵。 */
        const val DEFAULT_DESKTOP_LYRIC_FONT_SP = 20
        const val POSITION_UNSET = Int.MIN_VALUE
        val DEFAULT_DESKTOP_LYRIC_COLOR = 0xFFFFFFFF.toInt()
    }
}
