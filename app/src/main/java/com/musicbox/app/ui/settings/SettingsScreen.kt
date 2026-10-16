package com.musicbox.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    showTranslation: Boolean,
    showRomaji: Boolean,
    fallbackBaseUrl: String,
    lyricCacheCount: Int,
    onToggleTranslation: (Boolean) -> Unit,
    onToggleRomaji: (Boolean) -> Unit,
    onSaveFallbackUrl: (String) -> Unit,
    onClearLyricCache: () -> Unit,
    logText: () -> String,
    onClearLog: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var urlDraft by remember(fallbackBaseUrl) { mutableStateOf(fallbackBaseUrl) }
    var showLog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("歌词译文默认显示", modifier = Modifier.weight(1f))
            Switch(checked = showTranslation, onCheckedChange = onToggleTranslation)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("歌词罗马音默认显示", modifier = Modifier.weight(1f))
            Switch(checked = showRomaji, onCheckedChange = onToggleRomaji)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

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

        HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

        Text("歌词缓存：$lyricCacheCount 首")
        Button(onClick = onClearLyricCache, modifier = Modifier.padding(top = 8.dp)) { Text("清空歌词缓存") }

        HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

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

        TextButton(onClick = onBack, modifier = Modifier.padding(top = 24.dp)) { Text("返回") }
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
