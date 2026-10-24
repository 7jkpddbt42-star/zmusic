package com.musicbox.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicbox.app.model.Song
import com.musicbox.app.ui.components.Entrance
import com.musicbox.app.ui.components.GlassEmptyState
import com.musicbox.app.ui.components.GlassPanel
import com.musicbox.app.ui.components.MusicIcons
import com.musicbox.app.ui.components.PageHeader
import com.musicbox.app.ui.components.liquidglass.LocalGlassBackdrop
import com.musicbox.app.ui.components.liquidglass.liquidGlass
import com.musicbox.app.ui.theme.ArtworkImage
import com.musicbox.app.ui.theme.THUMB_EDGE_PX

/**
 * 搜索页（重绘版）。
 *
 * 视觉：
 * - 页头 + 玻璃搜索胶囊（含自有线性图标）+ 实心主题色搜索键；
 * - 结果整体放进一块玻璃面板，行内显示封面、歌名与歌手；
 * - 空态、加载态、错误态各自有独立形态，不再是一行裸文字。
 *
 * 性能：列表滚动不读取逐帧状态，搜索状态变化才重组，避免滚动掉帧。
 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    nowPlayingSongId: Long?,
    onSongClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
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
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Entrance(index = 0) {
            PageHeader(
                title = "发现音乐",
                subtitle = if (state.searched && state.results.isNotEmpty()) {
                    "找到 ${state.results.size} 首 · 网易云曲库"
                } else {
                    "搜索网易云曲库，选中即可试听"
                },
            )
        }

        Entrance(index = 1) {
            SearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                onSubmit = submit,
                onClear = { onQueryChange("") },
            )
        }

        Entrance(index = 2, modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.fillMaxSize().padding(top = 14.dp)) {
            val showEmpty = state.searched && !state.loading && state.message == null &&
                (state.results.isEmpty() || state.noRealMatch)

            when {
                state.loading -> SearchLoading()
                state.message != null -> SearchMessage(state.message)

                showEmpty -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    GlassEmptyState(
                        icon = MusicIcons.Search,
                        title = if (state.noRealMatch) "没有找到相关结果" else "没有找到结果",
                        description = "换个关键词试试，歌名、歌手或专辑都可以",
                    )
                }

                state.results.isNotEmpty() -> GlassPanel(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(26.dp),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        item(key = "resultHeader") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(
                                    start = 10.dp,
                                    end = 10.dp,
                                    top = 4.dp,
                                    bottom = 8.dp,
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "曲目",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "${state.results.size} 首",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                        }
                        itemsIndexed(state.results, key = { _, song -> song.id }) { index, song ->
                            SongResultRow(
                                song = song,
                                isCurrent = song.id == nowPlayingSongId,
                                onClick = { keyboard?.hide(); onSongClick(index) },
                            )
                        }
                    }
                }

                else -> Column(
                    modifier = Modifier.fillMaxSize().padding(bottom = 48.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = MusicIcons.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.size(38.dp),
                    )
                    Text(
                        text = "搜索你想听的歌",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = "输入关键词后回车，或点右侧按钮",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            }
        }
    }
}

/** 玻璃搜索胶囊：图标 + 无边框输入框 + 清除键 + 实心搜索键。 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .liquidGlass(
                backdrop = LocalGlassBackdrop.current,
                shape = RoundedCornerShape(27.dp),
                blurRadius = 6.dp,
                tint = Color.White.copy(alpha = 0.08f),
            )
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MusicIcons.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.size(19.dp),
        )
        Box(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
            if (query.isEmpty()) {
                Text(
                    text = "搜索歌曲、歌手、专辑",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = onClear, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = MusicIcons.Close,
                    contentDescription = "清空",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onSubmit),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MusicIcons.Search,
                contentDescription = "搜索",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/** 搜索中的玻璃状态条。 */
@Composable
private fun SearchLoading() {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "正在搜索…",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/** 搜索失败 / 提示的玻璃卡片。 */
@Composable
private fun SearchMessage(message: String) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/** 单条搜索结果。 */
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
            .clip(RoundedCornerShape(18.dp))
            .background(if (isCurrent) primary.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(
            url = song.coverUrl,
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(10.dp),
            maxEdgePx = THUMB_EDGE_PX,
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(
                text = song.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artists.joinToString(" / ") +
                    (song.album?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
