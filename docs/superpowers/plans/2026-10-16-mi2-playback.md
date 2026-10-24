# MI-2 能放歌（网易云取流 + Media3 播放 + 播放页 + 队列 + 通知栏）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 MI-1 搜到的歌真的能出声：点歌 → 在线取流 → Media3 播放 → 切后台不断 → 通知栏可控制，并对"试听片段"做显式标注。

**Architecture:** 播放的全部状态与队列都放在 Media3 原生轨道上（`PlaybackService` = `MediaSessionService` + `ExoPlayer`），UI 侧只用 `MediaController` 观察与控制，从而免费获得通知栏 / 锁屏 / 耳机线控 / 自动下一首 / 随机 / 单曲循环。流地址在端上按需解析：队列里的 `MediaItem` 用占位 URI `musicbox://song/<id>`，由 `ResolvingDataSource` 在真正打开时才向网易云换取真实地址，结果落在进程内共享短期缓存里（同一份缓存也让 UI 知道"当前这首是不是试听片段"）。

**Tech Stack:** 沿用 MI-1（Kotlin 2.4.21 · Compose BOM 2026.09.00 · AGP 9.4.1 · Gradle 9.8.1 · OkHttp 5.5.0 · Gson 2.11.0 · JUnit 4 · minSdk 26 / targetSdk 36 / compileSdk 37），新增 **AndroidX Media3 1.11.1**（`media3-exoplayer` + `media3-session`）。

---

## 0. 前置条件（必须先读）

### 0.1 项目根目录与既有状态

本计划所有路径以 **`/Users/zhuguanyu/Documents/Trae-CN/temp_20261015_01`** 为根（该目录已是独立 git 仓库）。

MI-1 已完成并验收，提交链：`a4baa1b` ← `906ff4c` ← `9cf5400` ← `98ff7c1` ← `61621c4` ← `401284a` ← `6c59a4a` ← `d5995c2`。
已有可复用资产：

| 资产 | 位置 | 复用方式 |
|---|---|---|
| eapi 签名 | `app/src/main/java/com/musicbox/app/source/netease/NetEaseCrypto.kt` | `NetEaseCrypto.eapiParams(uri, jsonText)` 直接用于取流请求 |
| header / Cookie / deviceId | `.../source/netease/NetEaseHeader.kt` | `NetEaseHeader.build(deviceId)` + `cookieHeader(header)` |
| 网络抽象 | `.../source/netease/HttpTransport.kt`（`post(url, body, headers): TransportResponse`） | 单测注入假实现 |
| 可用装配点 | `.../AppContainer.kt`（`provider: MusicProvider`） | `PlaybackService` 里 `AppContainer(applicationContext)` 复用 |
| 单测范本 | `app/src/test/java/com/musicbox/app/source/netease/NetEaseProviderTest.kt` | 照它的 `FakeTransport` 写法扩展取流测试 |

### 0.2 本机环境（已实测，不要重复排查）

| 项 | 状态 |
|---|---|
| 模拟器 AVD `musicbox` | **正在运行**，`/Users/zhuguanyu/Library/Android/sdk/platform-tools/adb devices` → `emulator-5554 device`，`sys.boot_completed=1` |
| `adb` 不在 PATH | 必须用完整路径 `/Users/zhuguanyu/Library/Android/sdk/platform-tools/adb` |
| `:app:installDebug` | **本机永久损坏**（AGP 9.4.1 的 ddmlib 缺类）→ 一律 `./gradlew :app:assembleDebug` + `adb install -r app/build/outputs/apk/debug/app-debug.apk` |
| debug 包名 / 入口 | `com.musicbox.app.debug` / `com.musicbox.app.MainActivity` |
| SDK 路径 | `local.properties` 已是 `sdk.dir=/Users/zhuguanyu/Library/Android/sdk` |

**构建 + 单测命令（本计划全程用它验证）：**

```bash
cd /Users/zhuguanyu/Documents/Trae-CN/temp_20261015_01
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

### 0.3 硬约束（用户反复强调，违反即返工）

1. 纯原生 Kotlin + Jetpack Compose，**不允许** WebView / 套壳。
2. 音源签名只在端上实现，不自建远程 API。
3. 登录完全可选，**游客必须能正常放歌**。
4. 歌词动画的逐帧时钟属于 MI-3，本计划**不要**提前实现歌词相关逻辑（`getLyric` 继续返回 `NotImplementedYet("MI-3")`）。
5. 中文文档与中文注释；每步给完整可编译代码，不留占位符。

---

## 1. 已核实的接口事实（**一手实测，不要凭记忆改动**）

以下全部通过本机对真实接口发请求得到（方法：解包 `/Applications/Folia.app/Contents/Resources/app.asar`，
读 `@neteasecloudmusicapienhanced/api` 的 `module/song_url_v1.js`、`util/request.js`、`util/config.json`、
`electron/neteaseBackend.cjs`，再用同款 eapi 签名直接请求线上）。

### 1.1 取流端点（**关键结论**）

| 项 | 值 |
|---|---|
| 方法 / 域名 | `POST https://interfacepc.music.163.com` |
| uri | `/api/song/enhance/player/url/v1` → URL 为 `/eapi/song/enhance/player/url/v1` |
| 加密方式 | **eapi（与 MI-1 完全相同）**。Folia 原实现走 `xeapi`，但经实测 **eapi 即可拿到有效结果**，无需 xeapi 公钥/会话，不需要新增加密代码 |
| 需要登录吗 | **不需要**。匿名态（无 `MUSIC_U`、无 `MUSIC_A`）即可拿到地址 |
| 请求 JSON | `{"ids":"[<songId>]","level":"exhigh","encodeType":"mp3","e_r":false,"header":{...}}` |
| 注意 | `ids` 是**字符串形式的 JSON 数组**（`"[347230]"`），不是数字数组；`header` 与 Cookie 头同 MI-1 规则 |
| 批量 | 支持一次多个 id（实测 3 个 id 返回 3 条），MI-2 只用单个 id |

### 1.2 响应形态（明文 JSON，`e_r=false`）

```json
{"code":200,"data":[{"id":347368,"url":"http://m802.music.126.net/.../xxx.mp3?vuutv=...","br":320000,
 "size":13090525,"md5":"...","code":200,"expi":1200,"type":"mp3","fee":0,"level":"exhigh",
 "freeTrialInfo":null}]}
```

| 字段 | 含义 |
|---|---|
| `url` | 播放地址。**是 `http://` 明文地址**（`m8xx.music.126.net`）→ 安卓必须放行明文流量 |
| `br` | 实际码率（bps） |
| `size` / `dt` | 字节数 / 歌单里的 `dt` 是毫秒时长 |
| `expi` | 地址有效期秒数，实测 `1200` |
| `level` | 实际给到的音质档位（请求 `exhigh` 时可能被压回 `standard`） |
| `freeTrialInfo` | **非 null 即"试听片段"**，实测形态 `{"fragmentType":-1,"start":0,"end":30,"algData":{...}}` |

### 1.3 匿名态实测结果（决定了"游客能不能听完整"）

| 歌类型 | `fee` | 实测结果 |
|---|---|---|
| 免费歌 | 0 | **完整曲目**（例：320kbps / 327.3 秒，`freeTrialInfo=null`） |
| 低音质免费 | 8 | **完整曲目**（例：320kbps / 239.6 秒，`freeTrialInfo=null`） |
| VIP 独占 | 1 | **30 秒试听片段**（`br=128012`、`size≈481KB`、`level` 被压回 `standard`、`freeTrialInfo.end=30`）；且把 `level` 依次改成 `standard/higher/exhigh/lossless/hires` 结果都一样 |
| 关键词「Taylor Swift」「五月天」30 首 | — | 匿名态 0 首完整、几乎全部 30 秒试听 → **试听标注是必需功能，不是边缘情况** |

**另一条重要事实**：从搜索结果里点播，匿名态 `url` **从未为空**（下架/无版权歌压根不进搜索结果），
所以 MI-2 里"播放地址为空"只是兜底分支，不会在正常流程触发。

### 1.4 明确的非目标（MI-2 不做）

- 登录 / 扫码 / `MUSIC_U`（MI-4）
- 歌词任何逻辑（MI-3）
- 收藏、历史、断网重试退避、备用源切换（MI-4）
- 音质切换 UI（固定请求 `exhigh`，服务端给什么就用什么）

---

## 2. 文件结构（本计划创建 / 修改的文件及职责）

```
app/src/main/java/com/musicbox/app/
├─ model/
│  ├─ StreamInfo.kt              [改] 增加 isTrial（试听片段标记）
│  └─ PlayMode.kt                [新] 顺序 / 单曲循环 / 随机 + 循环切换
├─ source/
│  ├─ SourceError.kt             [改] 增加 Unplayable（拿到地址但不可播）
│  └─ netease/
│     ├─ NetEaseDto.kt           [改] 增加取流响应 DTO（SongUrlRespDto / SongUrlDto / FreeTrialDto）
│     └─ NetEaseProvider.kt      [改] 实现 getStreamUrl
├─ player/
│  ├─ SongUri.kt                 [新] 占位 URI 编解码（纯 Kotlin，可单测）
│  ├─ SongStreamCache.kt         [新] 取流结果短期缓存（纯 Kotlin，可单测）+ 进程内共享实例
│  ├─ PlaybackService.kt         [新] MediaSessionService：ExoPlayer + MediaSession + 取流解析 + 预取下一首
│  └─ PlayModeMapping.kt         [新] PlayMode → ExoPlayer repeatMode/shuffleModeEnabled
└─ ui/
   ├─ ErrorText.kt               [新] SourceError → 用户可见中文文案（SearchViewModel 也改用它，DRY）
   ├─ search/
   │  ├─ SearchViewModel.kt      [改] 改用 ErrorText；高亮态交给播放层推进
   │  └─ SearchScreen.kt         [改] 列表项可点击 + 高亮当前播放曲
   └─ player/
      ├─ PlaybackConnection.kt   [新] MediaController 连接 + PlaybackUiState 汇聚
      ├─ PlayerViewModel.kt      [新] 点歌 / 播放控制 / 队列 / 播放模式
      ├─ Artwork.kt              [新] 极简远程封面加载（复用 OkHttp，不引图片库）
      ├─ NowPlayingBar.kt        [新] 底部迷你播放条
      ├─ PlayerScreen.kt         [新] 全屏播放页（含试听标注、进度拖动）
      └─ QueueSheet.kt           [新] 队列查看 / 删除 / 播放模式切换
app/src/main/java/com/musicbox/app/MainActivity.kt   [改] 装配导航 + 迷你条 + 通知权限申请
app/src/main/AndroidManifest.xml                     [改] 前台服务权限 + service 声明 + 明文流量
app/build.gradle.kts                                 [改] 加 media3 依赖

app/src/test/java/com/musicbox/app/
├─ model/PlayModeTest.kt                     [新]
├─ player/SongUriTest.kt                     [新]
├─ player/SongStreamCacheTest.kt             [新]
└─ source/netease/NetEaseProviderTest.kt     [改] 追加 4 个取流测试
app/src/test/resources/song_url_response.json [新] 取流响应固定样本
```

**分层规则不变**：`ui` 只依赖 `source` 的接口与 `player` 的门面；`player` 依赖 `source` 与 `model`；`model` 零依赖。

---

## Task 1: 实现网易云取流（`getStreamUrl`）

先说结论：这是 MI-2 风险最高的一步，所以放第一个做。端点与加密方式已在 §1 核实完毕，照抄即可。

**Files:**
- Modify: `app/src/main/java/com/musicbox/app/model/StreamInfo.kt`
- Modify: `app/src/main/java/com/musicbox/app/source/SourceError.kt`
- Modify: `app/src/main/java/com/musicbox/app/source/netease/NetEaseDto.kt`
- Modify: `app/src/main/java/com/musicbox/app/source/netease/NetEaseProvider.kt`
- Create: `app/src/test/resources/song_url_response.json`
- Test: `app/src/test/java/com/musicbox/app/source/netease/NetEaseProviderTest.kt`

- [ ] **Step 1: 写失败测试（先写测试，再去实现）**

在 `app/src/test/resources/song_url_response.json` 写入**真实响应形状**的固定样本（把真实超长 url 缩短，其余字段照抄）：

```json
{"code":200,"data":[{"id":347368,"url":"http://m802.music.126.net/20261016173259/sample/jd-musicrep-ts/sample.mp3?vuutv=abc","br":320000,"size":13090525,"md5":"e373556a62182903430f30275e61e9c4","code":200,"expi":1200,"type":"mp3","gain":2.0008,"peak":0,"fee":0,"level":"exhigh","freeTrialInfo":null}]}
```

在 `NetEaseProviderTest.kt` 末尾（文件最后一个 `}` 之前）追加以下内容。注意文件顶部需要补的 import：
`javax.crypto.Cipher`、`javax.crypto.spec.SecretKeySpec`、`java.net.URLDecoder`。

```kotlin
    // ── MI-2 取流 ────────────────────────────────────────────────

    private fun songUrlFixture(): String =
        javaClass.classLoader!!.getResource("song_url_response.json")!!.readText()

    /**
     * 把 transport 收到的 params 解回明文，用于断言我们真正发出去的加密 JSON。
     * 只在测试里做：生产代码不需要解密自己的能力。
     */
    private fun decryptParams(params: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(NetEaseCrypto.EAPI_KEY.toByteArray(Charsets.UTF_8), "AES"),
        )
        return String(cipher.doFinal(params.hexToBytes()), Charsets.UTF_8)
    }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0)
        return ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    /** 从 payload（uri + 分隔符 + text + 分隔符 + digest）里抠出 jsonText。 */
    private fun jsonTextOf(params: String): String =
        decryptParams(params).split(NetEaseCrypto.EAPI_SEP)[1]

    @Test
    fun `getStreamUrl posts ids and level then maps full stream`() = runBlocking {
        val transport = FakeTransport(TransportResponse(code = 200, body = songUrlFixture()))
        val provider = NetEaseProvider(transport = transport, deviceId = "ABCD")

        val result = provider.getStreamUrl(347368L)

        assertTrue(result.isSuccess)
        val info = result.getOrThrow()
        assertEquals(
            "http://m802.music.126.net/20261016173259/sample/jd-musicrep-ts/sample.mp3?vuutv=abc",
            info.url,
        )
        assertEquals(320000, info.bitrateBps)
        assertEquals(false, info.isTrial)

        // 我们真正发出去的东西
        assertEquals(
            "https://interfacepc.music.163.com/eapi/song/enhance/player/url/v1",
            transport.lastUrl,
        )
        assertTrue(transport.lastBody!!.startsWith("params="))
        val sentJson = jsonTextOf(
            URLDecoder.decode(transport.lastBody!!.removePrefix("params="), "UTF-8"),
        )
        assertTrue("实际请求体：$sentJson", sentJson.contains("""{"ids":"[347368]""""))
        assertTrue(sentJson.contains(""""level":"exhigh""""))
        assertTrue(sentJson.contains(""""encodeType":"mp3""""))
        assertTrue(sentJson.contains(""""e_r":false"""))
        assertTrue(sentJson.contains(""""header":{"osver""""))
        assertEquals(NetEaseHeader.UA, transport.lastHeaders!!["User-Agent"])
        assertTrue(transport.lastHeaders!!["Cookie"]!!.contains("deviceId=ABCD"))
    }

    @Test
    fun `getStreamUrl marks trial clip from freeTrialInfo`() = runBlocking {
        val trial = """{"code":200,"data":[{"id":1357375695,"url":"http://m702.music.126.net/x.mp3","br":128012,"size":481115,"expi":1200,"fee":1,"level":"standard","freeTrialInfo":{"fragmentType":-1,"start":0,"end":30,"algData":{"fragSource":"default"}}}]}"""
        val transport = FakeTransport(TransportResponse(code = 200, body = trial))
        val provider = NetEaseProvider(transport = transport, deviceId = "ABCD")

        val info = provider.getStreamUrl(1357375695L).getOrThrow()

        assertEquals(true, info.isTrial)
        assertEquals(128012, info.bitrateBps)
    }

    @Test
    fun `getStreamUrl fails as Unplayable when url is empty`() = runBlocking {
        val noUrl = """{"code":200,"data":[{"id":1,"url":null,"br":0,"expi":1200,"fee":0,"level":"none","freeTrialInfo":null}]}"""
        val transport = FakeTransport(TransportResponse(code = 200, body = noUrl))
        val provider = NetEaseProvider(transport = transport, deviceId = "ABCD")

        val result = provider.getStreamUrl(1L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is com.musicbox.app.source.SourceError.Unplayable)
    }

    @Test
    fun `getStreamUrl fails as Api when top level code is not 200`() = runBlocking {
        val transport = FakeTransport(
            TransportResponse(code = 200, body = """{"code":-460,"msg":"cheating"}"""),
        )
        val provider = NetEaseProvider(transport = transport, deviceId = "ABCD")

        val result = provider.getStreamUrl(1L)

        assertTrue(result.isFailure)
        assertEquals(-460, (result.exceptionOrNull() as com.musicbox.app.source.SourceError.Api).code)
    }
```

- [ ] **Step 2: 跑测试确认失败**

```bash
cd /Users/zhuguanyu/Documents/Trae-CN/temp_20261015_01
./gradlew :app:testDebugUnitTest --tests '*NetEaseProviderTest*'
```

预期：**编译失败**——`StreamInfo` 没有 `isTrial`、`SourceError.Unplayable` 不存在。编译失败也算"失败"，这一步就是确认测试真的在驱动实现。

- [ ] **Step 3: 扩展模型与错误类型**

`model/StreamInfo.kt` 整体替换为：

```kotlin
package com.musicbox.app.model

/**
 * 播放地址信息。
 *
 * isTrial 来自网易云取流响应的 freeTrialInfo：非 null 表示服务端只给了试听片段
 * （实测匿名态下 VIP 独占歌只给 30 秒、码率被压到 128k），UI 必须显式标注。
 */
data class StreamInfo(
    val url: String,
    val bitrateBps: Int,
    val isTrial: Boolean = false,
)
```

`source/SourceError.kt`：在 `Api` 之后、`Parse` 之前插入新错误类型（保持既有顺序与注释风格）：

```kotlin
    /** 接口应答正常但拿不到可播放地址（无版权 / 需要登录）。 */
    class Unplayable(message: String? = null) : SourceError(message ?: "当前歌曲暂无可用播放地址")
```

- [ ] **Step 4: 加取流响应 DTO**

在 `source/netease/NetEaseDto.kt` 末尾追加（文件已有 `internal data class` 与 `@SerializedName` 的写法，照此风格）：

```kotlin
/** /eapi/song/enhance/player/url/v1 的响应结构（字段以真实响应为准，见计划 §1.2）。 */
internal data class SongUrlRespDto(
    @SerializedName("code") val code: Int = -1,
    @SerializedName("data") val data: List<SongUrlDto>? = null,
)

internal data class SongUrlDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("url") val url: String? = null,
    @SerializedName("br") val bitrateBps: Int? = null,
    @SerializedName("size") val sizeBytes: Long? = null,
    @SerializedName("expi") val expireSeconds: Long? = null,
    @SerializedName("fee") val fee: Int? = null,
    @SerializedName("level") val level: String? = null,
    @SerializedName("freeTrialInfo") val freeTrialInfo: FreeTrialDto? = null,
)

internal data class FreeTrialDto(
    @SerializedName("start") val startMs: Long = 0L,
    @SerializedName("end") val endMs: Long = 0L,
)

/** 映射到统一模型。freeTrialInfo 非 null 即试听片段。 */
internal fun SongUrlDto.toStreamInfo(): StreamInfo? {
    val playableUrl = url
    if (playableUrl.isNullOrBlank()) return null
    return StreamInfo(
        url = playableUrl,
        bitrateBps = bitrateBps ?: 0,
        isTrial = freeTrialInfo != null,
    )
}
```

同步补 import：`import com.musicbox.app.model.StreamInfo`（若文件顶部已有 `com.musicbox.app.model.Song`，在它旁边加一行）。

- [ ] **Step 5: 实现 `getStreamUrl`**

`source/netease/NetEaseProvider.kt`：把当前的

```kotlin
    override suspend fun getStreamUrl(songId: Long): Result<StreamInfo> =
        Result.failure(SourceError.NotImplementedYet("MI-2"))
```

替换为：

```kotlin
    override suspend fun getStreamUrl(songId: Long): Result<StreamInfo> = withContext(Dispatchers.IO) {
        try {
            val header = NetEaseHeader.build(deviceId)
            val payload = linkedMapOf<String, Any>(
                // ids 必须是「字符串形式的 JSON 数组」，实测写成数字数组拿不到结果
                "ids" to "[$songId]",
                // 实测该档位对免费歌给到 320kbps，VIP 歌会自动降级为 standard + 试听片段
                "level" to STREAM_LEVEL,
                // 实测取 mp3 即可，且 ExoPlayer 吃 mp3 最省事
                "encodeType" to "mp3",
                "e_r" to false,
                "header" to header,
            )
            val uri = SONG_URL_URI
            val params = NetEaseCrypto.eapiParams(uri, gson.toJson(payload))
            val body = "params=" + URLEncoder.encode(params, "UTF-8")
            val headers = mapOf(
                "User-Agent" to NetEaseHeader.UA,
                "Cookie" to NetEaseHeader.cookieHeader(header),
                "Referer" to "https://music.163.com",
            )

            val response = transport.post(EAPI_BASE + uri.replace("/api", "/eapi"), body, headers)

            val dto = try {
                gson.fromJson(response.body, SongUrlRespDto::class.java)
            } catch (e: JsonSyntaxException) {
                throw SourceError.Parse("响应不是合法 JSON", e)
            } ?: throw SourceError.Parse("响应为空", null)

            if (dto.code != 200) {
                throw SourceError.Api(dto.code, null)
            }

            val first = dto.data?.firstOrNull()
                ?: throw SourceError.Parse("取流响应缺少 data", null)
            val info = first.toStreamInfo()
                ?: throw SourceError.Unplayable("当前歌曲暂无可用播放地址")

            Result.success(info)
        } catch (e: SourceError) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(SourceError.Network(e))
        }
    }
```

并在同一个文件的 `private companion object` 里补两行常量（放在 `SEARCH_PAGE_SIZE` 之后）：

```kotlin
        const val SONG_URL_URI = "/api/song/enhance/player/url/v1"

        /** 实测该档位对免费歌给到 320kbps，VIP 歌自动降级为 standard + 试听片段。 */
        const val STREAM_LEVEL = "exhigh"
```

- [ ] **Step 6: 跑测试确认通过**

```bash
./gradlew :app:testDebugUnitTest --tests '*NetEaseProviderTest*'
```

预期：**7 个测试全部 PASS**（原 3 个 + 新增 4 个）。

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/musicbox/app/model/StreamInfo.kt \
        app/src/main/java/com/musicbox/app/source/SourceError.kt \
        app/src/main/java/com/musicbox/app/source/netease/NetEaseDto.kt \
        app/src/main/java/com/musicbox/app/source/netease/NetEaseProvider.kt \
        app/src/test/java/com/musicbox/app/source/netease/NetEaseProviderTest.kt \
        app/src/test/resources/song_url_response.json
git commit -m "feat: implement netease stream url fetch with trial detection"
```


---

## Task 2: 播放模式与占位 URI（两小块纯逻辑，先落地可测的部分）

**Files:**
- Create: `app/src/main/java/com/musicbox/app/model/PlayMode.kt`
- Create: `app/src/main/java/com/musicbox/app/player/SongUri.kt`
- Test: `app/src/test/java/com/musicbox/app/model/PlayModeTest.kt`
- Test: `app/src/test/java/com/musicbox/app/player/SongUriTest.kt`

- [ ] **Step 1: 写失败测试**

`app/src/test/java/com/musicbox/app/model/PlayModeTest.kt`：

```kotlin
package com.musicbox.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayModeTest {

    @Test
    fun `cycles through three modes in order`() {
        assertEquals(PlayMode.REPEAT_ONE, PlayMode.ORDER.next())
        assertEquals(PlayMode.SHUFFLE, PlayMode.REPEAT_ONE.next())
        assertEquals(PlayMode.ORDER, PlayMode.SHUFFLE.next())
    }

    @Test
    fun `every mode has a chinese label`() {
        assertEquals(listOf("顺序", "单曲循环", "随机"), PlayMode.entries.map { it.label })
    }
}
```

`app/src/test/java/com/musicbox/app/player/SongUriTest.kt`：

```kotlin
package com.musicbox.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongUriTest {

    @Test
    fun `placeholder and parse round trip`() {
        assertEquals("musicbox://song/347368", SongUri.placeholder(347368L))
        assertEquals(347368L, SongUri.songIdOf("musicbox://song/347368"))
    }

    @Test
    fun `non placeholder uri is not a queue song`() {
        assertNull(SongUri.songIdOf("http://m802.music.126.net/a.mp3"))
        assertNull(SongUri.songIdOf("musicbox://song/not-a-number"))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
./gradlew :app:testDebugUnitTest --tests '*PlayModeTest*' --tests '*SongUriTest*'
```

预期：编译失败——`PlayMode` 与 `SongUri` 都还不存在。

- [ ] **Step 3: 实现 `PlayMode`**

`app/src/main/java/com/musicbox/app/model/PlayMode.kt`：

```kotlin
package com.musicbox.app.model

/**
 * 播放模式。三种模式的差异全部由 ExoPlayer 原生能力承担：
 * 顺序 = repeatMode OFF、单曲循环 = repeatMode ONE、随机 = shuffle 打开 + repeatMode ALL。
 * 映射实现见 player/PlayModeMapping.kt。
 */
enum class PlayMode(val label: String) {
    ORDER("顺序"),
    REPEAT_ONE("单曲循环"),
    SHUFFLE("随机"),
    ;

    fun next(): PlayMode = entries[(ordinal + 1) % entries.size]
}
```

- [ ] **Step 4: 实现 `SongUri`**

`app/src/main/java/com/musicbox/app/player/SongUri.kt`：

```kotlin
package com.musicbox.app.player

/**
 * 队列里每首 MediaItem 的占位 URI 编解码。
 *
 * 为什么要占位而不是直接写真实地址：真实地址要么提前批量取（请求数爆炸），
 * 要么等到播放时才知道（ExoPlayer 拿不到就播不了）。用占位 URI + ResolvingDataSource，
 * 队列可以完整地待在 ExoPlayer 的 timeline 上（通知栏上下首、自动续播、随机全部原生可用），
 * 真实地址只在真正 open 那一刻才去换。见 PlaybackService。
 */
object SongUri {

    private const val SCHEME = "musicbox"
    private const val HOST = "song"

    fun placeholder(songId: Long): String = "$SCHEME://$HOST/$songId"

    /** 占位 URI → songId；不是占位 URI（例如已经是 http 地址）时返回 null。 */
    fun songIdOf(uri: String): Long? {
        val prefix = "$SCHEME://$HOST/"
        if (!uri.startsWith(prefix)) return null
        return uri.removePrefix(prefix).substringBefore('?').toLongOrNull()
    }
}
```

- [ ] **Step 5: 跑测试确认通过**

```bash
./gradlew :app:testDebugUnitTest --tests '*PlayModeTest*' --tests '*SongUriTest*'
```

预期：**4 个测试全部 PASS**。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/musicbox/app/model/PlayMode.kt \
        app/src/main/java/com/musicbox/app/player/SongUri.kt \
        app/src/test/java/com/musicbox/app/model/PlayModeTest.kt \
        app/src/test/java/com/musicbox/app/player/SongUriTest.kt
git commit -m "feat: add play mode and queue placeholder uri"
```

---

## Task 3: 取流结果短期缓存（线程安全 + TTL + LRU）

真实地址实测有效期 `expi=1200` 秒，且同一首歌会被反复请求（点歌时预取一次、ExoPlayer 打开一次、下一首预取一次），必须缓存。
**进程内共享**：`PlaybackService`（取流、预取）与 UI（读"当前这首是不是试听片段"）需要看到同一份结果，因此除可单测的类之外，另给一个进程内共享实例。

**Files:**
- Create: `app/src/main/java/com/musicbox/app/player/SongStreamCache.kt`
- Test: `app/src/test/java/com/musicbox/app/player/SongStreamCacheTest.kt`

- [ ] **Step 1: 写失败测试**

`app/src/test/java/com/musicbox/app/player/SongStreamCacheTest.kt`：

```kotlin
package com.musicbox.app.player

import com.musicbox.app.model.StreamInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongStreamCacheTest {

    private val full = StreamInfo(url = "http://m1.music.126.net/a.mp3", bitrateBps = 320000)
    private val trial =
        StreamInfo(url = "http://m2.music.126.net/b.mp3", bitrateBps = 128012, isTrial = true)

    @Test
    fun `returns stored info and keeps trial flag`() {
        val cache = SongStreamCache()
        cache.put(1L, trial)

        assertEquals(trial, cache.get(1L))
        assertNull(cache.get(2L))
    }

    @Test
    fun `expires after ttl`() {
        var now = 1_000L
        val cache = SongStreamCache(ttlMs = 600_000L) { now }
        cache.put(1L, full)

        now += 599_999L
        assertEquals(full, cache.get(1L))

        now += 2L
        assertNull(cache.get(1L))
        assertEquals(0, cache.size())
    }

    @Test
    fun `evicts least recently used entry beyond capacity`() {
        val cache = SongStreamCache(maxEntries = 2)
        cache.put(1L, full)
        cache.put(2L, full)
        cache.get(1L)       // 1 变成最近使用
        cache.put(3L, full) // 淘汰 2

        assertNull(cache.get(2L))
        assertEquals(full, cache.get(1L))
        assertEquals(full, cache.get(3L))
        assertEquals(2, cache.size())
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
./gradlew :app:testDebugUnitTest --tests '*SongStreamCacheTest*'
```

预期：编译失败——`SongStreamCache` 不存在。

- [ ] **Step 3: 实现缓存**

`app/src/main/java/com/musicbox/app/player/SongStreamCache.kt`：

```kotlin
package com.musicbox.app.player

import com.musicbox.app.model.StreamInfo

/**
 * 取流结果短期缓存。纯 JVM 实现（LinkedHashMap + accessOrder），不依赖 Android，
 * 因此可以在单测里跑。TTL 默认 10 分钟，比实测的 expi=1200 秒留了余量。
 */
class SongStreamCache(
    private val maxEntries: Int = 20,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private class Entry(val info: StreamInfo, val expiresAtMs: Long)

    // accessOrder = true：get 会把条目挪到队尾，队首即「最久未使用」，用于 LRU 淘汰
    private val entries = LinkedHashMap<Long, Entry>(16, 0.75f, true)

    fun get(songId: Long): StreamInfo? = synchronized(entries) {
        val entry = entries[songId] ?: return null
        if (entry.expiresAtMs <= clock()) {
            entries.remove(songId)
            return null
        }
        entry.info
    }

    fun put(songId: Long, info: StreamInfo) = synchronized(entries) {
        entries[songId] = Entry(info, clock() + ttlMs)
        while (entries.size > maxEntries) {
            val oldest = entries.keys.first()
            entries.remove(oldest)
        }
    }

    fun size(): Int = synchronized(entries) { entries.size }

    companion object {
        const val DEFAULT_TTL_MS = 10 * 60 * 1000L
    }
}

/**
 * 进程内共享实例。本项目是单进程个人自用 App，Service 与 UI 读同一份取流结果
 * 比自建跨组件通道更简单（也让「试听片段」标记无需额外协议即可被 UI 看到）。
 * 单测请自行 new SongStreamCache()，不要用这个共享实例。
 */
internal val sharedSongStreamCache = SongStreamCache()
```

- [ ] **Step 4: 跑测试确认通过**

```bash
./gradlew :app:testDebugUnitTest --tests '*SongStreamCacheTest*'
```

预期：**3 个测试全部 PASS**。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/musicbox/app/player/SongStreamCache.kt \
        app/src/test/java/com/musicbox/app/player/SongStreamCacheTest.kt
git commit -m "feat: add stream url short lived cache with trial info"
```

---

## Task 4: 接入 Media3 与前台播放服务（骨架先跑通）

Media3 的 `MediaSessionService` 只要正确声明，通知栏 / 锁屏 / 耳机线控 / 前台保活全部自动获得，这是 MI-2 验收项「切后台不断、通知栏能控制」的实现根基。

本任务不写单元测试：`Service`、`ExoPlayer`、`MediaSession` 都无法在 JVM 单测里构造（依赖 Android 运行时）。
验证方式是「编译通过 + 装机后启动不崩」，由 Task 7 的验收覆盖。

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/musicbox/app/player/PlaybackService.kt`

- [ ] **Step 1: 加依赖**

`app/build.gradle.kts` 的 `dependencies { }` 内，在 okhttp/gson 那一组下面加：

```kotlin
    // Media3：ExoPlayer 播放引擎 + MediaSession（通知栏 / 锁屏 / 耳机线控）
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
```

- [ ] **Step 2: 改清单：权限、明文流量、Service 声明**

`app/src/main/AndroidManifest.xml`：`<uses-permission>` 区域改为：

```xml
    <uses-permission android:name="android.permission.INTERNET" />
    <!-- 前台播放服务（Media3 通知栏 / 后台不间断播放） -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
```

`<application>` 开标签加上 `android:usesCleartextTraffic="true"`（**必需**：实测播放地址是 `http://m8xx.music.126.net/...` 明文地址，安卓默认拦截）：

```xml
    <application
        android:label="@string/app_name"
        android:theme="@style/Theme.MusicBox"
        android:usesCleartextTraffic="true"
        android:supportsRtl="true">
```

在 `</application>` 之前加 Service 声明（类名必须与下面 Step 3 创建的 `PlaybackService` 一致）：

```xml
        <service
            android:name=".player.PlaybackService"
            android:exported="true"
            android:foregroundServiceType="mediaPlayback">
            <intent-filter>
                <action android:name="androidx.media3.session.MediaSessionService" />
            </intent-filter>
        </service>
```

- [ ] **Step 3: 写播放服务**

`app/src/main/java/com/musicbox/app/player/PlaybackService.kt`：

```kotlin
package com.musicbox.app.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.musicbox.app.AppContainer
import com.musicbox.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * 前台播放服务。用 Media3 的 MediaSessionService，通知栏 / 锁屏 / 耳机线控 / 自动续播
 * 都由框架承担，UI 侧只通过 MediaController 观察与控制（见 PlaybackConnection）。
 *
 * 队列里的 MediaItem 用占位 URI（musicbox://song/<id>），真正播放时才由
 * [resolveUri] 向网易云换取真实地址，结果写进 [sharedSongStreamCache]。
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var container: AppContainer

    // 预取下一首用：不能占用播放/主线程
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        val upstream = DefaultDataSource.Factory(this)
        val resolving = ResolvingDataSource.Factory(upstream) { spec ->
            spec.withUri(resolveUri(spec.uri))
        }
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                prefetchNext()
            }
        })

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        prefetchScope.cancel()
        session?.release()
        session = null
        player.release()
        super.onDestroy()
    }

    /**
     * 把占位 URI 换成真实播放地址。缓存命中直接返回；未命中则阻塞式取流（带超时），
     * 失败时抛 IOException 交给 ExoPlayer 的 onPlayerError 处理（UI 侧会提示并跳下一首）。
     *
     * 这里用 runBlocking 是被允许的简化：resolver 运行在 ExoPlayer 的加载线程上，
     * 不在主线程，也不会与 UI 协程互相等待。
     */
    private fun resolveUri(uri: Uri): Uri {
        val songId = SongUri.songIdOf(uri.toString()) ?: return uri

        sharedSongStreamCache.get(songId)?.let { return Uri.parse(it.url) }

        val info = runBlocking {
            withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { container.provider.getStreamUrl(songId) }
        }?.getOrNull() ?: throw IOException("取流失败 songId=$songId")

        sharedSongStreamCache.put(songId, info)
        return Uri.parse(info.url)
    }

    /** 提前把下一首的地址取好，让切歌时 resolver 直接命中缓存、不阻塞。 */
    private fun prefetchNext() {
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return
        val songId = player.getMediaItemAt(nextIndex).mediaId.toLongOrNull() ?: return
        if (sharedSongStreamCache.get(songId) != null) return

        prefetchScope.launch {
            container.provider.getStreamUrl(songId)
                .onSuccess { sharedSongStreamCache.put(songId, it) }
        }
    }

    private companion object {
        const val RESOLVE_TIMEOUT_MS = 15_000L
    }
}
```

- [ ] **Step 4: 编译**

```bash
./gradlew :app:assembleDebug
```

预期：`BUILD SUCCESSFUL`。首次会下载 media3 的 AAR，若拉取失败先确认网络再重跑。

- [ ] **Step 5: 装机并确认启动不崩**

```bash
ADB=/Users/zhuguanyu/Library/Android/sdk/platform-tools/adb
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
$ADB shell am start -n com.musicbox.app.debug/com.musicbox.app.MainActivity
sleep 3
$ADB logcat -d | grep -i "FATAL\|AndroidRuntime" | tail -20
```

预期：无 `FATAL EXCEPTION`。（此时 UI 还没接播放，服务只是被声明、不会启动，属正常。）

- [ ] **Step 6: 提交**

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml \
        app/src/main/java/com/musicbox/app/player/PlaybackService.kt
git commit -m "feat: add media3 playback foreground service with on demand stream resolving"
```

---

## Task 5: 播放模式映射 + MediaController 连接层（把状态汇聚成 UI 可用的流）

`PlayerViewModel` 与 UI 都需要一个稳定的地方把 `MediaController` 的碎片状态（曲目、时长、播放中、缓冲中、队列、试听标记）汇成一份不可变快照，并做成 `StateFlow`。这一层就是 `PlaybackConnection`。

**Files:**
- Create: `app/src/main/java/com/musicbox/app/player/PlayModeMapping.kt`
- Create: `app/src/main/java/com/musicbox/app/ui/player/PlaybackConnection.kt`

- [ ] **Step 1: 写 `PlayModeMapping`**

`app/src/main/java/com/musicbox/app/player/PlayModeMapping.kt`：

```kotlin
package com.musicbox.app.player

import androidx.media3.common.Player
import com.musicbox.app.model.PlayMode

/**
 * 播放模式 → ExoPlayer 原生开关。三种模式全部由 ExoPlayer 自己实现推进逻辑，
 * 我们不去手写队列索引（队列在 timeline 上，自动续播、通知栏上下首都因此可用）。
 */
internal fun PlayMode.applyTo(player: Player) {
    when (this) {
        // 顺序：播到队列末尾就停
        PlayMode.ORDER -> {
            player.shuffleModeEnabled = false
            player.repeatMode = Player.REPEAT_MODE_OFF
        }
        // 单曲循环：ExoPlayer 原生 REPEAT_MODE_ONE
        PlayMode.REPEAT_ONE -> {
            player.shuffleModeEnabled = false
            player.repeatMode = Player.REPEAT_MODE_ONE
        }
        // 随机：打乱顺序 + 队列放完继续随机
        PlayMode.SHUFFLE -> {
            player.shuffleModeEnabled = true
            player.repeatMode = Player.REPEAT_MODE_ALL
        }
    }
}
```

- [ ] **Step 2: 写 `PlaybackConnection`**

`app/src/main/java/com/musicbox/app/ui/player/PlaybackConnection.kt`：

```kotlin
package com.musicbox.app.ui.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.musicbox.app.model.PlayMode
import com.musicbox.app.player.PlaybackService
import com.musicbox.app.player.sharedSongStreamCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 队列里的一项（给 UI 展示用，取自 ExoPlayer timeline 上的 MediaItem 元数据）。 */
data class QueueItem(
    val index: Int,
    val title: String,
    val artist: String,
    val isCurrent: Boolean,
)

/** UI 需要的全部播放状态快照。 */
data class PlaybackUiState(
    val connected: Boolean = false,
    val hasCurrent: Boolean = false,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val artworkUrl: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isTrial: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<QueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val errorMessage: String? = null,
)

/**
 * MediaController 的连接与状态汇聚。UI 只跟它打交道，不直接接触 ExoPlayer。
 *
 * 位置每 250 毫秒采一次：进度条不需要逐帧精度。MI-3 的逐字歌词才需要逐帧时钟，
 * 那时会在这一层之上加 withFrameNanos 驱动的 positionClock。
 */
class PlaybackConnection(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var future: ListenableFuture<MediaController>? = null
    private var ticker: Job? = null

    /** 播放模式。放在连接层而不是 Service：它是 UI 偏好，用不着跨进程。 */
    val playMode = MutableStateFlow(PlayMode.ORDER)

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh(player)

        override fun onPlayerError(error: PlaybackException) {
            // 取流失败 / 版权不可播：显式提示并自动跳下一首（设计文档 §9）
            _state.update { it.copy(errorMessage = "当前歌曲无法播放，已跳过", isBuffering = false) }
            val current = _controller.value
            if (current != null && current.hasNextMediaItem()) {
                current.seekToNextMediaItem()
            }
        }
    }

    /** 连接播放服务。幂等：重复调用直接返回。 */
    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val built = MediaController.Builder(context, token).buildAsync()
        future = built
        built.addListener(
            {
                val controller = runCatching { built.get() }.getOrNull() ?: return@addListener
                controller.addListener(listener)
                _controller.value = controller
                refresh(controller)
                startTicker(controller)
            },
            { runnable -> context.mainExecutor.execute(runnable) },
        )
    }

    fun release() {
        ticker?.cancel()
        ticker = null
        _controller.value?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        _controller.value = null
        _state.value = PlaybackUiState()
        scope.cancel()
    }

    /** 等连接就绪。点歌时调用；还没连上就挂起等它连上。 */
    suspend fun awaitController(): MediaController? {
        connect()
        return controller.first { it != null }
    }

    fun clearError() = _state.update { it.copy(errorMessage = null) }

    private fun startTicker(controller: MediaController) {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                refresh(controller)
                delay(POSITION_TICK_MS)
            }
        }
    }

    private fun refresh(controller: Player) {
        val item = controller.currentMediaItem
        val metadata = item?.mediaMetadata
        val songId = item?.mediaId?.toLongOrNull()
        val duration = controller.duration
        _state.update { previous ->
            previous.copy(
                connected = true,
                hasCurrent = item != null,
                title = metadata?.title?.toString(),
                artist = metadata?.artist?.toString(),
                album = metadata?.albumTitle?.toString(),
                artworkUrl = metadata?.artworkUri?.toString(),
                isPlaying = controller.isPlaying,
                isBuffering = controller.playbackState == Player.STATE_BUFFERING,
                isTrial = songId?.let { sharedSongStreamCache.get(it)?.isTrial } == true,
                positionMs = controller.currentPosition.coerceAtLeast(0),
                durationMs = if (duration > 0) duration else 0L,
                queue = queueOf(controller),
                currentIndex = controller.currentMediaItemIndex,
            )
        }
    }

    private fun queueOf(controller: Player): List<QueueItem> =
        (0 until controller.mediaItemCount).map { index ->
            val item: MediaItem = controller.getMediaItemAt(index)
            QueueItem(
                index = index,
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString().orEmpty(),
                isCurrent = index == controller.currentMediaItemIndex,
            )
        }

    private companion object {
        const val POSITION_TICK_MS = 250L
    }
}
```

- [ ] **Step 3: 编译**

```bash
./gradlew :app:assembleDebug
```

预期：`BUILD SUCCESSFUL`。易踩的两个坑：`MediaController.releaseFuture` 需要 `media3-session`（Step 已加）；`context.mainExecutor` 是 `Context.getMainExecutor()` 的 Kotlin 属性写法，Android 6+ 可用。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/musicbox/app/player/PlayModeMapping.kt \
        app/src/main/java/com/musicbox/app/ui/player/PlaybackConnection.kt
git commit -m "feat: add play mode mapping and media controller connection layer"
```

---

## Task 6: 播放界面（点歌出声 + 迷你条 + 播放页 + 队列 + 试听标注）

本任务同样无法 JVM 单测（Compose + MediaController 都要 Android 运行时），交付判据是「编译通过 + Task 7 装机验收」。

**Files:**
- Create: `app/src/main/java/com/musicbox/app/ui/ErrorText.kt`
- Create: `app/src/main/java/com/musicbox/app/ui/player/Artwork.kt`
- Create: `app/src/main/java/com/musicbox/app/ui/player/PlayerViewModel.kt`
- Create: `app/src/main/java/com/musicbox/app/ui/player/NowPlayingBar.kt`
- Create: `app/src/main/java/com/musicbox/app/ui/player/PlayerScreen.kt`
- Create: `app/src/main/java/com/musicbox/app/ui/player/QueueSheet.kt`
- Modify: `app/src/main/java/com/musicbox/app/ui/player/PlaybackConnection.kt`
- Modify: `app/src/main/java/com/musicbox/app/ui/search/SearchViewModel.kt`
- Modify: `app/src/main/java/com/musicbox/app/ui/search/SearchScreen.kt`
- Modify: `app/src/main/java/com/musicbox/app/MainActivity.kt`

- [ ] **Step 1: 抽出错误文案（DRY）**

`app/src/main/java/com/musicbox/app/ui/ErrorText.kt`：

```kotlin
package com.musicbox.app.ui

import com.musicbox.app.source.SourceError

/** 音源错误 → 用户可见文案。所有页面共用一份，避免文案各写一套。 */
internal fun Throwable.toUserMessage(): String = when (this) {
    is SourceError.Network -> "网络不可用，请检查网络"
    is SourceError.Api -> "音源暂时不可用，请稍后重试"
    is SourceError.Parse -> "解析失败，可能接口已变更"
    is SourceError.Unplayable -> "这首歌暂无版权或需要登录"
    is SourceError.NotImplementedYet -> "该功能尚未实现"
    else -> "出错了"
}
```

`ui/search/SearchViewModel.kt`：把 `search()` 里那段 `when` 映射换成一行，并调整 import（删掉不再使用的 `com.musicbox.app.source.SourceError`，加上 `com.musicbox.app.ui.toUserMessage`）：

```kotlin
                .onFailure { error ->
                    _state.update {
                        it.copy(loading = false, searched = true, message = error.toUserMessage())
                    }
                }
```

- [ ] **Step 2: 极简封面加载（不引图片库）**

`app/src/main/java/com/musicbox/app/ui/player/Artwork.kt`：

```kotlin
package com.musicbox.app.ui.player

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

// 封面来源是 p*.music.126.net 的 jpg，一张十几行就够，没必要为它引入图片库
private val artworkClient = OkHttpClient()
private val artworkCache = mutableMapOf<String, ImageBitmap>()

@Composable
private fun rememberArtwork(url: String?): ImageBitmap? {
    var bitmap by remember(url) { mutableStateOf(url?.let { artworkCache[it] }) }

    LaunchedEffect(url) {
        if (url == null || bitmap != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                artworkClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    BitmapFactory.decodeStream(response.body.byteStream())
                }
            }.getOrNull()?.asImageBitmap()
        }
        if (loaded != null) {
            artworkCache[url] = loaded
            bitmap = loaded
        }
    }
    return bitmap
}

/** 封面方块。拿不到图时用底色 + 音符占位，不留空白。 */
@Composable
internal fun Artwork(url: String?, modifier: Modifier = Modifier) {
    val bitmap = rememberArtwork(url)
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(text = "♪", style = MaterialTheme.typography.displayMedium)
        }
    }
}
```

- [ ] **Step 3: 让连接层暴露当前歌曲 id**

`ui/player/PlaybackConnection.kt`：`PlaybackUiState` 增加一个字段（放在 `hasCurrent` 之后）：

```kotlin
    /** 当前播放歌曲的音源 id，供搜索列表高亮用。 */
    val currentSongId: Long? = null,
```

并在 `refresh()` 的 `previous.copy(...)` 里、紧接 `hasCurrent = item != null,` 之后加一行：

```kotlin
                currentSongId = songId,
```

- [ ] **Step 4: 写 `PlayerViewModel`**

`app/src/main/java/com/musicbox/app/ui/player/PlayerViewModel.kt`：

```kotlin
package com.musicbox.app.ui.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.musicbox.app.model.PlayMode
import com.musicbox.app.model.Song
import com.musicbox.app.player.SongUri
import com.musicbox.app.player.applyTo
import com.musicbox.app.player.sharedSongStreamCache
import com.musicbox.app.source.MusicProvider
import com.musicbox.app.ui.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val provider: MusicProvider,
    private val connection: PlaybackConnection,
) : ViewModel() {

    val state: StateFlow<PlaybackUiState> = connection.state
    val mode: StateFlow<PlayMode> = connection.playMode

    /** 一次性提示（播放失败、模式切换等），由 UI 消费后清空。 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun clearError() = connection.clearError()

    /** 点歌：把整套搜索结果作为队列交给 ExoPlayer，从 startIndex 开始播。 */
    fun play(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty() || startIndex !in songs.indices) return
        viewModelScope.launch {
            val controller = connection.awaitController()
            if (controller == null) {
                _message.value = "播放服务连接中，请稍后重试"
                return@launch
            }

            // 先把当前这首的地址取好并写进共享缓存，让 resolver 打开时不阻塞
            val target = songs[startIndex]
            val info = provider.getStreamUrl(target.id).getOrElse { error ->
                _message.value = error.toUserMessage()
                return@launch
            }
            sharedSongStreamCache.put(target.id, info)

            controller.setMediaItems(
                songs.map { it.toQueueMediaItem() },
                startIndex,
                C.TIME_UNSET,
            )
            connection.playMode.value.applyTo(controller)
            controller.prepare()
            controller.play()
        }
    }

    fun togglePlay() {
        val controller = connection.controller.value ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun next() {
        connection.controller.value?.seekToNextMediaItem()
    }

    fun previous() {
        connection.controller.value?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        connection.controller.value?.seekTo(positionMs)
    }

    /** 队列面板里点某一首 → 直接跳过去播。 */
    fun selectQueueItem(index: Int) {
        connection.controller.value?.seekTo(index, 0L)
    }

    /** 顺序 → 单曲循环 → 随机 轮换，切换后立刻作用到播放器。 */
    fun cyclePlayMode() {
        val controller = connection.controller.value ?: return
        val next = connection.playMode.value.next()
        next.applyTo(controller)
        connection.playMode.value = next
        _message.value = "播放模式：${next.label}"
    }

    fun removeQueueItem(index: Int) {
        val controller = connection.controller.value ?: return
        if (index !in 0 until controller.mediaItemCount) return
        controller.removeMediaItem(index)
    }
}

/**
 * Song → 队列 MediaItem。URI 用占位形式，真实地址由 PlaybackService 的
 * ResolvingDataSource 在播放那一刻才换取（见 SongUri 的注释）。
 */
private fun Song.toQueueMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(SongUri.placeholder(id))
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(name)
            .setArtist(artists.joinToString(" / "))
            .setAlbumTitle(album)
            .setArtworkUri(coverUrl?.let(Uri::parse))
            .setIsPlayable(true)
            .build(),
    )
    .build()
```

- [ ] **Step 5: 写迷你播放条**

`app/src/main/java/com/musicbox/app/ui/player/NowPlayingBar.kt`：

```kotlin
package com.musicbox.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 底部迷你播放条。点整条打开播放页，右侧按钮直接播放/暂停。 */
@Composable
internal fun NowPlayingBar(
    state: PlaybackUiState,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.hasCurrent) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.clickable(onClick = onExpand)) {
            // 进度条：2dp 细线，够用且不抢视觉
            val fraction = if (state.durationMs > 0) {
                (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(2.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceAtLeast(0.001f))
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary),
                ) {}
                Column(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                ) {}
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    url = state.artworkUrl,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                )
                Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(
                        text = state.title.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append(state.artist.orEmpty())
                            if (state.isTrial) append(" · 试听片段")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = onTogglePlay) {
                    Text(if (state.isPlaying) "⏸" else "▶", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
```

- [ ] **Step 6: 写播放页**

`app/src/main/java/com/musicbox/app/ui/player/PlayerScreen.kt`：

```kotlin
package com.musicbox.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 全屏播放页。MI-2 只保证能控制与信息完整（含试听标注），
 * 封面取色、过渡动画、图标体系等视觉打磨留给 MI-4。
 */
@Composable
internal fun PlayerScreen(
    state: PlaybackUiState,
    modeLabel: String,
    onCollapse: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleMode: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onCollapse)

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCollapse) { Text("收起") }
                Text(
                    text = "正在播放",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenQueue) { Text("队列") }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Artwork(
                url = state.artworkUrl,
                modifier = Modifier.fillMaxWidth(0.78f).aspectRatio(1f).clip(RoundedCornerShape(16.dp)),
            )

            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = state.title.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = state.artist.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.isTrial) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "试听片段 · 登录解锁完整版",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            var dragging by remember { mutableStateOf(false) }
            var dragValue by remember { mutableFloatStateOf(0f) }
            val max = state.durationMs.coerceAtLeast(1L).toFloat()
            val shown = if (dragging) dragValue else state.positionMs.toFloat()

            Slider(
                value = shown.coerceIn(0f, max),
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    onSeek(dragValue.toLong())
                    dragging = false
                },
                valueRange = 0f..max,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = formatTime(shown.toLong()), style = MaterialTheme.typography.labelSmall)
                Text(text = formatTime(state.durationMs), style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCycleMode) { Text(modeLabel) }
                TextButton(onClick = onPrevious) {
                    Text("⏮", style = MaterialTheme.typography.headlineSmall)
                }
                TextButton(onClick = onTogglePlay) {
                    Text(
                        text = if (state.isPlaying) "⏸" else "▶",
                        style = MaterialTheme.typography.displaySmall,
                    )
                }
                TextButton(onClick = onNext) {
                    Text("⏭", style = MaterialTheme.typography.headlineSmall)
                }
                // 占位保持左右对称，避免按钮横向跳动
                TextButton(onClick = {}, enabled = false) { Text(" ") }
            }

            if (state.isBuffering) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "缓冲中…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 毫秒 → m:ss。 */
internal fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
```

> 说明：用文字符号（⏮ ▶ ⏭）当按钮是刻意的选择——MI-2 的判据是"能出声、能控制"，
> 图标体系与视觉打磨留给 MI-4，避免在功能里程碑里引入图标依赖与样式争议。

- [ ] **Step 7: 写队列面板**

`app/src/main/java/com/musicbox/app/ui/player/QueueSheet.kt`：

```kotlin
package com.musicbox.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 队列面板：查看 / 删除 / 切换播放模式。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueSheet(
    state: PlaybackUiState,
    modeLabel: String,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onCycleMode: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "播放队列（${state.queue.size}）",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCycleMode) { Text("模式：$modeLabel") }
            }

            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(state.queue, key = { it.index }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (item.isCurrent) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            )
                            .clickable { onSelect(item.index) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = item.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { onRemove(item.index) }) { Text("移除") }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 8: 搜索页可点播 + 高亮当前播放**

`ui/search/SearchViewModel.kt`：`SearchUiState` 增加字段（放在 `searched` 之后）：

```kotlin
    /** 当前正在播放的歌曲 id，用于列表高亮。由播放层推进，不是搜索层自己的状态。 */
    val nowPlayingSongId: Long? = null,
```

并在类内新增入口：

```kotlin
    fun onNowPlayingChanged(songId: Long?) {
        _state.update { it.copy(nowPlayingSongId = songId) }
    }
```

`ui/search/SearchScreen.kt`：函数签名改为（并新增 `nowPlayingSongId` / `onSongClick` 两个参数）：

```kotlin
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    nowPlayingSongId: Long?,
    onSongClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
```

把 `LazyColumn` 里的 `items(state.results, key = { it.id }) { song -> ... }` 整块替换为：

```kotlin
            itemsIndexed(state.results, key = { _, song -> song.id }) { index, song ->
                val isCurrent = song.id == nowPlayingSongId
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSongClick(index) }
                        .background(
                            if (isCurrent) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        )
                        .padding(vertical = 10.dp),
                ) {
                    Text(
                        text = song.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artists.joinToString(" / ") +
                            (song.album?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HorizontalDivider()
            }
```

import 区相应调整：加 `androidx.compose.foundation.background`、`androidx.compose.foundation.clickable`、
`androidx.compose.foundation.lazy.itemsIndexed`，删掉不再使用的 `androidx.compose.foundation.lazy.items`。

- [ ] **Step 9: 装配导航与通知权限申请**

`app/src/main/java/com/musicbox/app/MainActivity.kt` 整体替换为：

```kotlin
package com.musicbox.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.musicbox.app.ui.MusicBoxTheme
import com.musicbox.app.ui.player.NowPlayingBar
import com.musicbox.app.ui.player.PlaybackConnection
import com.musicbox.app.ui.player.PlayerScreen
import com.musicbox.app.ui.player.PlayerViewModel
import com.musicbox.app.ui.player.QueueSheet
import com.musicbox.app.ui.search.SearchScreen
import com.musicbox.app.ui.search.SearchViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = AppContainer(this)
        setContent {
            MusicBoxTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MusicBoxApp(container)
                }
            }
        }
    }
}

@Composable
private fun MusicBoxApp(container: AppContainer) {
    val context = LocalContext.current

    // MediaController 的连接随 Composition 建立与释放
    val connection = remember { PlaybackConnection(context.applicationContext) }
    DisposableEffect(Unit) {
        connection.connect()
        onDispose { connection.release() }
    }

    val searchVm: SearchViewModel = viewModel(
        factory = viewModelFactory { initializer { SearchViewModel(container.provider) } },
    )
    val playerVm: PlayerViewModel = viewModel(
        factory = viewModelFactory { initializer { PlayerViewModel(container.provider, connection) } },
    )

    val searchState by searchVm.state.collectAsStateWithLifecycle()
    val playState by playerVm.state.collectAsStateWithLifecycle()
    val mode by playerVm.mode.collectAsStateWithLifecycle()
    val message by playerVm.message.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showPlayer by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    // 切歌 → 搜索列表高亮跟随
    LaunchedEffect(playState.currentSongId) {
        searchVm.onNowPlayingChanged(playState.currentSongId)
    }

    // 一次性提示
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            playerVm.consumeMessage()
        }
    }
    LaunchedEffect(playState.errorMessage) {
        playState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            playerVm.clearError()
        }
    }

    // Android 13+ 通知栏需要运行时权限（拒绝也不影响播放，只少一条通知）
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 拒绝就让用户自己去设置里开，不阻塞播放 */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NowPlayingBar(
                state = playState,
                onExpand = { showPlayer = true },
                onTogglePlay = playerVm::togglePlay,
            )
        },
    ) { innerPadding ->
        SearchScreen(
            state = searchState,
            onQueryChange = searchVm::onQueryChange,
            onSearch = searchVm::search,
            nowPlayingSongId = playState.currentSongId,
            onSongClick = { index -> playerVm.play(searchState.results, index) },
            modifier = Modifier.padding(innerPadding),
        )
    }

    if (showPlayer) {
        PlayerScreen(
            state = playState,
            modeLabel = mode.label,
            onCollapse = { showPlayer = false },
            onTogglePlay = playerVm::togglePlay,
            onNext = playerVm::next,
            onPrevious = playerVm::previous,
            onSeek = playerVm::seekTo,
            onCycleMode = playerVm::cyclePlayMode,
            onOpenQueue = { showQueue = true },
        )
    }

    if (showQueue) {
        QueueSheet(
            state = playState,
            modeLabel = mode.label,
            onDismiss = { showQueue = false },
            onSelect = { index -> playerVm.selectQueueItem(index) },
            onRemove = playerVm::removeQueueItem,
            onCycleMode = playerVm::cyclePlayMode,
        )
    }
}
```

- [ ] **Step 10: 编译并全量跑单测**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

预期：`BUILD SUCCESSFUL`，单测 **14 个全部 PASS**（7 取流 / 2 模式 / 2 URI / 3 缓存）。
若报未使用 import 或找不到符号，按报错逐个修正（不要靠删测试绕过）。

- [ ] **Step 11: 提交**

```bash
git add app/src/main/java/com/musicbox/app/
git commit -m "feat: wire playback ui with mini bar player screen and queue sheet"
```

---

## Task 7: 模拟器验收（MI-2 的完成判据）

**Files:** 无（本任务只做验证，除非发现缺陷才回头改代码）

- [ ] **Step 1: 装机**

```bash
cd /Users/zhuguanyu/Documents/Trae-CN/temp_20261015_01
./gradlew :app:assembleDebug
ADB=/Users/zhuguanyu/Library/Android/sdk/platform-tools/adb
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
$ADB logcat -c
$ADB shell am start -n com.musicbox.app.debug/com.musicbox.app.MainActivity
```

- [ ] **Step 2: 验收项 1 —— 点歌能出声**

1. 搜索框输入 `Beyond`，点「搜索」。
2. 点列表中第一首（`海阔天空 - Beyond`）。
3. 判定：底部迷你条出现该曲、状态变播放中；扬声器**能听到声音**（模拟器需在宿主机侧确认音频输出，`-no-audio` 启动的模拟器没有声音——若听不到，先确认模拟器是否带音频，再取 `$ADB shell dumpsys media_session` 验证播放态）。
4. 用 `PlayerScreen` 的进度条拖动并观察到时间跳变，确认是**真的在流式播放**而不是静默失败：

```bash
$ADB shell dumpsys media_session | grep -A 12 "musicbox"
```

预期：能看到一个 `MusicBox`/`PlaybackService` 会话，状态为 `state=PLAYING`，含 `position=` 与 `speed=1.0`。

- [ ] **Step 3: 验收项 2 —— 试听片段显式标注**

1. 搜索 `Taylor Swift`（§1.3 实测该关键词匿名态全是 30 秒试听）。
2. 点第一首。
3. 判定：播放页出现「试听片段 · 登录解锁完整版」，迷你条副标题带「试听片段」，且进度条总长约为 0:30。

```bash
$ADB shell dumpsys media_session | grep -E "state=|position=" | head -5
```

预期：`position` 在 30 秒内封顶（本次以视觉标注为准，30 秒后自动结束下一次）。

- [ ] **Step 4: 验收项 3 —— 切后台不断**

1. 播放中按 Home 键回桌面（或 `$ADB shell input keyevent KEYCODE_HOME`）。
2. 等 10 秒后回 App。
3. 判定：进度条继续前进（说明播放没被打断），迷你条仍在。

```bash
$ADB shell dumpsys media_session | grep "state="
```

预期：离开 App 期间仍为 `state=PLAYING`。

- [ ] **Step 5: 验收项 4 —— 通知栏能控制**

1. 播放中下拉通知栏，找到音乐通知（标题为当前歌曲、含封面）。
2. 点通知上的暂停 → 判定：声音停、App 内迷你条变暂停图标；再点播放 → 恢复。
3. 点下一首 / 上一首 → 判定：曲目切换，界面同步。
4. `$ADB shell settings get secure enabled_notification_listeners` 不是本任务判据，以肉眼与 media_session 为准：

```bash
$ADB shell dumpsys media_session | grep -E "state=|next|previous" | head -10
```

- [ ] **Step 6: 验收项 5 —— 队列与播放模式**

1. 打开播放页 → 「队列」→ 看到整套搜索结果（30 首）。
2. 点队列里第 5 首 → 判定：立刻切过去播。
3. 「移除」一首 → 判定：列表数量减 1。
4. 点「模式」轮换三次 → 判定：顺序 → 单曲循环 → 随机 → 顺序，且随机模式下下一首不是顺位。

- [ ] **Step 7: 稳定性检查**

```bash
$ADB logcat -d | grep -iE "FATAL EXCEPTION|AndroidRuntime.*Exception" | tail -20
```

预期：**无任何 FATAL EXCEPTION**。出现 `ExoPlaybackException` 之类的非崩溃日志属正常（例如单曲试听结束）。

- [ ] **Step 8: 提交验收记录**

```bash
git commit --allow-empty -m "test: MI-2 verified on device - playback, background and notification"
```

---

## 3. 自查（Self-Review）

### 3.1 Spec 覆盖

| 设计文档 §11 里 MI-2 的要求 | 落在哪个 Task |
|---|---|
| 在线播放（点歌出声） | Task 1 取流 + Task 4 服务 + Task 6 点歌（`PlayerViewModel.play`） |
| 播放页 | Task 6 Step 6（`PlayerScreen`） |
| 队列（顺序 / 单曲循环 / 随机，可查看可删） | Task 2 模式 + Task 5 映射 + Task 6 Step 7（`QueueSheet` + `removeQueueItem`） |
| 后台播放 / 通知栏 / 锁屏 / 耳机线控 | Task 4（Media3 `MediaSessionService`，框架自带） |
| 游客"试听片段"显式标注（§6、§9） | Task 1 `isTrial` → Task 5 `PlaybackUiState.isTrial` → Task 6 播放页与迷你条文案 |
| 播放地址短期缓存（§8 缓存） | Task 3（`SongStreamCache`，TTL 10 分钟，短于服务端 `expi=1200`） |
| 失败不抛到 UI 层（§9） | Task 1 `Result` + `SourceError.Unplayable`；Task 5 `onPlayerError` 提示并跳下一首；Task 6 `toUserMessage` |

**刻意不在 MI-2 范围内**（有 spec 依据，不是遗漏）：登录/扫码（MI-4）、歌词任何逻辑（MI-3）、收藏与历史（MI-4）、
断网指数退避重试与备用源（MI-4）、桌面歌词与小组件（§2.2 非目标）、音质切换 UI（§2.2 非目标）。

### 3.2 占位符扫描

已检查：本计划没有 `TBD` / `TODO` / "implement later" / "add appropriate error handling" 之类表述；
每个改代码的步骤都给了可直接粘贴的完整代码（含 import 调整说明）。

### 3.3 类型与命名一致性

已逐项核对，以下名字在所有 Task 中保持一致：

| 名字 | 定义处 | 使用处 |
|---|---|---|
| `StreamInfo(url, bitrateBps, isTrial)` | Task 1 Step 3 | Task 1 Step 4/5、Task 3 测试 |
| `SourceError.Unplayable` | Task 1 Step 3 | Task 1 测试、Task 6 `toUserMessage` |
| `SongUrlRespDto` / `SongUrlDto` / `FreeTrialDto` / `toStreamInfo()` | Task 1 Step 4 | Task 1 Step 5 |
| `PlayMode.ORDER/REPEAT_ONE/SHUFFLE`、`.label`、`.next()` | Task 2 Step 3 | Task 5 `applyTo`、Task 6 VM/UI |
| `SongUri.placeholder/songIdOf` | Task 2 Step 4 | Task 4 `resolveUri`、Task 6 VM |
| `SongStreamCache` / `sharedSongStreamCache` | Task 3 Step 3 | Task 4 服务、Task 5 `refresh`、Task 6 VM |
| `PlayMode.applyTo(player)` | Task 5 Step 1 | Task 6 VM |
| `PlaybackUiState`（含 `currentSongId`） | Task 5 Step 2 + Task 6 Step 3 | Task 6 全部 UI |
| `QueueItem(index,title,artist,isCurrent)` | Task 5 Step 2 | Task 6 `QueueSheet` |
| `PlaybackConnection.connect/release/awaitController/clearError/playMode` | Task 5 Step 2 | Task 6 VM 与 `MainActivity` |
| `PlayerViewModel.play/togglePlay/next/previous/seekTo/selectQueueItem/cyclePlayMode/removeQueueItem` | Task 6 Step 4 | Task 6 Step 9 装配 |
| `SearchUiState.nowPlayingSongId` / `onNowPlayingChanged` | Task 6 Step 8 | Task 6 Step 9 装配 |
| `toUserMessage()` | Task 6 Step 1 | `SearchViewModel` 与 `PlayerViewModel` |

**一处已知的简化**（刻意为之，不是疏漏）：`sharedSongStreamCache` 是进程内共享实例，
让 UI 无需自建跨组件通道就能读到"当前这首是不是试听片段"。本项目是单进程个人自用 App，
该简化可接受；若将来要做多进程或跨版本兼容，应改为通过 `MediaSession` 自定义命令下发。

---

## 4. 下一步

计划已完成。按用户已选定的**方式 1（Subagent-Driven）**执行：每个 Task 派一个全新 subagent 实现，
我在 Task 之间做两阶段审查（先看是否真的改了应改的文件、再跑构建与单测确认）。
MI-2 验收通过后进入 MI-3（YRC 解析 + LyricTimeline + 逐帧驱动 + 逐字动画）。
