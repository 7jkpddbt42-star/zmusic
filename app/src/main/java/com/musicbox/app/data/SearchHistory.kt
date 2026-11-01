package com.musicbox.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 最近搜索词：最多 [MAX_ENTRIES] 条，新词置顶、重复词前移。
 *
 * 只存几行字符串，用 SharedPreferences + JSON 就够，不必走文件原子写那一套。
 * 进程级单例由 [com.musicbox.app.AppContainer] 持有，界面只订阅 [entries]。
 */
class SearchHistory(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("search_history", Context.MODE_PRIVATE)

    private val gson = Gson()

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    /** 记录一次搜索：空词忽略，已存在的词提到最前，超出上限的丢弃。 */
    fun record(keyword: String) {
        val value = keyword.trim()
        if (value.isEmpty()) return
        val next = (listOf(value) + _entries.value.filterNot { it == value }).take(MAX_ENTRIES)
        if (next == _entries.value) return
        _entries.value = next
        persist(next)
    }

    fun remove(keyword: String) {
        val next = _entries.value.filterNot { it == keyword }
        if (next == _entries.value) return
        _entries.value = next
        persist(next)
    }

    fun clear() {
        if (_entries.value.isEmpty()) return
        _entries.value = emptyList()
        persist(emptyList())
    }

    private fun persist(entries: List<String>) {
        prefs.edit().putString(KEY_ENTRIES, gson.toJson(entries)).apply()
    }

    private fun load(): List<String> = runCatching {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        val parsed: List<String>? = gson.fromJson(raw, listType)
        parsed.orEmpty().filter { it.isNotBlank() }.take(MAX_ENTRIES)
    }.getOrDefault(emptyList())

    private companion object {
        const val KEY_ENTRIES = "entries"
        const val MAX_ENTRIES = 6

        val listType: java.lang.reflect.Type =
            object : TypeToken<List<String>>() {}.type
    }
}
