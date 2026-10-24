package com.musicbox.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.musicbox.app.ui.components.liquidglass.LocalGlassBackdrop
import com.musicbox.app.ui.components.liquidglass.liquidGlass

/**
 * 页面内统一玻璃面板。
 *
 * 只对页面背景源采样（`LocalGlassBackdrop` 由页面壳层提供），因此面板内可以放
 * 任意列表/文字，不会出现自采样鬼影。整块列表用一块玻璃而不是每行一块，
 * 避免几十个 drawBackdrop 节点叠加带来渲染开销。
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    tint: Color = Color.White.copy(alpha = 0.08f),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.liquidGlass(
            backdrop = LocalGlassBackdrop.current,
            shape = shape,
            blurRadius = 6.dp,
            tint = tint,
        ),
        content = content,
    )
}

/** 玻璃小胶囊按钮：用于页头动作（清空、登录、退出等）。 */
@Composable
fun GlassPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White.copy(alpha = 0.08f),
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .liquidGlass(
                backdrop = LocalGlassBackdrop.current,
                shape = RoundedCornerShape(50),
                blurRadius = 6.dp,
                tint = tint,
                interactionSource = interactionSource,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** 返回胶囊：左箭头 + 文案，用于全屏覆盖页的页头。 */
@Composable
fun GlassBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassPill(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = MusicIcons.ChevronLeft,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "返回",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 页面标题：可选前置动作（返回）+ 大标题说明，右侧可放玻璃按钮。 */
@Composable
fun PageHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        trailing?.invoke()
    }
}

/** 空状态：玻璃卡片 + 图标 + 标题 + 说明。 */
@Composable
fun GlassEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 14.dp),
                textAlign = TextAlign.Center,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}
