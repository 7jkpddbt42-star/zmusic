package com.musicbox.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.musicbox.app.ui.components.glass

/** 底部四标签。用自写 `when` 切换，不引入 navigation-compose（保持轻量）。 */
enum class MainTab(val label: String) {
    SEARCH("搜索"),
    FAVORITE("收藏"),
    HISTORY("历史"),
    MINE("我的"),
}

@Composable
fun BottomTabs(current: MainTab, onSelect: (MainTab) -> Unit) {
    // 底栏玻璃化：NavigationBar 容器透明，外层 Box 提供玻璃底 + 顶部亮边 + 顶部高光
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glass(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                tintAlpha = 0.82f,
                borderAlpha = 0.24f,
            ),
    ) {
        NavigationBar(containerColor = Color.Transparent) {
            MainTab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = tab == current,
                    onClick = { onSelect(tab) },
                    icon = {
                        Icon(
                            imageVector = when (tab) {
                                MainTab.SEARCH -> Icons.Filled.Home
                                MainTab.FAVORITE -> Icons.Filled.Favorite
                                MainTab.HISTORY -> Icons.Filled.History
                                MainTab.MINE -> Icons.Filled.Person
                            },
                            contentDescription = tab.label,
                        )
                    },
                    label = { Text(tab.label) },
                )
            }
        }
    }
}
