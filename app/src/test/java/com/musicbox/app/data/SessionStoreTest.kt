package com.musicbox.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStoreTest {

    @Test
    fun `starts as guest`() {
        val store = SessionStore(InMemoryKeyValueStore())
        assertFalse(store.session.value.isLoggedIn)
        assertEquals(null, store.session.value.musicU)
    }

    @Test
    fun `save then read back`() {
        val kv = InMemoryKeyValueStore()
        SessionStore(kv).save("MUSIC_U_VALUE", "csrf-123", "阿怪")

        val reopened = SessionStore(kv)
        assertTrue(reopened.session.value.isLoggedIn)
        assertEquals("MUSIC_U_VALUE", reopened.session.value.musicU)
        assertEquals("csrf-123", reopened.session.value.csrf)
        assertEquals("阿怪", reopened.session.value.nickname)
    }

    @Test
    fun `clear returns to guest`() {
        val kv = InMemoryKeyValueStore()
        val store = SessionStore(kv)
        store.save("u", "c", "n")
        store.clear()
        assertFalse(store.session.value.isLoggedIn)
        assertFalse(SessionStore(kv).session.value.isLoggedIn)
    }

    @Test
    fun `updateNickname keeps login state`() {
        val store = SessionStore(InMemoryKeyValueStore())
        store.save("u", "c", null)
        store.updateNickname("新昵称")
        assertEquals("新昵称", store.session.value.nickname)
        assertTrue(store.session.value.isLoggedIn)
    }
}
