package com.musicbox.app.ui.components.liquidglass

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.musicbox.app.ui.MusicBoxTheme
import com.musicbox.app.ui.components.MusicIcons
import com.musicbox.app.ui.theme.ThemeSpec
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 液态玻璃验收预览。
 *
 * 背景刻意使用「图标网格 + 文字卡片 + 图片纹理」混合内容，且作为玻璃的 sibling 排在前面：
 * - 纯色或平滑渐变背景下折射在数学上不可见，不能用于验收；
 * - 玻璃卡片由官方库 drawBackdrop + lens + blur + 色散驱动，滚动时逐帧重采样。
 */
@Preview(name = "液态玻璃 / 纹理背景", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun LiquidGlassPreview() {
    MusicBoxTheme(spec = ThemeSpec(), seed = null) {
        LiquidGlassPreviewContent()
    }
}

@Composable
internal fun LiquidGlassPreviewContent() {
    val backdrop = rememberGlassBackdrop()

    // 验收页背景固定为深色：把所在窗口（Activity 或 Dialog）的系统栏图标改成浅色，
    // 否则浅色主题下会沿用深色图标，压在黑底上几乎不可见
    SystemBarsLight()
    // 拖动玻璃卡片：位置每帧变化，验证背景采样是逐帧更新而非只采一次
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val maxDragPx = with(LocalDensity.current) { 120.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF14141A)),
    ) {
        // 背景源：必须排在玻璃之前，且内容有足够纹理；内容可滚动，滚动时同样触发重采样
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassBackdropSource(backdrop),
        ) {
            TexturedBackdrop()
        }

        // 玻璃浮层：大卡片（28dp 圆角），可拖动
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 72.dp, start = 20.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset {
                        IntOffset(
                            dragOffset.x.coerceIn(-maxDragPx, maxDragPx).roundToInt(),
                            dragOffset.y.coerceIn(-maxDragPx, maxDragPx).roundToInt(),
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            dragOffset += dragAmount
                        }
                    }
                    .liquidGlass(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(28.dp),
                    )
                    .padding(18.dp),
            ) {
                Text("液态玻璃", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    "折射主导 · 6dp 辅助模糊 · 8% 白色着色 · 边缘色散",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "拖动卡片 / 滚动背景，观察折射逐帧更新",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            // 小尺寸控件：胶囊 + 图标，验证小尺寸下折射仍压紧边缘
            Row(
                modifier = Modifier
                    .liquidGlass(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(50),
                        tint = Color.White.copy(alpha = 0.08f),
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(MusicIcons.Play, contentDescription = null, tint = Color.White)
                Text("播放全部", color = Color.White)
            }
        }
    }
}

/** 混合纹理背景：图标网格 + 文字卡片 + 带噪点的图片块。 */
@Composable
private fun TexturedBackdrop() {
    val icons = listOf(
        MusicIcons.Search, MusicIcons.HeartFilled, MusicIcons.MusicNote, MusicIcons.Play,
        MusicIcons.Person, MusicIcons.Sliders, MusicIcons.Lyrics, MusicIcons.Queue,
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            "最近播放",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.65f),
        )
        Spacer(modifier = Modifier.height(10.dp))
        // 图标网格：4 列，足够密的边缘供折射采样
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(4) { column ->
                        val index = (row * 4 + column) % icons.size
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF2A2A34)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = icons[index],
                                contentDescription = null,
                                tint = Color(0xFFB9B9C6),
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        // 图片块：渐变 + 确定性噪点，避免「平滑渐变不可见折射」的验收陷阱
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PhotoTile(modifier = Modifier.weight(1f), seed = 3)
            PhotoTile(modifier = Modifier.weight(1f), seed = 7)
        }

        Spacer(modifier = Modifier.height(16.dp))
        // 文字卡片：细碎文字制造高频纹理；数量足够多以支持滚动验收
        repeat(8) { index ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF20202A))
                    .padding(12.dp),
            ) {
                Text("歌曲标题 ${index + 1}", color = Color.White.copy(alpha = 0.9f))
                Text(
                    "歌手 · 专辑 · 03:45",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PhotoTile(modifier: Modifier = Modifier, seed: Int) {
    Box(
        modifier = modifier
            .height(96.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF4A3F6B),
                        Color(0xFF23566B),
                        Color(0xFF7A3E4B),
                    ),
                ),
            ),
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawPhotoNoise(seed)
        }
        Icon(
            MusicIcons.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.align(Alignment.Center).size(30.dp),
        )
    }
}

/** 确定性噪点：让「图片」具备真实纹理，便于肉眼观察折射与色散。 */
private fun DrawScope.drawPhotoNoise(seed: Int) {
    repeat(90) { index ->
        val x = (abs((index * 37 + seed * 53) % 1000) / 1000f) * size.width
        val y = (abs((index * 71 + seed * 29) % 1000) / 1000f) * size.height
        val radius = 1.2f + (index % 3)
        drawCircle(
            color = Color.White.copy(alpha = 0.05f + (index % 4) * 0.02f),
            radius = radius,
            center = Offset(x, y),
        )
    }
}

/**
 * 把当前窗口（Dialog 优先，其次 Activity）的系统栏图标固定为浅色。
 * 验收页是固定深色背景，浅色主题下沿用深色图标会看不清。
 */
@Composable
private fun SystemBarsLight() {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = view.findDialogWindow()
            ?: view.context.findActivity()?.window
            ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }
}

/** 从组合视图向上找承载它的 Dialog 窗口（`getRootView` 会一路走到 DecorView，拿不到）。 */
private fun View.findDialogWindow(): Window? {
    var node: View? = this
    while (node != null) {
        if (node is DialogWindowProvider) return node.window
        node = node.parent as? View
    }
    return null
}

private fun Context.findActivity(): Activity? {
    var context: Context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
