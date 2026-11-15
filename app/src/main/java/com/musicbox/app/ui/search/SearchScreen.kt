package com.musicbox.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicbox.app.model.Song
import com.musicbox.app.ui.components.Entrance
import com.musicbox.app.ui.components.MusicIcons
import com.musicbox.app.ui.player.formatTime
import com.musicbox.app.ui.theme.ArtworkImage
import com.musicbox.app.ui.theme.THUMB_EDGE_PX

/** 热门搜索词：网易云的搜索页在没有历史记录时展示一份固定榜单，点词直搜。 */
private val HotKeywords = listOf(
    "周杰伦", "陈奕迅", "告五人", "林俊杰",
    "邓紫棋", "薛之谦", "毛不易", "纯音乐",
)

/**
 * 搜索页：按网易云手机端搜索页的排布重做。
 *
 * 顶部是常驻的搜索框（右侧「搜索」文字按钮），下面依次是最近搜索与热门搜索榜单；
 * 搜索中/无结果/结果列表都在这块区域里滚动，结果按单曲列表平铺，不再套整块玻璃面板。
 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    history: List<String>,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onQuickSearch: (String) -> Unit,
    onClearHistory: () -> Unit,
    nowPlayingSongId: Long?,
    onSongClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    // 切回搜索页时不重播入场
    animateEntrance: Boolean = true,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        keyboard?.hide()
        onSearch()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Entrance(index = 0, animate = animateEntrance) {
            SearchBarRow(
                query = state.query,
                onQueryChange = onQueryChange,
                onClear = { onQueryChange("") },
                onSubmit = submit,
            )
        }

        Entrance(index = 1, modifier = Modifier.weight(1f), animate = animateEntrance) {
            Box(modifier = Modifier.fillMaxSize()) {
                val showEmpty = state.searched && !state.loading && state.message == null &&
                    (state.results.isEmpty() || state.noRealMatch)

                when {
                    state.loading -> SearchLoading()
                    state.message != null -> SearchMessage(state.message)
                    showEmpty -> SearchEmpty(state.noRealMatch)
                    state.results.isNotEmpty() -> SearchResults(
                        results = state.results,
                        nowPlayingSongId = nowPlayingSongId,
                        onSongClick = { index ->
                            keyboard?.hide()
                            onSongClick(index)
                        },
                    )

                    else -> SearchIdle(
                        history = history,
                        onQuickSearch = onQuickSearch,
                        onClearHistory = onClearHistory,
                    )
                }
            }
        }
    }
}

/** 顶部搜索行：圆角灰底输入框 + 右侧「搜索」。 */
@Composable
private fun SearchBarRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .clip(RoundedCornerShape(19.dp))
                // 与首页搜索栏同一套底色：浅色胶囊 + 极细描边，在彩色背景上边界清晰
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f))
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                    shape = RoundedCornerShape(19.dp),
                )
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = MusicIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.size(17.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "搜索歌曲、歌手、专辑",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = MusicIcons.Close,
                        contentDescription = "清空",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Text(
            text = "搜索",
            fontSize = 14.sp,
            color = primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onSubmit)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

/** 空态：最近搜索 + 热门搜索，全部平铺在页面上。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchIdle(
    history: List<String>,
    onQuickSearch: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (history.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "最近搜索",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClearHistory, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = MusicIcons.Trash,
                        contentDescription = "清空最近搜索",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            // 最近 6 条：标签宽度跟随文字自然换行
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp),
            ) {
                history.take(6).forEach { keyword ->
                    KeywordChip(keyword, onQuickSearch)
                }
            }
        }

        Text(
            text = "热门搜索",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 2.dp),
        )

        // 网易云式两列榜单：序号 + 关键词，前三名序号用主题色
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            HotKeywords.chunked(2).forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (rowIndex == 0) 0.dp else 2.dp),
                ) {
                    row.forEachIndexed { columnIndex, keyword ->
                        HotKeywordRow(
                            rank = rowIndex * 2 + columnIndex + 1,
                            keyword = keyword,
                            onQuickSearch = onQuickSearch,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size < 2) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HotKeywordRow(
    rank: Int,
    keyword: String,
    onQuickSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val top = rank <= 3
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onQuickSearch(keyword) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = rank.toString(),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (top) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            },
            modifier = Modifier.width(22.dp),
        )
        Text(
            text = keyword,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun KeywordChip(
    keyword: String,
    onQuickSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f))
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                shape = RoundedCornerShape(16.dp),
            )
            .clickable { onQuickSearch(keyword) }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = keyword,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 搜索结果：平铺单曲列表，标题行只写数量。 */
@Composable
private fun SearchResults(
    results: List<Song>,
    nowPlayingSongId: Long?,
    onSongClick: (Int) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "resultHeader") {
            Text(
                text = "共 ${results.size} 首",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            )
        }
        itemsIndexed(
            results,
            key = { index, song -> "$index-${song.id}" },
        ) { index, song ->
            SongResultRow(
                song = song,
                isCurrent = song.id == nowPlayingSongId,
                onClick = { onSongClick(index) },
            )
        }
    }
}

/** 搜索中：一行进度条，不挡内容。 */
@Composable
private fun SearchLoading() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 1.8.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "正在搜索…",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun SearchMessage(message: String) {
    Text(
        text = message,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
    )
}

@Composable
private fun SearchEmpty(noRealMatch: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = 64.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = MusicIcons.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.size(34.dp),
        )
        Text(
            text = if (noRealMatch) "没有找到相关结果" else "没有找到结果",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = "换个关键词试试，歌名、歌手或专辑都可以",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** 单条搜索结果：封面 + 歌名 + 歌手·专辑 + 时长。 */
@Composable
private fun SongResultRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(
            url = song.coverUrl,
            modifier = Modifier.size(46.dp),
            shape = RoundedCornerShape(10.dp),
            maxEdgePx = THUMB_EDGE_PX,
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(
                text = song.name,
                fontSize = 14.sp,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artists.joinToString(" / ") +
                    (song.album?.let { " · $it" } ?: ""),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(
            text = formatTime(song.durationMs),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}
