package com.musicbox.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 用户偏好。译文/罗马音默认开关在 MI-3 是内存态，本里程碑起持久化。 */
class SettingsRepository(private val store: KeyValueStore) {

    private val _showTranslation = MutableStateFlow(store.getBoolean(KEY_TRANSLATION, true))
    val showTranslation: StateFlow<Boolean> = _showTranslation.asStateFlow()

    private val _showRomaji = MutableStateFlow(store.getBoolean(KEY_ROMAJI, false))
    val showRomaji: StateFlow<Boolean> = _showRomaji.asStateFlow()

    private val _fallbackBaseUrl = MutableStateFlow(store.getString(KEY_FALLBACK_URL).orEmpty())
    val fallbackBaseUrl: StateFlow<String> = _fallbackBaseUrl.asStateFlow()

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

    private companion object {
        const val KEY_TRANSLATION = "show_translation"
        const val KEY_ROMAJI = "show_romaji"
        const val KEY_FALLBACK_URL = "fallback_base_url"
    }
}
