package com.musicbox.app.data

import java.io.File
import java.time.Instant

/**
 * 本地滚动日志文件。用途（设计文档 §9）：签名 / 解析异常这类"不该让用户看到"
 * 的技术错误，写进文件供排查；用户侧只看到友好文案。
 *
 * 滚动策略：文件超过 [maxBytes] 时，保留尾部一半内容重写，避免无限增长。
 */
class AppLog(
    private val file: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun log(tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = Instant.ofEpochMilli(clock()).toString()
        val detail = throwable?.let { " | ${it.javaClass.simpleName}: ${it.message}" }.orEmpty()
        append("$timestamp [$tag] $message$detail")
    }

    fun readAll(): String = if (file.exists()) file.readText() else ""

    fun clear() {
        if (file.exists()) file.delete()
    }

    private fun append(line: String) {
        file.parentFile?.mkdirs()
        rollIfNeeded()
        file.appendText(line + "\n")
    }

    private fun rollIfNeeded() {
        if (!file.exists() || file.length() <= maxBytes) return
        val tail = file.readText().takeLast((maxBytes / 2).toInt())
        file.writeText(tail)
    }

    companion object {
        const val DEFAULT_MAX_BYTES: Long = 256 * 1024

        fun forDir(dir: File): AppLog = AppLog(File(dir, "musicbox.log"))
    }
}
