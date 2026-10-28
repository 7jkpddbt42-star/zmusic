package com.musicbox.app.data

import com.musicbox.app.model.PlayMode
import com.musicbox.app.ui.theme.SeedSource
import com.musicbox.app.ui.theme.ThemeMode
import com.musicbox.app.ui.theme.ThemeSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 歌词页视觉样式。
 * [Focus]：当前行居中大字高亮、其余行缩小淡出（默认，参考主流播放器）；
 * [Classic]：统一字号列表、当前行仅描色。
 */
enum class LyricStyle(val key: String) {
    Focus("focus"),
    Classic("classic");

    companion object {
        fun fromKey(value: String?): LyricStyle =
            values().firstOrNull { it.key == value } ?: Focus
    }
}

/** 用户偏好。译文/罗马音默认开关在 MI-3 是内存态，本里程碑起持久化。 */
class SettingsRepository(private val store: KeyValueStore) {

    private val _showTranslation = MutableStateFlow(store.getBoolean(KEY_TRANSLATION, true))
    val showTranslation: StateFlow<Boolean> = _showTranslation.asStateFlow()

    private val _showRomaji = MutableStateFlow(store.getBoolean(KEY_ROMAJI, false))
    val showRomaji: StateFlow<Boolean> = _showRomaji.asStateFlow()

    private val _lyricStyle =
        MutableStateFlow(LyricStyle.fromKey(store.getString(KEY_LYRIC_STYLE)))
    val lyricStyle: StateFlow<LyricStyle> = _lyricStyle.asStateFlow()

    private val _playerStyle =
        MutableStateFlow(PlayerStyle.fromKey(store.getString(KEY_PLAYER_STYLE)))
    val playerStyle: StateFlow<PlayerStyle> = _playerStyle.asStateFlow()

    private val _fallbackBaseUrl = MutableStateFlow(store.getString(KEY_FALLBACK_URL).orEmpty())
    val fallbackBaseUrl: StateFlow<String> = _fallbackBaseUrl.asStateFlow()

    /** 播放模式：顺序 / 单曲循环 / 随机，重启后沿用上次选择。 */
    private val _playMode = MutableStateFlow(PlayMode.fromKey(store.getString(KEY_PLAY_MODE)))
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    /** 免费听歌模式：开 / 关（关=普通模式，只用网易云官方音源）。 */
    private val _freeSourceEnabled = MutableStateFlow(isFreeSourceEnabledNow())
    val freeSourceEnabled: StateFlow<Boolean> = _freeSourceEnabled.asStateFlow()

    /** 智能过渡：切歌按节拍淡出淡入，默认开启。 */
    private val _smartTransition = MutableStateFlow(isSmartTransitionEnabledNow())
    val smartTransition: StateFlow<Boolean> = _smartTransition.asStateFlow()

    /** 主题偏好整包暴露：由若干设置 key 组成，UI 只订阅这一个 flow，接线更简单。 */
    private val _themeSpec = MutableStateFlow(readThemeSpec())
    val themeSpec: StateFlow<ThemeSpec> = _themeSpec.asStateFlow()

    /** 播放页背景偏好整包暴露：类型 + 私有目录素材路径。 */
    private val _backgroundSpec = MutableStateFlow(readBackgroundSpec())
    val backgroundSpec: StateFlow<BackgroundSpec> = _backgroundSpec.asStateFlow()

    private val _backgroundBlur =
        MutableStateFlow(BackgroundBlur.fromKey(store.getString(KEY_BACKGROUND_BLUR)))
    val backgroundBlur: StateFlow<BackgroundBlur> = _backgroundBlur.asStateFlow()

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

    fun setLyricStyle(value: LyricStyle) {
        store.putString(KEY_LYRIC_STYLE, value.key)
        _lyricStyle.value = value
    }

    fun setPlayerStyle(value: PlayerStyle) {
        store.putString(KEY_PLAYER_STYLE, value.key)
        _playerStyle.value = value
    }

    fun setFallbackBaseUrl(value: String) {
        val trimmed = value.trim()
        store.putString(KEY_FALLBACK_URL, trimmed)
        _fallbackBaseUrl.value = trimmed
    }

    fun setFreeSourceEnabled(value: Boolean) {
        store.putBoolean(KEY_FREE_SOURCE, value)
        _freeSourceEnabled.value = value
    }

    fun setPlayMode(value: PlayMode) {
        store.putString(KEY_PLAY_MODE, value.key)
        _playMode.value = value
    }

    fun setSmartTransition(value: Boolean) {
        store.putBoolean(KEY_SMART_TRANSITION, value)
        _smartTransition.value = value
    }

    /**
     * 直接读持久化值。播放服务与界面各自持有 AppContainer，内存里的 StateFlow
     * 不会互相同步，取流侧必须用这个「现读」入口，切换模式才能立刻生效。
     */
    fun isFreeSourceEnabledNow(): Boolean = store.getBoolean(KEY_FREE_SOURCE, false)

    /** 播放服务侧现读「智能过渡」开关，与界面各自持有容器时也能立刻生效。 */
    fun isSmartTransitionEnabledNow(): Boolean = store.getBoolean(KEY_SMART_TRANSITION, true)

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

    fun setBackgroundImage(path: String) {
        store.putString(KEY_BACKGROUND_TYPE, BackgroundType.Image.key)
        store.putString(KEY_BACKGROUND_PATH, path)
        _backgroundSpec.value = BackgroundSpec(BackgroundType.Image, path)
    }

    fun setBackgroundVideo(path: String) {
        store.putString(KEY_BACKGROUND_TYPE, BackgroundType.Video.key)
        store.putString(KEY_BACKGROUND_PATH, path)
        _backgroundSpec.value = BackgroundSpec(BackgroundType.Video, path)
    }

    /** 恢复默认：类型回到封面模糊，路径清空。 */
    fun resetBackground() {
        store.putString(KEY_BACKGROUND_TYPE, BackgroundType.Cover.key)
        store.putString(KEY_BACKGROUND_PATH, "")
        _backgroundSpec.value = BackgroundSpec.COVER
    }

    fun setBackgroundBlur(value: BackgroundBlur) {
        store.putString(KEY_BACKGROUND_BLUR, value.key)
        _backgroundBlur.value = value
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

    private fun readBackgroundSpec(): BackgroundSpec = BackgroundSpec(
        type = BackgroundType.fromKey(store.getString(KEY_BACKGROUND_TYPE)),
        path = store.getString(KEY_BACKGROUND_PATH).orEmpty().ifBlank { null },
    )

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
        const val KEY_LYRIC_STYLE = "lyric_style"
        const val KEY_PLAYER_STYLE = "player_style"
        const val KEY_FALLBACK_URL = "fallback_base_url"
        const val KEY_FREE_SOURCE = "free_source_enabled"
        const val KEY_PLAY_MODE = "play_mode"
        const val KEY_SMART_TRANSITION = "smart_transition"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_SEED_SOURCE = "seed_source"
        const val KEY_FIXED_SEED = "fixed_seed_argb"
        const val KEY_SEPARATE_PLAYER = "separate_player_theme"
        const val KEY_PLAYER_SEED_SOURCE = "player_seed_source"
        const val KEY_PLAYER_FIXED_SEED = "player_fixed_seed_argb"
        const val KEY_BACKGROUND_TYPE = "background_type"
        const val KEY_BACKGROUND_PATH = "background_path"
        const val KEY_BACKGROUND_BLUR = "background_blur"
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
