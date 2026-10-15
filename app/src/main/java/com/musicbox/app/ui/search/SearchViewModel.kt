package com.musicbox.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicbox.app.model.SearchType
import com.musicbox.app.model.Song
import com.musicbox.app.source.MusicProvider
import com.musicbox.app.source.SourceError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<Song> = emptyList(),
    val message: String? = null,
    val searched: Boolean = false,
)

class SearchViewModel(
    private val provider: MusicProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun search() {
        val keyword = _state.value.query.trim()
        if (keyword.isEmpty()) return

        _state.update { it.copy(loading = true, message = null) }
        viewModelScope.launch {
            provider.search(keyword, SearchType.SONG, page = 1)
                .onSuccess { songs ->
                    _state.update {
                        it.copy(loading = false, results = songs, searched = true, message = null)
                    }
                }
                .onFailure { error ->
                    val text = when (error) {
                        is SourceError.Network -> "网络不可用，请检查网络"
                        is SourceError.Api -> "音源暂时不可用"
                        is SourceError.Parse -> "解析失败，可能接口已变更"
                        is SourceError.NotImplementedYet -> "该功能尚未实现"
                        else -> "出错了"
                    }
                    _state.update {
                        it.copy(loading = false, searched = true, message = text)
                    }
                }
        }
    }
}
