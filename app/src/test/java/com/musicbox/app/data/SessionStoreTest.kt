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
        SessionStore(kv).save("MUSIC_U_VALUE", "csrf-123", "阿怪", 42L)

        val reopened = SessionStore(kv)
        assertTrue(reopened.session.value.isLoggedIn)
        assertEquals("MUSIC_U_VALUE", reopened.session.value.musicU)
        assertEquals("csrf-123", reopened.session.value.csrf)
        assertEquals("阿怪", reopened.session.value.nickname)
        assertEquals(42L, reopened.session.value.userId)
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
    fun `updateProfile keeps login state and fills uid`() {
        val store = SessionStore(InMemoryKeyValueStore())
        store.save("u", "c", null)
        store.updateProfile("新昵称", 7L)
        assertEquals("新昵称", store.session.value.nickname)
        assertEquals(7L, store.session.value.userId)
        assertTrue(store.session.value.isLoggedIn)
    }

    @Test
    fun `updateProfile without nickname keeps existing nickname`() {
        val store = SessionStore(InMemoryKeyValueStore())
        store.save("u", "c", "旧昵称")
        store.updateProfile(null, 9L)
        assertEquals("旧昵称", store.session.value.nickname)
        assertEquals(9L, store.session.value.userId)
    }

    @Test
    fun `updateProfile persists avatar and listen songs`() {
        val kv = InMemoryKeyValueStore()
        val store = SessionStore(kv)
        store.save("u", "c", "阿怪", 42L)
        store.updateProfile("阿怪", 42L, avatarUrl = "https://cdn/avatar.jpg", listenSongs = 1234)

        val reopened = SessionStore(kv).session.value
        assertEquals("https://cdn/avatar.jpg", reopened.avatarUrl)
        assertEquals(1234, reopened.listenSongs)
        assertEquals(42L, reopened.userId)
    }

    @Test
    fun `clear also drops avatar and listen songs`() {
        val kv = InMemoryKeyValueStore()
        val store = SessionStore(kv)
        store.save("u", "c", "阿怪", 42L)
        store.updateProfile("阿怪", 42L, avatarUrl = "a.jpg", listenSongs = 99)
        store.clear()

        val reopened = SessionStore(kv).session.value
        assertEquals(null, reopened.avatarUrl)
        assertEquals(0, reopened.listenSongs)
    }
}
