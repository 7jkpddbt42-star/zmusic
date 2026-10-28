package com.musicbox.app.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.musicbox.app.data.BackgroundSpec
import com.musicbox.app.ui.components.liquidglass.GlassAmbientBackdrop
import com.musicbox.app.ui.components.liquidglass.glassBackdropSource
import com.musicbox.app.ui.components.liquidglass.liquidGlass
import com.musicbox.app.ui.components.liquidglass.rememberGlassBackdrop

/**
 * 扫码登录页。二维码**本地用 ZXing 生成位图**（纯原生，不加载任何网页）。
 * 轮询在 LoginViewModel 里每 2 秒跑一次。
 */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    background: BackgroundSpec,
    artworkUrl: String?,
    onLoggedIn: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 进入页面即开始申请二维码并轮询（幂等：已在对码/扫码中不会重复申请）
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.start() }

    androidx.compose.runtime.LaunchedEffect(state) {
        if (state is LoginUiState.Success) onLoggedIn()
    }

    // 页面自有玻璃背景源：真实背景（视频/图片/封面）+ 遮罩，供二维码卡片逐帧采样
    val backdrop = rememberGlassBackdrop()

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景源层：真实背景（二维码卡片不放进这里，避免自采样鬼影）；
        // 纯色底会让模糊不可见，所以改用真实背景
        GlassAmbientBackdrop(
            background = background,
            artworkUrl = artworkUrl,
            modifier = Modifier
                .fillMaxSize()
                .glassBackdropSource(backdrop),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("扫码登录网易云", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "登录后可同步歌单、喜欢与每日推荐；会员歌曲仍需网易云会员权限。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )

            when (val s = state) {
                is LoginUiState.Loading, LoginUiState.Idle -> CircularProgressIndicator()
                is LoginUiState.Waiting -> {
                    if (s.notice != null) {
                        Text(
                            text = s.notice,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    // 二维码卡片：整块玻璃；二维码位图自带白底、画在玻璃层之上不受染色
                    Box(
                        modifier = Modifier.liquidGlass(
                            backdrop = backdrop,
                            shape = RoundedCornerShape(28.dp),
                            blurRadius = 6.dp,
                            tint = Color.White.copy(alpha = 0.08f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            QrImage(s.qrContent)
                            Text("打开网易云音乐 App → 扫一扫", modifier = Modifier.padding(top = 16.dp))
                        }
                    }
                }
                LoginUiState.Scanned -> {
                    CircularProgressIndicator()
                    Text("已扫码，请在手机上确认", modifier = Modifier.padding(top = 16.dp))
                }
                LoginUiState.Expired -> {
                    Text("二维码已过期")
                    TextButton(onClick = viewModel::refresh) { Text("刷新二维码") }
                }
                is LoginUiState.Failed -> {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::refresh) { Text("重试") }
                }
                is LoginUiState.Success -> Text("登录成功")
            }

            TextButton(onClick = onBack, modifier = Modifier.padding(top = 24.dp)) { Text("返回") }
        }
    }
}

@Composable
private fun QrImage(content: String) {
    val bitmap = remember(content) { encodeQr(content) }
    Box(modifier = Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        if (bitmap == null) {
            Text("二维码生成失败", color = MaterialTheme.colorScheme.error)
        } else {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = "登录二维码")
        }
    }
}

/**
 * 用 ZXing 把文本编码成二维码位图。纯 CPU，不联网。
 * 尺寸取 640px，够任何屏幕清晰显示。
 */
private fun encodeQr(content: String): android.graphics.Bitmap? = runCatching {
    val size = 640
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1,
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) BLACK else WHITE
        }
    }
    android.graphics.Bitmap.createBitmap(pixels, size, size, android.graphics.Bitmap.Config.ARGB_8888)
}.getOrNull()

private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
