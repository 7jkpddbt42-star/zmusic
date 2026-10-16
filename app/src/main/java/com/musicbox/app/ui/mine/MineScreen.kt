package com.musicbox.app.ui.mine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musicbox.app.data.NetEaseSession

@Composable
fun MineScreen(
    session: NetEaseSession,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onOpenSettings: () -> Unit,
    favoriteCount: Int,
    historyCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(
            text = if (session.isLoggedIn) (session.nickname ?: "已登录网易云") else "游客模式",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = if (session.isLoggedIn) {
                "已解锁完整播放与更高音质"
            } else {
                "未登录仅可试听部分歌曲，登录完全可选。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        if (session.isLoggedIn) {
            Button(onClick = onLogout) { Text("退出登录") }
        } else {
            Button(onClick = onLogin) { Text("扫码登录") }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

        Text("收藏 $favoriteCount 首")
        Text("最近播放 $historyCount 首", modifier = Modifier.padding(top = 4.dp))

        HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

        Button(onClick = onOpenSettings) { Text("设置") }
    }
}
