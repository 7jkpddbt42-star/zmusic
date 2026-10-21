package com.musicbox.app.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.musicbox.app.ui.components.liquidglass.GlassBackdrop
import com.musicbox.app.ui.components.liquidglass.LiquidGlassPreviewContent
import com.musicbox.app.ui.components.liquidglass.glassBackdropSource
import com.musicbox.app.ui.components.liquidglass.liquidGlass
import com.musicbox.app.ui.components.liquidglass.rememberGlassBackdrop
import com.musicbox.app.ui.theme.SeedSource
import com.musicbox.app.ui.theme.ThemeMode
import com.musicbox.app.ui.theme.ThemeSpec

/** 固定色色板预设（8 个）：覆盖紫/蓝/青/绿/橙/红/粉/灰。 */
private val PRESET_COLORS: List<Int> = listOf(
    0xFF7C5CFF.toInt(),
    0xFF3D7BFF.toInt(),
    0xFF00B4A0.toInt(),
    0xFF3BA55D.toInt(),
    0xFFE58A00.toInt(),
    0xFFE5484D.toInt(),
    0xFFD6409F.toInt(),
    0xFF6B7280.toInt(),
)

/** 桌面歌词可选颜色：白 / 黄 / 青 / 淡粉，均保证深色胶囊上的可读性。 */
private val DESKTOP_LYRIC_COLORS: List<Int> = listOf(
    0xFFFFFFFF.toInt(),
    0xFFFFD54F.toInt(),
    0xFF80DEEA.toInt(),
    0xFFFF9EC7.toInt(),
)

private val WALLPAPER_AVAILABLE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun SettingsScreen(
    showTranslation: Boolean,
    showRomaji: Boolean,
    fallbackBaseUrl: String,
    lyricCacheCount: Int,
    themeSpec: ThemeSpec,
    desktopLyricEnabled: Boolean,
    desktopLyricFontSp: Int,
    desktopLyricColorArgb: Int,
    onToggleTranslation: (Boolean) -> Unit,
    onToggleRomaji: (Boolean) -> Unit,
    onSaveFallbackUrl: (String) -> Unit,
    onClearLyricCache: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onSeedSource: (SeedSource) -> Unit,
    onFixedSeedArgb: (Int) -> Unit,
    onSeparatePlayerTheme: (Boolean) -> Unit,
    onPlayerSeedSource: (SeedSource) -> Unit,
    onPlayerFixedSeedArgb: (Int) -> Unit,
    onDesktopLyricEnabled: (Boolean) -> Unit,
    onDesktopLyricFontSp: (Int) -> Unit,
    onDesktopLyricColorArgb: (Int) -> Unit,
    backgroundLabel: String,
    onPickBackgroundImage: () -> Unit,
    onPickBackgroundVideo: () -> Unit,
    onResetBackground: () -> Unit,
    logText: () -> String,
    onClearLog: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var urlDraft by remember(fallbackBaseUrl) { mutableStateOf(fallbackBaseUrl) }
    var showLog by remember { mutableStateOf(false) }
    var showGlassDemo by remember { mutableStateOf(false) }

    // 页面自有玻璃背景源：不透明 surface 底遮住下层页面（防叠字），同时供分组卡片采样
    val backdrop = rememberGlassBackdrop()

    Box(modifier = modifier.fillMaxSize()) {
        // 背景源层：只放不透明底色（玻璃卡片不放进这里，避免自采样鬼影）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassBackdropSource(backdrop)
                .background(MaterialTheme.colorScheme.surface),
        )
        // 内容层：每个分组包进一块玻璃卡片（采样纯色底，视觉是干净的毛玻璃块）
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            GlassSection(backdrop = backdrop) {
                Text("外观", style = MaterialTheme.typography.titleMedium)

                Text(
                    "主题模式",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    ChoiceChip("跟随系统", themeSpec.mode == ThemeMode.System) { onThemeMode(ThemeMode.System) }
                    ChoiceChip("浅色", themeSpec.mode == ThemeMode.Light) { onThemeMode(ThemeMode.Light) }
                    ChoiceChip("深色", themeSpec.mode == ThemeMode.Dark) { onThemeMode(ThemeMode.Dark) }
                }

                Text(
                    "取色来源",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    ChoiceChip("封面", themeSpec.seedSource == SeedSource.Cover) { onSeedSource(SeedSource.Cover) }
                    ChoiceChip(
                        label = "壁纸",
                        selected = themeSpec.seedSource == SeedSource.Wallpaper,
                        enabled = WALLPAPER_AVAILABLE,
                    ) { onSeedSource(SeedSource.Wallpaper) }
                    ChoiceChip("固定色", themeSpec.seedSource == SeedSource.Fixed) { onSeedSource(SeedSource.Fixed) }
                }
                if (!WALLPAPER_AVAILABLE) {
                    Text(
                        "壁纸取色需要 Android 12 及以上系统，当前设备不可用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Text(
                    "固定色",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                PaletteRow(
                    selectedArgb = themeSpec.fixedSeedArgb,
                    onSelect = onFixedSeedArgb,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("播放页独立主题", modifier = Modifier.weight(1f))
                    Switch(
                        checked = themeSpec.separatePlayerTheme,
                        onCheckedChange = onSeparatePlayerTheme,
                    )
                }

                if (themeSpec.separatePlayerTheme) {
                    Text(
                        "播放页取色来源",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        ChoiceChip("封面", themeSpec.playerSeedSource == SeedSource.Cover) {
                            onPlayerSeedSource(SeedSource.Cover)
                        }
                        ChoiceChip(
                            label = "壁纸",
                            selected = themeSpec.playerSeedSource == SeedSource.Wallpaper,
                            enabled = WALLPAPER_AVAILABLE,
                        ) { onPlayerSeedSource(SeedSource.Wallpaper) }
                        ChoiceChip("固定色", themeSpec.playerSeedSource == SeedSource.Fixed) {
                            onPlayerSeedSource(SeedSource.Fixed)
                        }
                    }
                    Text(
                        "播放页固定色",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    PaletteRow(
                        selectedArgb = themeSpec.playerFixedSeedArgb,
                        onSelect = onPlayerFixedSeedArgb,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            GlassSection(backdrop = backdrop) {
                Text("液态玻璃", style = MaterialTheme.typography.titleMedium)
                Text(
                    "基于官方库 Kyant0/Backdrop：折射主导、6dp 辅助模糊、8% 白色着色、边缘色散；" +
                        "低版本系统自动降级为渐变伪玻璃。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Button(
                    onClick = { showGlassDemo = true },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("打开纹理验收页") }
            }

            Spacer(modifier = Modifier.height(12.dp))

            GlassSection(backdrop = backdrop) {
                Text("背景", style = MaterialTheme.typography.titleMedium)
                Text(
                    "仅支持本机素材（相册中的图片或视频），不提供官方素材库 / 在线 MV；选中的素材会复制到应用内保存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    backgroundLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = onPickBackgroundImage) { Text("选择背景图片") }
                    Button(onClick = onPickBackgroundVideo) { Text("选择背景视频") }
                }
                TextButton(onClick = onResetBackground, modifier = Modifier.padding(top = 4.dp)) {
                    Text("恢复默认")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            GlassSection(backdrop = backdrop) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("歌词译文默认显示", modifier = Modifier.weight(1f))
                    Switch(checked = showTranslation, onCheckedChange = onToggleTranslation)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("歌词罗马音默认显示", modifier = Modifier.weight(1f))
                    Switch(checked = showRomaji, onCheckedChange = onToggleRomaji)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            GlassSection(backdrop = backdrop) {
                Text("桌面歌词", style = MaterialTheme.typography.titleMedium)
                Text(
                    "在系统悬浮窗中显示当前歌词，可拖动到任意位置。首次开启需要授予「显示在其他应用上层」权限。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("启用桌面歌词", modifier = Modifier.weight(1f))
                    Switch(checked = desktopLyricEnabled, onCheckedChange = onDesktopLyricEnabled)
                }
                Text(
                    "字号",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    ChoiceChip("小", desktopLyricFontSp == 16) { onDesktopLyricFontSp(16) }
                    ChoiceChip("中", desktopLyricFontSp == 20) { onDesktopLyricFontSp(20) }
                    ChoiceChip("大", desktopLyricFontSp == 24) { onDesktopLyricFontSp(24) }
                }
                Text(
                    "颜色",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                PaletteRow(
                    selectedArgb = desktopLyricColorArgb,
                    onSelect = onDesktopLyricColorArgb,
                    modifier = Modifier.padding(top = 4.dp),
                    colors = DESKTOP_LYRIC_COLORS,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            GlassSection(backdrop = backdrop) {
                Text("备用音源地址", style = MaterialTheme.typography.titleMedium)
                Text(
                    "默认留空。仅在端上签名路径临时失效时作为应急兜底，不参与正常流程。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                OutlinedTextField(
                    value = urlDraft,
                    onValueChange = { urlDraft = it },
                    singleLine = true,
                    label = { Text("例如 https://example.com") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onSaveFallbackUrl(urlDraft) },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("保存地址") }
            }

            Spacer(modifier = Modifier.height(12.dp))

            GlassSection(backdrop = backdrop) {
                Text("歌词缓存：$lyricCacheCount 首")
                Button(onClick = onClearLyricCache, modifier = Modifier.padding(top = 8.dp)) { Text("清空歌词缓存") }
            }

            Spacer(modifier = Modifier.height(12.dp))

            GlassSection(backdrop = backdrop) {
                Text("错误日志", style = MaterialTheme.typography.titleMedium)
                Text(
                    "签名 / 解析异常等本地技术日志，仅用于排查，不影响使用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    TextButton(onClick = { showLog = true }) { Text("查看") }
                    TextButton(onClick = onClearLog) { Text("清空") }
                }
            }

            TextButton(onClick = onBack, modifier = Modifier.padding(top = 24.dp)) { Text("返回") }
        }
    }

    // 全屏纹理验收页：玻璃下方是图标网格 + 文字卡片 + 噪点图片，专门用于观察折射与色散
    if (showGlassDemo) {
        Dialog(
            onDismissRequest = { showGlassDemo = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF14141A))) {
                LiquidGlassPreviewContent()
                TextButton(
                    onClick = { showGlassDemo = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 12.dp),
                ) {
                    Text("关闭", color = Color.White)
                }
            }
        }
    }

    if (showLog) {
        AlertDialog(
            onDismissRequest = { showLog = false },
            confirmButton = { TextButton(onClick = { showLog = false }) { Text("关闭") } },
            title = { Text("错误日志") },
            text = {
                Text(
                    text = logText().ifBlank { "暂无日志" },
                    style = MaterialTheme.typography.bodySmall,
                )
            },
        )
    }
}

/**
 * 设置页分组玻璃卡片：采样页面不透明底色的毛玻璃块（页面内容不穿卡片，视觉干净）。
 * 内边距 16dp，与旧分组内容的留白习惯一致。
 */
@Composable
private fun GlassSection(
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop = backdrop,
                shape = RoundedCornerShape(28.dp),
                blurRadius = 6.dp,
                tint = Color.White.copy(alpha = 0.08f),
            )
            .padding(16.dp),
        content = content,
    )
}

/** 单个可选项胶囊。选中高亮，禁用置灰。 */
@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val container = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        selected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = container,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .padding(end = 8.dp)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            text = label,
            color = content,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/** 调色板等分铺满一行，选中项加粗描边。[colors] 默认 8 个主题预设色。 */
@Composable
private fun PaletteRow(
    selectedArgb: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    colors: List<Int> = PRESET_COLORS,
) {
    val outlineColor = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        colors.forEach { argb ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(
                        width = if (argb == selectedArgb) 3.dp else 1.dp,
                        color = if (argb == selectedArgb) outlineColor
                        else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(argb) },
            )
        }
    }
}
