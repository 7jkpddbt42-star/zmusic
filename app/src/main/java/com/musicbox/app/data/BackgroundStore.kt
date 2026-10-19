package com.musicbox.app.data

import android.content.Context
import android.net.Uri
import java.io.File

/** 播放页背景类型：跟随封面模糊 / 自选本机图片 / 自选本机视频。 */
enum class BackgroundType(val key: String) {
    Cover("cover"),
    Image("image"),
    Video("video"),
    ;

    companion object {
        fun fromKey(key: String?): BackgroundType = entries.firstOrNull { it.key == key } ?: Cover
    }
}

/**
 * 背景偏好快照。纯数据、无 Android 依赖，可在 JVM 单测里断言。
 *
 * [path] 为导入后落在应用私有目录的稳定路径；[type] 为 [BackgroundType.Cover] 时为 null。
 */
data class BackgroundSpec(
    val type: BackgroundType = BackgroundType.Cover,
    val path: String? = null,
) {
    companion object {
        /** 默认：跟随封面模糊。 */
        val COVER = BackgroundSpec()
    }
}

/**
 * 本机背景素材仓库。
 *
 * 用户从系统相册/文件选择器选中的素材会被**复制到应用私有目录**
 * （`filesDir/background`），避免相册 URI 授权在重启后失效。目录内文件名固定为
 * `bg_image.<ext>` / `bg_video.<ext>`，重新选择时先清掉同前缀旧文件。
 *
 * 所有对外方法都不抛异常：失败（无效 uri / 流为 null / IO 异常）一律返回 null 或静默。
 */
class BackgroundStore(private val context: Context) {

    private val dir: File get() = File(context.filesDir, DIR_NAME)

    /** 复制图片到私有目录，返回稳定路径；失败返回 null。 */
    fun importImage(uri: Uri): String? = import(uri, FILE_IMAGE, "jpg")

    /** 复制视频到私有目录，返回稳定路径；失败返回 null。 */
    fun importVideo(uri: Uri): String? = import(uri, FILE_VIDEO, "mp4")

    /** 删除 `background` 目录下所有素材（用于「恢复默认」）。失败静默。 */
    fun clearAll() {
        try {
            dir.listFiles()?.forEach { it.delete() }
        } catch (_: Throwable) {
            // 清理失败不阻塞 UI
        }
    }

    private fun import(uri: Uri, filePrefix: String, fallbackExt: String): String? {
        return try {
            val mime = context.contentResolver.getType(uri)
            val ext = extensionFor(mime, fallbackExt)
            // 先打开输入流：流为 null 直接失败，不误删既有背景
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val directory = ensureDir() ?: return null
            val target = File(directory, "$filePrefix.$ext")
            // 打开成功后再清掉同前缀旧文件（含不同扩展名），避免残留
            directory.listFiles()?.forEach { if (it.name.startsWith("$filePrefix.")) it.delete() }
            input.use { source -> target.outputStream().use { sink -> source.copyTo(sink) } }
            target.absolutePath
        } catch (_: Throwable) {
            null
        }
    }

    private fun ensureDir(): File? = try {
        if (!dir.exists()) dir.mkdirs()
        if (dir.isDirectory) dir else null
    } catch (_: Throwable) {
        null
    }

    companion object {
        private const val DIR_NAME = "background"
        private const val FILE_IMAGE = "bg_image"
        private const val FILE_VIDEO = "bg_video"

        /**
         * MIME → 文件扩展名。无法识别或为 null 时返回 [fallbackExt]。
         * 纯函数，便于 JVM 单测。
         */
        fun extensionFor(mimeType: String?, fallbackExt: String): String = when (mimeType?.lowercase()) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "video/mp4" -> "mp4"
            "video/webm" -> "webm"
            "video/3gpp" -> "3gp"
            else -> fallbackExt
        }
    }
}
