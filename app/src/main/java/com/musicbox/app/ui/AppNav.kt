package com.musicbox.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicbox.app.ui.components.MusicIcons
import com.musicbox.app.ui.components.liquidglass.LocalGlassBackdrop
import com.musicbox.app.ui.components.liquidglass.liquidGlass

/** 底部三标签。用自写 `when` 切换，不引入 navigation-compose（保持轻量）。 */
enum class MainTab(val label: String) {
    HOME("发现"),
    SEARCH("搜索"),
    MINE("我的"),
}

/**
 * 悬浮玻璃导航胶囊。
 *
 * 与旧版 Material NavigationBar 的区别：
 * - 不再贴边铺满、只圆顶部两角，而是四周留白的独立胶囊，四周都能看到折射；
 * - 选中态是一枚主题色玻璃药丸 + 图标微放大，不依赖 Material 的导航条高亮块；
 * - 图标来自 [MusicIcons] 自有线性图标集，与页面其它控件同一套笔触。
 *
 * 外层留白与导航条 inset 由调用方（MainActivity 底部浮层）负责。
 */
@Composable
fun BottomTabs(current: MainTab, onSelect: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop = LocalGlassBackdrop.current,
                shape = RoundedCornerShape(30.dp),
                blurRadius = 6.dp,
                tint = Color.White.copy(alpha = 0.08f),
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(6.dp).height(60.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MainTab.entries.forEach { tab ->
                NavItem(
                    tab = tab,
                    selected = tab == current,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    tab: MainTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.onSurfaceVariant
    val iconTint by animateColorAsState(
        targetValue = if (selected) primary else idle.copy(alpha = 0.88f),
        animationSpec = tween(durationMillis = 180),
        label = "navIconTint",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) primary else idle,
        animationSpec = tween(durationMillis = 180),
        label = "navLabelColor",
    )
    val pillColor by animateColorAsState(
        targetValue = if (selected) primary.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = tween(durationMillis = 220),
        label = "navPill",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.92f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy,
        ),
        label = "navIconScale",
    )
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .height(60.dp)
            .background(pillColor, RoundedCornerShape(24.dp))
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = when (tab) {
                MainTab.HOME -> MusicIcons.Compass
                MainTab.SEARCH -> MusicIcons.Search
                MainTab.MINE -> MusicIcons.Person
            },
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp).graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
            },
        )
        Text(
            text = tab.label,
            color = labelColor,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
