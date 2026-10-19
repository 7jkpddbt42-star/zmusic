package com.musicbox.app.desktoplyric

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.musicbox.app.AppContainer
import com.musicbox.app.data.SettingsRepository
import com.musicbox.app.ui.lyric.LyricBus
import com.musicbox.app.ui.lyric.LyricOverlayState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 桌面歌词悬浮窗：前台服务 + `TYPE_APPLICATION_OVERLAY` 窗口。
 *
 * 职责边界（只做三件事）：
 * 1. **画**：代码构建一个胶囊（深色圆角底 + 两行 TextView：当前行 / 下一行），
 *    不使用 ComposeView —— 原生 View 没有 ViewTreeLifecycleOwner 的坑。
 * 2. **拖**：`onTouch` 更新 `WindowManager.LayoutParams` 坐标，抬手时持久化。
 * 3. **订阅**：collect [LyricBus] 的**行级**状态并更新文本，行变化做淡入淡出。
 *
 * 歌词计算一律在 [com.musicbox.app.ui.lyric.LyricViewModel] 侧完成，本服务不碰时间轴。
 */
class DesktopLyricService : Service() {

    private lateinit var container: AppContainer
    private lateinit var settings: SettingsRepository

    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private var currentLineView: TextView? = null
    private var nextLineView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var scope: CoroutineScope? = null

    /** 上一次渲染的当前行文本，用于判断是否需要淡入淡出。 */
    private var renderedLine: String = ""

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        settings = container.settings
        createChannel()
        // 未授权悬浮窗：不建视图，走"只前台通知"的降级（onStartCommand 里会自停）
        if (Settings.canDrawOverlays(this)) {
            setupOverlay()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 前台服务契约：用 startForegroundService 启动后必须在 5s 内调用 startForeground
        startForegroundCompat()

        if (overlayView == null) {
            // 悬浮窗未能建立（未授权或建视图失败）：移除通知并自行停止
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        overlayView?.let { view ->
            // 视图可能因异常从未 attach，removeView 失败不能影响服务退出
            try {
                windowManager?.removeView(view)
            } catch (_: IllegalArgumentException) {
            }
        }
        overlayView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------- 通知 ----------

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "桌面歌词",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("桌面歌词")
            .setContentText("歌词悬浮窗运行中")
            .setOngoing(true)
            .build()

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ---------- 悬浮视图 ----------

    private fun setupOverlay() {
        val wm = getSystemService(WindowManager::class.java) ?: return
        windowManager = wm

        val fontSp = settings.desktopLyricFontSp.value
        val color = settings.desktopLyricColorArgb.value

        val current = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSp.toFloat())
            setTextColor(color)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val next = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, (fontSp - 3).coerceAtLeast(10).toFloat())
            setTextColor(color)
            alpha = 0.55f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(CAPSULE_COLOR)
            }
            addView(current)
            addView(next)
            visibility = View.GONE
        }

        val saved = settings.desktopLyricPosition()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // 未持久化过坐标（哨兵值）时给默认位置：左对齐、屏高 1/3 处
            x = if (saved.first == POSITION_UNSET) 0 else saved.first
            y = if (saved.second == POSITION_UNSET) {
                resources.displayMetrics.heightPixels / 3
            } else {
                saved.second
            }
        }

        root.setOnTouchListener(DragListener(root, params))

        try {
            wm.addView(root, params)
        } catch (_: Throwable) {
            // 极端情况下（窗口被系统拒绝）静默降级：不留悬浮窗，服务随后自停
            return
        }

        overlayView = root
        currentLineView = current
        nextLineView = next
        layoutParams = params

        startCollecting()
    }

    private fun startCollecting() {
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        s.launch {
            LyricBus.state.collect { render(it) }
        }
    }

    /** 行级状态 → 视图。行文本变化时淡入淡出，暂停/播放不改变文本，只更新可见性。 */
    private fun render(state: LyricOverlayState) {
        val view = overlayView ?: return
        if (!state.visible) {
            view.visibility = View.GONE
            return
        }
        val lineChanged = state.currentLine != renderedLine
        currentLineView?.text = state.currentLine
        nextLineView?.text = state.nextLine

        if (view.visibility != View.VISIBLE) {
            // 从隐藏恢复：直接淡入
            view.visibility = View.VISIBLE
            view.alpha = 0f
            view.animate().alpha(1f).setDuration(FADE_MS).start()
        } else if (lineChanged) {
            view.animate().cancel()
            view.alpha = 0f
            view.animate().alpha(1f).setDuration(FADE_MS).start()
        }
        renderedLine = state.currentLine
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** 拖动：按下记录起点，移动即更新窗口坐标，抬手持久化。 */
    private inner class DragListener(
        private val view: View,
        private val params: WindowManager.LayoutParams,
    ) : View.OnTouchListener {

        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - downRawX).toInt()
                    params.y = startY + (event.rawY - downRawY).toInt()
                    windowManager?.updateViewLayout(view, params)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 落点持久化，重启后位置保持
                    settings.setDesktopLyricPosition(params.x, params.y)
                }
                else -> return false
            }
            return true
        }
    }

    private companion object {
        const val CHANNEL_ID = "desktop_lyric"
        const val NOTIFICATION_ID = 0x4C59 // "LY"
        const val FADE_MS = 180L

        /** 胶囊半透明深色底 0xCC000000（`.toInt()` 非编译期常量，只能用 val）。 */
        val CAPSULE_COLOR = 0xCC000000.toInt()

        /** 与 SettingsRepository 的坐标未设置哨兵一致（该 companion 为私有，故此处重复常量）。 */
        const val POSITION_UNSET = Int.MIN_VALUE
    }
}
