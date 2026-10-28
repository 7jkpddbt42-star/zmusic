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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import com.musicbox.app.data.BackgroundBlur
import com.musicbox.app.data.BackgroundSpec
import com.musicbox.app.data.LyricStyle
import com.musicbox.app.data.PlayerStyle
import com.musicbox.app.ui.components.liquidglass.GlassAmbientBackdrop
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
    lyricStyle: LyricStyle,
    playerStyle: PlayerStyle,
    fallbackBaseUrl: String,
    freeSourceEnabled: Boolean,
    lyricCacheCount: Int,
    themeSpec: ThemeSpec,
    background: BackgroundSpec,
    backgroundBlur: BackgroundBlur,
    artworkUrl: String?,
    desktopLyricEnabled: Boolean,
    desktopLyricFontSp: Int,
    desktopLyricColorArgb: Int,
    onToggleTranslation: (Boolean) -> Unit,
    onToggleRomaji: (Boolean) -> Unit,
    onLyricStyle: (LyricStyle) -> Unit,
    onPlayerStyle: (PlayerStyle) -> Unit,
    onSaveFallbackUrl: (String) -> Unit,
    onFreeSourceEnabled: (Boolean) -> Unit,
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
    onBackgroundBlur: (BackgroundBlur) -> Unit,
    onPickBackgroundImage: () -> Unit,
    onPickBackgroundVideo: () -> Unit,
    onResetBackground: () -> Unit,
    logText: () -> String,
    onClearLog: () -> Unit,
    onBack: () -> Unit,
    onOpenGlassDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var urlDraft by remember(fallbackBaseUrl) { mutableStateOf(fallbackBaseUrl) }
    var showLog by remember { mutableStateOf(false) }

    // 页面自有玻璃背景源：真实背景（视频/图片/封面）+ 遮罩，供分组卡片逐帧采样
    val backdrop = rememberGlassBackdrop()

    Box(modifier = modifier.fillMaxSize()) {
        // 背景源层：只放真实背景（玻璃卡片不放进这里，避免自采样鬼影）；
        // 纯色 surface 会让模糊不可见，所以这里改用真实背景
        GlassAmbientBackdrop(
            background = background,
            artworkUrl = artworkUrl,
            modifier = Modifier
                .fillMaxSize()
                .glassBackdropSource(backdrop),
        )
        // 内容层：整页只用一块玻璃（背景层）+ 前景滚动列。
        // 每个分组各挂一块玻璃时，滚动会同时重算 8 份全幅模糊/折射，模拟器上直接掉到几帧并触发 ANR。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .liquidGlass(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(30.dp),
                        blurRadius = 6.dp,
                        tint = Color.White.copy(alpha = 0.08f),
                        // 整页大面板上色散几乎不可见，却是逐像素多采样的大头，
                        // 关掉后观感几乎不变、滚动明显更轻
                        dispersion = false,
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(30.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
            SettingsGroup {
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

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                modifier = Modifier.padding(vertical = 12.dp),
            )

            SettingsGroup {
                Text("液态玻璃", style = MaterialTheme.typography.titleMedium)
                Text(
                    "基于官方库 Kyant0/Backdrop：折射主导、6dp 辅助模糊、8% 白色着色、边缘色散；" +
                        "低版本系统自动降级为渐变伪玻璃。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Button(
                    onClick = onOpenGlassDemo,
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("打开纹理验收页") }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                modifier = Modifier.padding(vertical = 12.dp),
            )

            SettingsGroup {
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
                Text(
                    "背景模糊",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    BackgroundBlur.entries.forEach { blur ->
                        ChoiceChip(blur.label, backgroundBlur == blur) {
                            onBackgroundBlur(blur)
                        }
                    }
                }
                Text(
                    "模糊越小背景纹理越清晰；关闭后保留原图细节。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
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

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                modifier = Modifier.padding(vertical = 12.dp),
            )

            SettingsGroup {
                Text("歌词", style = MaterialTheme.typography.titleMedium)

                Text(
                    "歌词样式",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    ChoiceChip("聚焦", lyricStyle == LyricStyle.Focus) { onLyricStyle(LyricStyle.Focus) }
                    ChoiceChip("经典", lyricStyle == LyricStyle.Classic) { onLyricStyle(LyricStyle.Classic) }
                }
                Text(
                    "聚焦：当前行居中大字，其余行缩小淡出；经典：统一字号列表。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("歌词译文默认显示", modifier = Modifier.weight(1f))
                    Switch(checked = showTranslation, onCheckedChange = onToggleTranslation)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("歌词罗马音默认显示", modifier = Modifier.weight(1f))
                    Switch(checked = showRomaji, onCheckedChange = onToggleRomaji)
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                modifier = Modifier.padding(vertical = 12.dp),
            )

            SettingsGroup {
                Text("播放页", style = MaterialTheme.typography.titleMedium)
                Text(
                    "播放页风格",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    PlayerStyle.values().toList().chunked(3).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            row.forEach { style ->
                                ChoiceChip(style.label, playerStyle == style) {
                                    onPlayerStyle(style)
                                }
                            }
                        }
                    }
                }
                Text(
                    text = playerStyle.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "切换立即生效，不会中断播放或丢失进度；也可以在播放页点「更多」快速切换。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                modifier = Modifier.padding(vertical = 12.dp),
            )

            SettingsGroup {
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

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                modifier = Modifier.padding(vertical = 12.dp),
            )

            SettingsGroup {
                Text("音源", style = MaterialTheme.typography.titleMedium)
                Text(
                    "听歌模式",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    ChoiceChip("普通模式", !freeSourceEnabled) { onFreeSourceEnabled(false) }
                    ChoiceChip("免费听歌模式", freeSourceEnabled) { onFreeSourceEnabled(true) }
                }
                Text(
                    if (freeSourceEnabled) {
                        "当前：免费听歌模式。官方只给试听片段时，会自动到公开的第三方接口换取完整地址" +
                            "（音质通常 128kbps）；第三方地址并非官方授权，个别歌曲仍可能拿不到完整版。"
                    } else {
                        "当前：普通模式。只使用网易云官方音源，VIP / 付费歌曲仅提供试听片段。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "随时可切换，切换后当前歌曲会立即按新模式重新取流，进度会尽量保持；" +
                        "若该曲在新模式下只有试听片段，超出部分无法继续。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                modifier = Modifier.padding(vertical = 12.dp),
            )

            SettingsGroup {
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

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                modifier = Modifier.padding(vertical = 12.dp),
            )

            SettingsGroup {
                Text("歌词缓存：$lyricCacheCount 首")
                Button(onClick = onClearLyricCache, modifier = Modifier.padding(top = 8.dp)) { Text("清空歌词缓存") }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                modifier = Modifier.padding(vertical = 12.dp),
            )

            SettingsGroup {
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
 * 设置页分组：整页共用外层那一块玻璃，分组本身只排内容，
 * 不再各自挂 drawBackdrop（滚动时只有一块玻璃参与采样/折射）。
 */
@Composable
private fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), content = content)
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
