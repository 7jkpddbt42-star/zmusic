package com.musicbox.app.data

import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JsonFileStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val type = object : TypeToken<List<String>>() {}.type

    @Test
    fun `read returns null when file does not exist`() {
        val store = JsonFileStore<List<String>>(File(tmp.root, "nope.json"), type)
        assertNull(store.read())
    }

    @Test
    fun `write then read round trips`() {
        val store = JsonFileStore<List<String>>(File(tmp.root, "list.json"), type)
        store.write(listOf("a", "b"))
        assertEquals(listOf("a", "b"), store.read())
    }

    @Test
    fun `write replaces previous content atomically`() {
        val file = File(tmp.root, "list.json")
        val store = JsonFileStore<List<String>>(file, type)
        store.write(listOf("a"))
        store.write(listOf("c", "d"))
        assertEquals(listOf("c", "d"), store.read())
        // 不留临时文件
        assertTrue(tmp.root.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun `read returns null when content is corrupt`() {
        val file = File(tmp.root, "broken.json")
        file.writeText("{ not json ")
        val store = JsonFileStore<List<String>>(file, type)
        assertNull(store.read())
    }
}
