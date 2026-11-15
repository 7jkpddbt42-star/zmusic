package com.musicbox.app.ui.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.musicbox.app.data.AudioQuality
import com.musicbox.app.data.BackgroundBlur
import com.musicbox.app.data.BackgroundSpec
import com.musicbox.app.data.LyricStyle
import com.musicbox.app.data.PlayerStyle
import com.musicbox.app.player.SongDiskCache
import com.musicbox.app.ui.components.MusicIcons
import com.musicbox.app.ui.components.liquidglass.GlassAmbientBackdrop
import com.musicbox.app.ui.components.liquidglass.glassBackdropSource
import com.musicbox.app.ui.components.liquidglass.liquidGlass
import com.musicbox.app.ui.components.liquidglass.rememberGlassBackdrop
import com.musicbox.app.ui.theme.SeedSource
import com.musicbox.app.ui.theme.ThemeMode
import com.musicbox.app.ui.theme.ThemeSpec
import java.util.Locale
import kotlin.math.roundToInt

/** 固定色色板预设（8 个）：一组冷灰蓝到墨蓝的色阶。
 *  主题本身会把所有种子冷化，这里若仍摆彩虹色，用户点了紫色却得到蓝灰会困惑，
 *  所以预设直接收在冷色范围内，只体现深浅与冷暖的细微差别。 */
private val PRESET_COLORS: List<Int> = listOf(
    0xFF8FA9C2.toInt(),
    0xFF7B96B2.toInt(),
    0xFF6E8CA8.toInt(),
    0xFF5F7F8C.toInt(),
    0xFF566F87.toInt(),
    0xFF4C6478.toInt(),
    0xFF3F566E.toInt(),
    0xFF6B7A88.toInt(),
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
    smartTransition: Boolean,
    audioFxEnabled: Boolean,
    audioFxBands: List<Int>,
    customAudioFx: List<Int>?,
    audioQuality: AudioQuality,
    lyricCacheCount: Int,
    songCacheBytes: Long,
    backupLikedCount: Int,
    backupHistoryCount: Int,
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
    onSmartTransition: (Boolean) -> Unit,
    onAudioFxEnabled: (Boolean) -> Unit,
    onAudioFxBand: (Int, Int) -> Unit,
    onAudioFxPreset: (List<Int>) -> Unit,
    onSaveCustomAudioFx: () -> Unit,
    onClearCustomAudioFx: () -> Unit,
    onAudioQuality: (AudioQuality) -> Unit,
    onClearLyricCache: () -> Unit,
    onClearSongCache: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
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
                        // 设置页文字密集，面板比别处更实：既保留玻璃的边缘折射，
                        // 又不会被背后的大图（尤其视频）透过来干扰阅读
                        tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        // 整页大面板上色散几乎不可见，却是逐像素多采样的大头，
                        // 关掉后观感几乎不变、滚动明显更轻
                        dispersion = false,
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(30.dp)),
            ) {
                // 固定顶栏：设置项再长也能随时返回，不用滚到底部找返回
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MusicIcons.ChevronLeft,
                            contentDescription = "返回",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    thickness = 1.dp,
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
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

                AnimatedVisibility(
                    visible = themeSpec.separatePlayerTheme,
                    enter = expandVertically(
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                    ) + fadeIn(tween(200)),
                    exit = shrinkVertically(
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                    ) + fadeOut(tween(140)),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
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
            }

            
            SettingsGroup {
                Text("液态玻璃", style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = onOpenGlassDemo,
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("打开纹理验收页") }
            }

            
            SettingsGroup {
                Text("背景", style = MaterialTheme.typography.titleMedium)
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

            
            SettingsGroup {
                Text("播放", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("智能过渡", modifier = Modifier.weight(1f))
                    Switch(checked = smartTransition, onCheckedChange = onSmartTransition)
                }
            }

            
            SettingsGroup {
                Text("音效", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("均衡器", modifier = Modifier.weight(1f))
                    Switch(checked = audioFxEnabled, onCheckedChange = onAudioFxEnabled)
                }
                // 展开时同时做高度与透明度过渡：只淡入会让下方内容瞬间跳位
                AnimatedVisibility(
                    visible = audioFxEnabled,
                    enter = expandVertically(
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                    ) + fadeIn(tween(200)),
                    exit = shrinkVertically(
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                    ) + fadeOut(tween(140)),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 预设：一次把五段增益填好，仍可继续手动微调
                        Text(
                            "预设",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AUDIO_FX_PRESETS.forEach { preset ->
                                ChoiceChip(
                                    label = preset.label,
                                    selected = audioFxBands == preset.bands,
                                    onClick = { onAudioFxPreset(preset.bands) },
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 自定义预设放在按钮行：四个预设已经占满一行，再加一颗会被挤窄
                            customAudioFx?.let { bands ->
                                ChoiceChip(
                                    label = "自定义",
                                    selected = audioFxBands == bands,
                                    onClick = { onAudioFxPreset(bands) },
                                )
                            }
                            TextButton(onClick = onSaveCustomAudioFx) {
                                Text(if (customAudioFx == null) "存为预设" else "更新预设")
                            }
                            if (customAudioFx != null) {
                                TextButton(onClick = onClearCustomAudioFx) { Text("删除") }
                            }
                        }
                        AUDIO_FX_BAND_LABELS.forEachIndexed { index, label ->
                            AudioFxBandRow(label, audioFxBands.getOrElse(index) { 0 }) {
                                onAudioFxBand(index, it)
                            }
                        }
                    }
                }
            }

            
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
            }

            
            SettingsGroup {
                Text("桌面歌词", style = MaterialTheme.typography.titleMedium)
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

            
            SettingsGroup {
                Text("音源", style = MaterialTheme.typography.titleMedium)
                Text(
                    "音质",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    AudioQuality.values().forEach { quality ->
                        ChoiceChip(quality.label, audioQuality == quality) {
                            onAudioQuality(quality)
                        }
                    }
                }
                Text(
                    "听歌模式",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    ChoiceChip("普通模式", !freeSourceEnabled) { onFreeSourceEnabled(false) }
                    ChoiceChip("免费听歌模式", freeSourceEnabled) { onFreeSourceEnabled(true) }
                }
            }

            
            SettingsGroup {
                Text("备用音源地址", style = MaterialTheme.typography.titleMedium)
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

            
            SettingsGroup {
                Text("缓存", style = MaterialTheme.typography.titleMedium)

                Text("歌词缓存：$lyricCacheCount 首", modifier = Modifier.padding(top = 12.dp))
                Button(onClick = onClearLyricCache, modifier = Modifier.padding(top = 8.dp)) {
                    Text("清空歌词缓存")
                }

                Text(
                    "离线音频：${formatBytes(songCacheBytes)} / ${formatBytes(SongDiskCache.MAX_BYTES)}",
                    modifier = Modifier.padding(top = 12.dp),
                )
                Button(onClick = onClearSongCache, modifier = Modifier.padding(top = 8.dp)) {
                    Text("清空离线音频")
                }
            }

            
            SettingsGroup {
                Text("备份与恢复", style = MaterialTheme.typography.titleMedium)
                Text(
                    "本机收藏 $backupLikedCount 首 · 播放记录 $backupHistoryCount 条",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = onExportBackup) { Text("导出备份") }
                    TextButton(onClick = onImportBackup) { Text("恢复备份") }
                }
            }

            
            SettingsGroup {
                Text("错误日志", style = MaterialTheme.typography.titleMedium)
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = { showLog = true }) { Text("查看") }
                    TextButton(onClick = onClearLog) { Text("清空") }
                }
            }

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

/** 字节数 → 人类可读体积，用于缓存占用展示。 */
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.US, "%d KB", bytes / 1024)
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

/** 均衡器预设：五段增益（低频 / 中低 / 中频 / 中高 / 高频），单位 dB，范围与滑杆一致（±6）。 */
private data class AudioFxPreset(val label: String, val bands: List<Int>)

/** 频段名称与 [SettingsRepository.AUDIO_FX_BAND_KEYS] 顺序一一对应。 */
private val AUDIO_FX_BAND_LABELS = listOf("低频", "中低", "中频", "中高", "高频")

private val AUDIO_FX_PRESETS = listOf(
    AudioFxPreset("平直", listOf(0, 0, 0, 0, 0)),
    AudioFxPreset("流行", listOf(2, 1, 0, 1, 2)),
    AudioFxPreset("摇滚", listOf(4, 2, -1, 2, 3)),
    AudioFxPreset("人声", listOf(0, 1, 3, 1, -1)),
)

/**
 * 单个频段滑杆：左侧名称、右侧当前增益（dB），下方整宽滑杆。
 * 步进 1 dB，取值 -6..6；拖到两端就是最大幅度的低频 / 高频增强（或衰减）。
 */
@Composable
private fun AudioFxBandRow(label: String, db: Int, onValueChange: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (db > 0) "+$db dB" else "$db dB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = db.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = -6f..6f,
            steps = 11,
            modifier = Modifier.fillMaxWidth(),
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
