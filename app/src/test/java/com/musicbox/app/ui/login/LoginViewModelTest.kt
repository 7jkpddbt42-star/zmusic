package com.musicbox.app.ui.login

import com.musicbox.app.data.InMemoryKeyValueStore
import com.musicbox.app.data.SessionStore
import com.musicbox.app.source.netease.LoginApi
import com.musicbox.app.source.netease.QrPollResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    /** 可脚本化的假登录接口。 */
    private class FakeLoginApi(
        private val keys: MutableList<Result<String>> = mutableListOf(),
        private val polls: MutableList<Result<QrPollResult>> = mutableListOf(),
        var nickname: Result<String?> = Result.success(null),
    ) : LoginApi {
        override fun createQrKey(): Result<String> =
            if (keys.isEmpty()) Result.failure(IllegalStateException("no key")) else keys.removeFirst()
        override fun poll(unikey: String): Result<QrPollResult> =
            if (polls.isEmpty()) Result.success(QrPollResult.Waiting) else polls.removeFirst()
        override fun fetchNickname(musicU: String, csrf: String): Result<String?> = nickname
    }

    private val fakeError = Result.failure<QrPollResult>(IllegalStateException("网络不可用"))

    @Test
    fun `start exposes waiting state with qr content`() = runTest(dispatcher) {
        val api = FakeLoginApi(keys = mutableListOf(Result.success("KEY-1")))
        val store = SessionStore(InMemoryKeyValueStore())
        val vm = LoginViewModel(api, store, pollIntervalMs = 2_000L)

        vm.start()
        advanceTimeBy(1) // 让 createQrKey 的协程跑起来

        assertEquals(
            LoginUiState.Waiting("https://music.163.com/login?codekey=KEY-1"),
            vm.state.value,
        )
        vm.reset() // 停掉轮询，避免测试结束时仍有挂起的轮询协程
    }

    @Test
    fun `poll success saves session`() = runTest(dispatcher) {
        val api = FakeLoginApi(
            keys = mutableListOf(Result.success("KEY-1")),
            polls = mutableListOf(Result.success(QrPollResult.Success("U", "C"))),
            nickname = Result.success("小明"),
        )
        val store = SessionStore(InMemoryKeyValueStore())
        val vm = LoginViewModel(api, store, pollIntervalMs = 2_000L)

        vm.start()
        advanceTimeBy(2_100) // 越过一次轮询间隔
        advanceTimeBy(2_100)

        assertTrue(store.session.value.isLoggedIn)
        assertEquals("U", store.session.value.musicU)
        assertEquals("小明", store.session.value.nickname)
        assertEquals(LoginUiState.Success("小明"), vm.state.value)
    }

    @Test
    fun `expired qr requests a new key`() = runTest(dispatcher) {
        val api = FakeLoginApi(
            keys = mutableListOf(Result.success("K1"), Result.success("K2")),
            polls = mutableListOf(Result.success(QrPollResult.Expired)),
        )
        val vm = LoginViewModel(api, SessionStore(InMemoryKeyValueStore()), pollIntervalMs = 2_000L)

        vm.start()
        advanceTimeBy(2_100) // 轮询 → Expired → 重取 key
        advanceTimeBy(2_100)

        assertEquals(
            LoginUiState.Waiting("https://music.163.com/login?codekey=K2"),
            vm.state.value,
        )
        vm.reset() // 停掉轮询，避免测试结束时仍有挂起的轮询协程
    }

    @Test
    fun `network failure during poll keeps waiting`() = runTest(dispatcher) {
        val api = FakeLoginApi(
            keys = mutableListOf(Result.success("K1")),
            polls = mutableListOf(fakeError, Result.success(QrPollResult.Waiting)),
        )
        val vm = LoginViewModel(api, SessionStore(InMemoryKeyValueStore()), pollIntervalMs = 2_000L)

        vm.start()
        advanceTimeBy(2_100)
        assertEquals(
            LoginUiState.Waiting("https://music.163.com/login?codekey=K1"),
            vm.state.value,
        )
        vm.reset() // 停掉轮询，避免测试结束时仍有挂起的轮询协程
    }
}
