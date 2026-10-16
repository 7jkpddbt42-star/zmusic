package com.musicbox.app.data

import com.google.gson.Gson
import java.io.File
import java.lang.reflect.Type

/**
 * 泛型 JSON 文件存储。原子写：先写 `<name>.tmp`，再 rename 覆盖目标，
 * 避免"写到一半进程被杀"留下半个损坏文件。
 *
 * 读失败（文件不存在 / 内容损坏）一律返回 null，由调用方当作"空"处理。
 * 纯 JVM 实现，单测可注入临时目录的 File。
 */
class JsonFileStore<T : Any>(
    private val file: File,
    private val type: Type,
    private val gson: Gson = Gson(),
) {

    fun read(): T? {
        if (!file.exists()) return null
        return try {
            gson.fromJson(file.readText(), type)
        } catch (e: Exception) {
            null
        }
    }

    fun write(value: T) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + TMP_SUFFIX)
        tmp.writeText(gson.toJson(value, type))
        if (file.exists() && !file.delete()) {
            // 删不掉就直接覆盖（copyTo overwrite 会先清空），不做静默失败
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
            return
        }
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    fun delete() {
        if (file.exists()) file.delete()
    }

    fun exists(): Boolean = file.exists()

    private companion object {
        const val TMP_SUFFIX = ".tmp"
    }
}
