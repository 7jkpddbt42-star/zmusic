# MI-1 打通接口（项目骨架 + 网易云 eapi 签名 + 搜索）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建起一个能编译运行的原生安卓工程，并在 Kotlin 端上完成网易云 `eapi` 签名，跑通"输入关键词 → 列出真实搜索结果"，以此验证整个在线音源方案可行。

**Architecture:** 单 Gradle 模块 + 严格内部分包。本计划只落地 `model` / `source` 两层和最小 `ui`：
`ui` → 只依赖 `source` 暴露的 `MusicProvider` 接口；`source/netease` 内部自包含（签名 → 组请求 → 解析响应），通过注入的 `HttpTransport` 与网络解耦，便于离线单测。

**Tech Stack:** Kotlin 2.4.21 · Jetpack Compose（BOM 2026.09.00）· AGP 9.4.1 · Gradle 9.8.1 · OkHttp 5.5.0 · Gson 2.11.0 · JUnit 4 · minSdk 26 / targetSdk 36 / compileSdk 37。

---

## 0. 前置条件（必须先读）

### 0.1 本机环境现状（已实测）

| 项 | 状态 |
|---|---|
| JDK | 21.0.12（`java -version` 已确认） |
| Android SDK | `/Users/zhuguanyu/Library/Android/sdk` |
| platforms | `android-36`、`android-37.0` |
| build-tools | `36.0.0` |
| platform-tools (adb) | 有，`adb devices` 当前为空 |
| **emulator 组件** | **缺失** |
| **系统镜像 system-images** | **缺失** |

**结论：本计划所有代码步骤都能跑通"编译 + 单元测试"，但 Task 7 的"真机验收"在你补上运行环境之前无法完成。**

解决方式（二选一，MI-1 验收前完成）：

1. 装模拟器：
   ```bash
   /Users/zhuguanyu/Library/Android/sdk/cmdline-tools/latest/bin/sdkmanager \
     "emulator" "system-images;android-36;google_apis;arm64-v8a"
   /Users/zhuguanyu/Library/Android/sdk/cmdline-tools/latest/bin/avdmanager \
     create avd -n mi1 -k "system-images;android-36;google_apis;arm64-v8a"
   ```
   （约 1.5GB 下载）
2. 或用 USB 接一台开了调试的安卓真机，`adb devices` 能看到设备即可。

### 0.2 项目根目录

本计划所有路径以 **`/Users/zhuguanyu/Documents/Trae-CN/temp_20261015_01`** 为根（与设计文档 `docs/superpowers/specs/2026-10-15-android-online-music-player-design.md` 同目录）。
下文所有 `app/...`、`gradle/...` 均指该根目录下的相对路径。

> 注意：该目录本身不是独立 git 仓库（它落在上层一个较大的仓库里）。Task 1 会显式建独立仓库，避免误提交无关文件。

---

## 1. 已核实的接口事实（**不要凭记忆改动**）

以下全部从本机 `/Applications/Folia.app/Contents/Resources/app.asar` 内打包的
`@neteasecloudmusicapienhanced/api` 源码中直接提取并交叉验证，属一手事实。

**关键修正**：Folia 这一代用的是 **eapi**（`/eapi/...`），**不是**老的 weapi `/weapi/...`。
所以本项目 MI-1 实现的是 eapi 签名，不是我们最初设想的 weapi。

### 1.1 加密常量

| 名称 | 值 |
|---|---|
| eapi AES 密钥 | `e82ckenh8dichen8`（16 字节 ASCII） |
| AES 模式 | `AES/ECB/PKCS5Padding`（PKCS5 == PKCS7） |
| params 编码 | **大写十六进制**（不是 base64） |
| URL/body 分隔符 | `-36cd479b6b5-` |
| 摘要 | `MD5`，**小写十六进制** |
| 域名 | `https://interfacepc.music.163.com` |
| UA | `NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)` |

（参考：weapi 那套常量——`presetKey=0CoJUm6Qyw8W8jud`、`iv=0102030405060708`、RSA 模数 `00e0b509…`、指数 `010001`——仍存在于该源码里，但**本计划不使用**，留作以后需要时参考。）

### 1.2 eapi 签名算法（逐行等价）

JS 原实现：

```js
const eapi = (url, object) => {
  const text = JSON.stringify(object)
  const message = `nobody${url}use${text}md5forencrypt`
  const digest = CryptoJS.MD5(message).toString()
  const data = `${url}-36cd479b6b5-${text}-36cd479b6b5-${digest}`
  return { params: aesEncrypt(data, 'ecb', eapiKey, '', 'hex') }
}
```

要点：**`text` 与 `digest` 由同一个字符串自洽生成**，所以 Kotlin 侧可以自己决定 JSON 的键顺序——
只要保证"我发的 text"和"我算的 digest"来自同一份文本即可，服务端会从 payload 里抠出 text 自行重算校验。

### 1.3 请求形态

- 方法：`POST`
- URL：`https://interfacepc.music.163.com/eapi/cloudsearch/pc`
  （规则：模块里的 uri `/api/cloudsearch/pc` → `/eapi/` + `uri.substring(5)`）
- Body：`params=<大写HEX>`，`Content-Type: application/x-www-form-urlencoded;charset=utf-8`
- 请求体里被加密的 JSON 结构（搜索）：

  ```json
  {
    "s": "<关键词>",
    "type": 1,
    "limit": 30,
    "offset": 0,
    "total": true,
    "e_r": false,
    "header": { "<见 1.4>" }
  }
  ```

- `e_r` 默认 **false**（配置 `encryptResponse: false`）→ **响应是明文 JSON，不需要解密**。
  这是本计划能大幅简化的关键。

### 1.4 Cookie / header 字段

eapi 的 `Cookie` 头直接由 `header` 对象按 `encodeURIComponent(k)=encodeURIComponent(v)` 用 `; ` 拼接而成，
`header` 同时被塞进上面的加密 JSON 里。字段与默认值（`os=pc` 那组）：

| 字段 | 值 |
|---|---|
| `osver` | `Microsoft-Windows-10-Professional-build-19045-64bit` |
| `deviceId` | 本机随机生成并持久化（32 位大写 HEX） |
| `os` | `pc` |
| `appver` | `3.1.17.204416` |
| `versioncode` | `140` |
| `mobilename` | ``（空） |
| `buildver` | 当前时间戳前 10 位 |
| `resolution` | `1920x1080` |
| `__csrf` | ``（未登录为空） |
| `channel` | `netease` |
| `requestId` | `<毫秒时间戳>_<0~999 补 4 位>` |

> 第一版**不带** `NMTID`/`_ntes_nuid`/`WNMCID`/`MUSIC_A`。原实现首次请求也不带 `NMTID`（服务端会通过
> `Set-Cookie` 下发），而搜索匿名可用。把这些补齐属于 MI-4 的"备用源/容错"范围。

### 1.5 搜索响应字段（已核实）

`result.songs[]`，歌曲字段用 **`ar`（歌手数组）/ `al`（专辑对象，含 `picUrl`）/ `dt`（时长毫秒）**。
依据：Folia 前端模板里就是 `track.al.picUrl`。

---

## 2. 文件结构（本计划创建的文件及其职责）

```
<root>/                                    项目根
├─ settings.gradle.kts                     仓库与模块声明
├─ build.gradle.kts                        插件版本声明
├─ gradle.properties                       构建参数
├─ local.properties                        SDK 路径（不入库）
├─ .gitignore
├─ gradlew / gradlew.bat                   从已验证工程拷贝
├─ gradle/wrapper/gradle-wrapper.jar       从已验证工程拷贝
├─ gradle/wrapper/gradle-wrapper.properties
└─ app/
   ├─ build.gradle.kts                     模块依赖
   ├─ proguard-rules.pro
   └─ src/
      ├─ main/
      │  ├─ AndroidManifest.xml
      │  ├─ java/com/musicbox/app/
      │  │  ├─ MainActivity.kt             入口
      │  │  ├─ AppContainer.kt             极简依赖装配（deviceId/Transport/Provider）
      │  │  ├─ model/
      │  │  │  ├─ Song.kt                  统一歌曲模型
      │  │  │  ├─ SearchType.kt            搜索类型枚举
      │  │  │  ├─ StreamInfo.kt            播放地址模型（MI-2 用，先定契约）
      │  │  │  └─ LyricData.kt             歌词模型（MI-3 用，先定契约）
      │  │  ├─ source/
      │  │  │  ├─ SourceError.kt           统一失败原因
      │  │  │  ├─ MusicProvider.kt         音源统一接口
      │  │  │  └─ netease/
      │  │  │     ├─ NetEaseCrypto.kt      eapi 签名（本项目最关键单元）
      │  │  │     ├─ NetEaseHeader.kt      header/Cookie 构造 + deviceId
      │  │  │     ├─ HttpTransport.kt      网络抽象（可测）
      │  │  │     ├─ OkHttpTransport.kt    真实实现
      │  │  │     ├─ NetEaseDto.kt         响应 DTO
      │  │  │     └─ NetEaseProvider.kt    组装：签名→发送→解析
      │  │  └─ ui/search/
      │  │     ├─ SearchViewModel.kt       搜索状态机
      │  │     └─ SearchScreen.kt          Compose 搜索界面
      │  └─ res/values/strings.xml, themes.xml
      └─ test/
         ├─ java/com/musicbox/app/source/netease/
         │  ├─ NetEaseCryptoTest.kt        签名已知向量
         │  ├─ NetEaseHeaderTest.kt        Cookie 拼接
         │  └─ NetEaseProviderTest.kt      离线端到端（假 Transport）
         └─ resources/search_response.json 固定响应样本
```

---

## Task 1: 工程骨架 + 最小 Compose 应用（编译通过）

**Files:**
- Create: `settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`local.properties`、`.gitignore`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `app/build.gradle.kts`、`app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`、`app/src/main/res/values/themes.xml`
- Create: `app/src/main/java/com/musicbox/app/MainActivity.kt`

- [ ] **Step 1: 建目录并拷贝已验证可用的 gradle wrapper**

这一步复用本机**已经成功构建过**的 wrapper（`织网` 工程），避免 wrapper 版本踩坑。

```bash
cd /Users/zhuguanyu/Documents/Trae-CN/temp_20261015_01
mkdir -p app/src/main/java/com/musicbox/app app/src/main/res/values gradle/wrapper
cp /Users/zhuguanyu/Documents/Trae-CN/temp_20261011_01/gradlew .
cp /Users/zhuguanyu/Documents/Trae-CN/temp_20261011_01/gradlew.bat .
cp /Users/zhuguanyu/Documents/Trae-CN/temp_20261011_01/gradle/wrapper/gradle-wrapper.jar gradle/wrapper/
chmod +x gradlew
```

- [ ] **Step 2: 写 `gradle/wrapper/gradle-wrapper.properties`**

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-9.8.1-bin.zip
networkTimeout=120000
retries=3
retryBackOffMs=500
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 3: 写根构建脚本**

`settings.gradle.kts`：

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "musicbox"
include(":app")
```

`build.gradle.kts`：

```kotlin
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
```

`gradle.properties`：

```properties
org.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true

android.useAndroidX=true
kotlin.code.style=official
```

`local.properties`（指向本机 SDK；该文件不入库）：

```properties
sdk.dir=/Users/zhuguanyu/Library/Android/sdk
```

`.gitignore`：

```
*.iml
.gradle
/local.properties
/.idea
.DS_Store
/build
/captures
.externalNativeBuild
.cxx
local.properties
app/build
```

- [ ] **Step 4: 写模块构建脚本 `app/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.musicbox.app"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.musicbox.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
```

`app/proguard-rules.pro`（MI-1 不混淆，先留空占位文件即可，内容如下）：

```
# MI-1 阶段 release 未开启混淆，此文件留作后续规则入口。
```

- [ ] **Step 5: 写清单、主题与最简 Activity**

`app/src/main/AndroidManifest.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:label="@string/app_name"
        android:theme="@style/Theme.MusicBox"
        android:supportsRtl="true">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/res/values/strings.xml`：

```xml
<resources>
    <string name="app_name">musicbox</string>
</resources>
```

`app/src/main/res/values/themes.xml`：

```xml
<resources>
    <style name="Theme.MusicBox" parent="@android:style/Theme.Material.NoActionBar">
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>
```

`app/src/main/java/com/musicbox/app/MainActivity.kt`：

```kotlin
package com.musicbox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Placeholder()
                }
            }
        }
    }
}

@Composable
private fun Placeholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("musicbox", style = MaterialTheme.typography.titleLarge)
    }
}
```

> `darkScheme()` 的引用在 Task 7 里定义（`ui/Theme.kt`）。为了让 Task 1 独立可编译，这里先内联：

```kotlin
package com.musicbox.app

import androidx.compose.material3.darkColorScheme

@Composable
internal fun darkScheme() = darkColorScheme()
```

把上面这段放进 `app/src/main/java/com/musicbox/app/ui/Theme.kt`（Task 7 会扩展它，MI-1 先给这一行）。

- [ ] **Step 6: 编译验证**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`；产物 `app/build/outputs/apk/debug/app-debug.apk` 存在。

- [ ] **Step 7: 建独立仓库并提交**

```bash
cd /Users/zhuguanyu/Documents/Trae-CN/temp_20261015_01
git init
git add .gitignore gradlew gradlew.bat gradle settings.gradle.kts build.gradle.kts gradle.properties app
git commit -m "chore: scaffold android compose project for MI-1"
```

---

## Task 2: 统一模型与音源契约

**Files:**
- Create: `app/src/main/java/com/musicbox/app/model/Song.kt`
- Create: `app/src/main/java/com/musicbox/app/model/SearchType.kt`
- Create: `app/src/main/java/com/musicbox/app/model/StreamInfo.kt`
- Create: `app/src/main/java/com/musicbox/app/model/LyricData.kt`
- Create: `app/src/main/java/com/musicbox/app/source/SourceError.kt`
- Create: `app/src/main/java/com/musicbox/app/source/MusicProvider.kt`
- Test: `app/src/test/java/com/musicbox/app/model/SearchTypeTest.kt`

- [ ] **Step 1: 先写搜索类型参数的失败测试**

`app/src/test/java/com/musicbox/app/model/SearchTypeTest.kt`：

```kotlin
package com.musicbox.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTypeTest {

    @Test
    fun `search type maps to netease apiValue`() {
        assertEquals(1, SearchType.SONG.apiValue)
        assertEquals(10, SearchType.ALBUM.apiValue)
        assertEquals(100, SearchType.ARTIST.apiValue)
    }
}
```

- [ ] **Step 2: 运行，确认失败（类不存在）**

Run: `./gradlew :app:testDebugUnitTest --tests "com.musicbox.app.model.SearchTypeTest"`
Expected: 编译失败，提示 `Unresolved reference: SearchType`。

- [ ] **Step 3: 写模型与契约**

`model/SearchType.kt`：

```kotlin
package com.musicbox.app.model

/** 网易云搜索类型：单曲 / 专辑 / 歌手。数值即接口的 type 参数。 */
enum class SearchType(val apiValue: Int) {
    SONG(1),
    ALBUM(10),
    ARTIST(100),
}
```

`model/Song.kt`：

```kotlin
package com.musicbox.app.model

/** 统一歌曲模型，各音源都要映射到它。 */
data class Song(
    val id: Long,
    val name: String,
    val artists: List<String>,
    val album: String?,
    val durationMs: Long,
    val coverUrl: String?,
    val providerId: String,
)
```

`model/StreamInfo.kt`：

```kotlin
package com.musicbox.app.model

/** 播放地址信息。MI-2 起由音源填充。 */
data class StreamInfo(
    val url: String,
    val bitrateBps: Int,
)
```

`model/LyricData.kt`：

```kotlin
package com.musicbox.app.model

/**
 * 歌词原始载荷。MI-3 起由音源填充并解析为时间轴。
 * 三个字段都为原始文本，解析职责在 lyric 层，音源不解析。
 */
data class LyricData(
    val yrc: String?,          // 逐字歌词（YRC）
    val lrc: String?,          // 逐行歌词（LRC）
    val translation: String?,  // 翻译
    val romaji: String?,       // 罗马音
)
```

`source/SourceError.kt`：

```kotlin
package com.musicbox.app.source

/** 音源统一失败原因。绝不把底层异常直接抛给 UI。 */
sealed class SourceError(message: String? = null, cause: Throwable? = null) :
    Exception(message, cause) {

    /** 网络/连接层失败。 */
    class Network(cause: Throwable? = null) : SourceError("网络不可用", cause)

    /** 接口返回了非成功 code。 */
    class Api(val code: Int, message: String?) : SourceError(message ?: "接口返回 $code")

    /** 响应解析失败（结构变化或脏数据）。 */
    class Parse(message: String?, cause: Throwable? = null) : SourceError(message, cause)

    /** 当前里程碑尚未实现的接口。 */
    class NotImplementedYet(val milestone: String) :
        SourceError("该能力计划在 $milestone 实现")
}
```

`source/MusicProvider.kt`：

```kotlin
package com.musicbox.app.source

import com.musicbox.app.model.LyricData
import com.musicbox.app.model.SearchType
import com.musicbox.app.model.Song
import com.musicbox.app.model.StreamInfo

/**
 * 音源统一抽象（设计文档 §4.3）。每个平台一个实现，音源之间互不依赖。
 * 所有方法返回 Result<T>，失败时异常类型固定为 [SourceError]。
 */
interface MusicProvider {

    val id: String

    /** 搜索。type 指定歌曲/专辑/歌手，page 从 1 开始。 */
    suspend fun search(keyword: String, type: SearchType, page: Int): Result<List<Song>>

    /** 取播放地址。MI-2 实现。 */
    suspend fun getStreamUrl(songId: Long): Result<StreamInfo>

    /** 取歌词（逐字 YRC + 译文 + 罗马音）。MI-3 实现。 */
    suspend fun getLyric(songId: Long): Result<LyricData>
}
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.musicbox.app.model.SearchTypeTest"`
Expected: `BUILD SUCCESSFUL`，1 个测试通过。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/musicbox/app/model app/src/main/java/com/musicbox/app/source app/src/test/java/com/musicbox/app/model
git commit -m "feat: add unified models and MusicProvider contract"
```

---

## Task 3: eapi 签名器（**本里程碑最关键单元**）

**Files:**
- Create: `app/src/main/java/com/musicbox/app/source/netease/NetEaseCrypto.kt`
- Test: `app/src/test/java/com/musicbox/app/source/netease/NetEaseCryptoTest.kt`

下面的期望值是**用 openssl 独立算出并做过解密回环验证**的，不是猜的。

- [ ] **Step 1: 写失败测试（已知向量）**

`app/src/test/java/com/musicbox/app/source/netease/NetEaseCryptoTest.kt`：

```kotlin
package com.musicbox.app.source.netease

import org.junit.Assert.assertEquals
import org.junit.Test

class NetEaseCryptoTest {

    @Test
    fun `md5 returns lowercase hex`() {
        assertEquals(
            "900150983cd24fb0d6963f7d28e17f72",
            NetEaseCrypto.md5Hex("abc"),
        )
    }

    @Test
    fun `aes128EcbPkcs7 returns uppercase hex`() {
        assertEquals(
            "3329CABA8FB561FF0086A5823D14F529",
            NetEaseCrypto.aes128EcbPkcs7Hex(NetEaseCrypto.EAPI_KEY, "hello"),
        )
    }

    @Test
    fun `eapiParams matches known vector (ascii)`() {
        val text = """{"s":"hello","type":1,"limit":30,"offset":0,"total":true,"e_r":false,"header":{}}"""
        assertEquals(
            "2B5D64177AA6460FBAA3DCB1285E28954BBB4F7556E09B0FB25750F12398BB50" +
                "FCCCA3BBD139F9F4CFD33CF4D5C3FD2E6B6A421FBF3C9F27628217E612454733" +
                "266459E5F9F3C9B60A2B275D0A17AB340EA7C9AC352BE2430014FF1A9D81964D" +
                "4BFD64C4A08E9C0CD130F2B72E3AAB1E2BA5716B5036AD446E7BCF5FDE0B92F6" +
                "722B4BB7C225A22A4A59F729599859FC76B0218F40FA6C7143E91FF69E404D94",
            NetEaseCrypto.eapiParams("/api/cloudsearch/pc", text),
        )
    }

    @Test
    fun `eapiParams handles utf8 (chinese) correctly`() {
        val text = """{"s":"海阔天空","type":1,"limit":30,"offset":0,"total":true,"e_r":false,"header":{}}"""
        assertEquals(
            "2B5D64177AA6460FBAA3DCB1285E28954BBB4F7556E09B0FB25750F12398BB50" +
                "327BB1B330EEC8D8681261926D2FE69CB676D727589A690810B7C3D0C2835446" +
                "D753A81DA5F0FA39074199F024F0F2E407C432A9B3ECF4ECA5FBFBD501E02684" +
                "2A0BE5EE12C5ACDB01AECD5781C70154D6515A806FBA5C2B47F32E7D47D12864" +
                "3557228FF76C8FFC7CCDA7AE5778AEEF4BE1613C84A7AD720DCC33222A157581" +
                "421CB711F5002053AA0D3F07EB593C9B",
            NetEaseCrypto.eapiParams("/api/cloudsearch/pc", text),
        )
    }
}
```

- [ ] **Step 2: 运行，确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.musicbox.app.source.netease.NetEaseCryptoTest"`
Expected: 编译失败，提示 `Unresolved reference: NetEaseCrypto`。

- [ ] **Step 3: 实现签名器**

`app/src/main/java/com/musicbox/app/source/netease/NetEaseCrypto.kt`：

```kotlin
package com.musicbox.app.source.netease

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 网易云 eapi 签名。
 *
 * 事实依据：Folia 所打包的 neteasecloudmusicapienhanced/api 中 encrypt.eapi：
 *   text    = JSON.stringify(data)
 *   digest  = MD5("nobody" + uri + "use" + text + "md5forencrypt")      // 小写 hex
 *   payload = uri + "-36cd479b6b5-" + text + "-36cd479b6b5-" + digest
 *   params  = AES-128-ECB/PKCS7(payload, "e82ckenh8dichen8") 的大写 hex
 */
object NetEaseCrypto {

    /** eapi 的 AES-128 密钥。 */
    const val EAPI_KEY = "e82ckenh8dichen8"

    /** payload 中 URL 与 JSON 文本之间的固定分隔符。 */
    const val EAPI_SEP = "-36cd479b6b5-"

    private const val MD5_SUFFIX = "md5forencrypt"
    private const val MESSAGE_PREFIX = "nobody"

    fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.toHexLower()
    }

    /** AES-128-ECB + PKCS7，输出大写十六进制。 */
    fun aes128EcbPkcs7Hex(key: String, plain: String): String {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        require(keyBytes.size == 16) { "eapi key must be 16 bytes, got ${keyBytes.size}" }
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"))
        return cipher.doFinal(plain.toByteArray(Charsets.UTF_8)).toHexUpper()
    }

    /** 生成请求体里的 params（大写 hex）。 */
    fun eapiParams(uri: String, jsonText: String): String {
        val digest = md5Hex(MESSAGE_PREFIX + uri + "use" + jsonText + MD5_SUFFIX)
        val payload = uri + EAPI_SEP + jsonText + EAPI_SEP + digest
        return aes128EcbPkcs7Hex(EAPI_KEY, payload)
    }

    private fun ByteArray.toHexUpper(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xFF) }.uppercase()

    private fun ByteArray.toHexLower(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
```

- [ ] **Step 4: 运行测试，确认全部通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.musicbox.app.source.netease.NetEaseCryptoTest"`
Expected: `BUILD SUCCESSFUL`，4 个测试通过。若中文向量失败，几乎一定是 UTF-8 编码问题，检查 `Charsets.UTF_8`。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/musicbox/app/source/netease/NetEaseCrypto.kt app/src/test/java/com/musicbox/app/source/netease/NetEaseCryptoTest.kt
git commit -m "feat: implement netease eapi signature with verified vectors"
```

---

## Task 4: header/Cookie 构造与网络抽象

**Files:**
- Create: `app/src/main/java/com/musicbox/app/source/netease/NetEaseHeader.kt`
- Create: `app/src/main/java/com/musicbox/app/source/netease/HttpTransport.kt`
- Create: `app/src/main/java/com/musicbox/app/source/netease/OkHttpTransport.kt`
- Test: `app/src/test/java/com/musicbox/app/source/netease/NetEaseHeaderTest.kt`

- [ ] **Step 1: 写失败测试**

`app/src/test/java/com/musicbox/app/source/netease/NetEaseHeaderTest.kt`：

```kotlin
package com.musicbox.app.source.netease

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetEaseHeaderTest {

    @Test
    fun `cookie header joins entries with percent encoding`() {
        val header = linkedMapOf(
            "deviceId" to "ABCD",
            "os" to "pc",
        )
        assertEquals("deviceId=ABCD; os=pc", NetEaseHeader.cookieHeader(header))
    }

    @Test
    fun `generated device id is 32 uppercase hex chars`() {
        val id = NetEaseHeader.newDeviceId()
        assertEquals(32, id.length)
        assertTrue(id.all { it in '0'..'9' || it in 'A'..'F' })
    }

    @Test
    fun `build fills required eapi fields`() {
        val header = NetEaseHeader.build(deviceId = "ABCD")
        assertEquals("pc", header["os"])
        assertEquals("140", header["versioncode"])
        assertEquals("1920x1080", header["resolution"])
        assertEquals("netease", header["channel"])
        assertEquals("ABCD", header["deviceId"])
        assertTrue(header.containsKey("requestId"))
        assertTrue(header.containsKey("buildver"))
    }
}
```

- [ ] **Step 2: 运行，确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.musicbox.app.source.netease.NetEaseHeaderTest"`
Expected: 编译失败，`Unresolved reference: NetEaseHeader`。

- [ ] **Step 3: 实现 header 与网络抽象**

`app/src/main/java/com/musicbox/app/source/netease/NetEaseHeader.kt`：

```kotlin
package com.musicbox.app.source.netease

import java.net.URLEncoder
import java.security.SecureRandom

/**
 * eapi 请求的 header / Cookie 构造。
 *
 * 事实依据（同 NetEaseCrypto）：eapi 分支里 Cookie 直接由 header 对象
 * 经 encodeURIComponent(k)=encodeURIComponent(v) 以 "; " 连接而成，
 * 且同一个 header 对象会被塞进加密 JSON 的 header 字段。
 */
object NetEaseHeader {

    const val UA = "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)"

    private const val OSVER = "Microsoft-Windows-10-Professional-build-19045-64bit"
    private const val APPVER = "3.1.17.204416"
    private const val VERSION_CODE = "140"
    private const val RESOLUTION = "1920x1080"
    private const val CHANNEL = "netease"

    private val random = SecureRandom()

    /** 生成 32 位大写 HEX 设备号，应由调用方持久化以保证身份稳定。 */
    fun newDeviceId(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
    }

    /** 构造 eapi header。未登录时 csrf 为空串。 */
    fun build(deviceId: String, csrf: String = ""): Map<String, String> {
        val now = System.currentTimeMillis()
        return linkedMapOf(
            "osver" to OSVER,
            "deviceId" to deviceId,
            "os" to "pc",
            "appver" to APPVER,
            "versioncode" to VERSION_CODE,
            "mobilename" to "",
            "buildver" to now.toString().take(10),
            "resolution" to RESOLUTION,
            "__csrf" to csrf,
            "channel" to CHANNEL,
            "requestId" to "${now}_${random.nextInt(1000).toString().padStart(4, '0')}",
        )
    }

    fun cookieHeader(header: Map<String, String>): String =
        header.entries.joinToString("; ") { (key, value) ->
            encode(key) + "=" + encode(value)
        }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
```

`app/src/main/java/com/musicbox/app/source/netease/HttpTransport.kt`：

```kotlin
package com.musicbox.app.source.netease

/** 一次 HTTP 往返的结果。 */
data class TransportResponse(val code: Int, val body: String)

/**
 * 网络传输抽象。抽出来是为了让 NetEaseProvider 能在单测里注入假实现，
 * 从而不依赖外网跑完整链路。
 */
interface HttpTransport {
    fun post(url: String, body: String, headers: Map<String, String>): TransportResponse
}
```

`app/src/main/java/com/musicbox/app/source/netease/OkHttpTransport.kt`：

```kotlin
package com.musicbox.app.source.netease

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class OkHttpTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
) : HttpTransport {

    private val formType =
        "application/x-www-form-urlencoded;charset=utf-8".toMediaType()

    override fun post(url: String, body: String, headers: Map<String, String>): TransportResponse {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(formType))
            .apply { headers.forEach { (key, value) -> addHeader(key, value) } }
            .build()

        client.newCall(request).execute().use { response ->
            return TransportResponse(response.code, response.body.string())
        }
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.musicbox.app.source.netease.NetEaseHeaderTest"`
Expected: `BUILD SUCCESSFUL`，3 个测试通过。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/musicbox/app/source/netease/NetEaseHeader.kt \
        app/src/main/java/com/musicbox/app/source/netease/HttpTransport.kt \
        app/src/main/java/com/musicbox/app/source/netease/OkHttpTransport.kt \
        app/src/test/java/com/musicbox/app/source/netease/NetEaseHeaderTest.kt
git commit -m "feat: add eapi header builder and testable http transport"
```

---

## Task 5: 响应 DTO 与解析（离线固定样本）

**Files:**
- Create: `app/src/test/resources/search_response.json`
- Create: `app/src/main/java/com/musicbox/app/source/netease/NetEaseDto.kt`

- [ ] **Step 1: 放入固定响应样本**

`app/src/test/resources/search_response.json`：

```json
{
  "result": {
    "songCount": 2,
    "songs": [
      {
        "id": 347230,
        "name": "海阔天空",
        "ar": [{ "id": 6452, "name": "Beyond" }],
        "al": { "id": 34167, "name": "乐与怒", "picUrl": "https://p1.music.126.net/cover1.jpg" },
        "dt": 326000
      },
      {
        "id": 1330348068,
        "name": "起风了",
        "ar": [{ "id": 12085562, "name": "买辣椒也用券" }],
        "al": { "id": 74334058, "name": "起风了", "picUrl": "https://p2.music.126.net/cover2.jpg" },
        "dt": 325000
      }
    ]
  },
  "code": 200
}
```

- [ ] **Step 2: 写解析 DTO 与映射**

`app/src/main/java/com/musicbox/app/source/netease/NetEaseDto.kt`：

```kotlin
package com.musicbox.app.source.netease

import com.musicbox.app.model.Song
import com.google.gson.annotations.SerializedName

/** /eapi/cloudsearch/pc 的响应结构。字段名以真实响应为准（见计划 §1.5）。 */
internal data class SearchRespDto(
    @SerializedName("code") val code: Int = -1,
    @SerializedName("result") val result: SearchResultDto? = null,
)

internal data class SearchResultDto(
    @SerializedName("songCount") val songCount: Int = 0,
    @SerializedName("songs") val songs: List<SongDto>? = null,
)

internal data class SongDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("name") val name: String? = null,
    @SerializedName("ar") val artists: List<ArtistDto>? = null,
    @SerializedName("al") val album: AlbumDto? = null,
    @SerializedName("dt") val durationMs: Long = 0L,
)

internal data class ArtistDto(
    @SerializedName("name") val name: String? = null,
)

internal data class AlbumDto(
    @SerializedName("name") val name: String? = null,
    @SerializedName("picUrl") val picUrl: String? = null,
)

internal fun SongDto.toSong(providerId: String): Song = Song(
    id = id,
    name = name.orEmpty(),
    artists = artists?.mapNotNull { it.name } ?: emptyList(),
    album = album?.name,
    durationMs = durationMs,
    coverUrl = album?.picUrl,
    providerId = providerId,
)
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/musicbox/app/source/netease/NetEaseDto.kt app/src/test/resources/search_response.json
git commit -m "feat: add search response dto and song mapping"
```

---

## Task 6: NetEaseProvider 组装（含假 Transport 端到端测试）

**Files:**
- Create: `app/src/main/java/com/musicbox/app/source/netease/NetEaseProvider.kt`
- Test: `app/src/test/java/com/musicbox/app/source/netease/NetEaseProviderTest.kt`

- [ ] **Step 1: 写失败测试**

`app/src/test/java/com/musicbox/app/source/netease/NetEaseProviderTest.kt`：

```kotlin
package com.musicbox.app.source.netease

import com.musicbox.app.model.SearchType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetEaseProviderTest {

    private class FakeTransport(
        private val response: TransportResponse,
    ) : HttpTransport {
        var lastUrl: String? = null
        var lastBody: String? = null
        var lastHeaders: Map<String, String>? = null

        override fun post(url: String, body: String, headers: Map<String, String>): TransportResponse {
            lastUrl = url
            lastBody = body
            lastHeaders = headers
            return response
        }
    }

    private fun fixtureBody(): String =
        javaClass.classLoader!!
            .getResource("search_response.json")!!
            .readText()

    @Test
    fun `search posts params to eapi endpoint and maps songs`() = runBlocking {
        val transport = FakeTransport(TransportResponse(code = 200, body = fixtureBody()))
        val provider = NetEaseProvider(transport = transport, deviceId = "ABCD")

        val result = provider.search("海阔天空", SearchType.SONG, page = 1)

        assertTrue(result.isSuccess)
        val songs = result.getOrThrow()
        assertEquals(2, songs.size)

        val first = songs.first()
        assertEquals(347230L, first.id)
        assertEquals("海阔天空", first.name)
        assertEquals(listOf("Beyond"), first.artists)
        assertEquals("乐与怒", first.album)
        assertEquals(326000L, first.durationMs)
        assertEquals("https://p1.music.126.net/cover1.jpg", first.coverUrl)
        assertEquals("netease", first.providerId)

        // 请求本身长什么样
        assertEquals("https://interfacepc.music.163.com/eapi/cloudsearch/pc", transport.lastUrl)
        assertTrue(transport.lastBody!!.startsWith("params="))
        assertEquals(NetEaseHeader.UA, transport.lastHeaders!!["User-Agent"])
        assertNotNull(transport.lastHeaders!!["Cookie"])
        assertTrue(transport.lastHeaders!!["Cookie"]!!.contains("deviceId=ABCD"))
    }

    @Test
    fun `search returns failure when api code is not 200`() = runBlocking {
        val transport = FakeTransport(
            TransportResponse(code = 200, body = """{"code":-460,"msg":"cheating"}"""),
        )
        val provider = NetEaseProvider(transport = transport, deviceId = "ABCD")

        val result = provider.search("x", SearchType.SONG, page = 1)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is com.musicbox.app.source.SourceError.Api)
        assertEquals(-460, (error as com.musicbox.app.source.SourceError.Api).code)
    }

    @Test
    fun `search returns parse failure when body is not json`() = runBlocking {
        val transport = FakeTransport(TransportResponse(code = 200, body = "<html>oops</html>"))
        val provider = NetEaseProvider(transport = transport, deviceId = "ABCD")

        val result = provider.search("x", SearchType.SONG, page = 1)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is com.musicbox.app.source.SourceError.Parse)
    }
}
```

`kotlinx.coroutines.runBlocking` 需要协程依赖——在 `app/build.gradle.kts` 的 `dependencies` 里补一行：

```kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
```

- [ ] **Step 2: 运行，确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.musicbox.app.source.netease.NetEaseProviderTest"`
Expected: 编译失败，`Unresolved reference: NetEaseProvider`。

- [ ] **Step 3: 实现 Provider**

`app/src/main/java/com/musicbox/app/source/netease/NetEaseProvider.kt`：

```kotlin
package com.musicbox.app.source.netease

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.musicbox.app.model.LyricData
import com.musicbox.app.model.SearchType
import com.musicbox.app.model.Song
import com.musicbox.app.model.StreamInfo
import com.musicbox.app.source.MusicProvider
import com.musicbox.app.source.SourceError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URLEncoder

/**
 * 网易云音源（端上自包含签名）。
 *
 * 只负责：组 JSON → 签名 → 发请求 → 解析。
 * 不负责：备用源切换、重试策略、缓存（MI-4）。
 */
class NetEaseProvider(
    private val transport: HttpTransport,
    private val deviceId: String,
) : MusicProvider {

    override val id: String = "netease"

    private val gson = Gson()

    override suspend fun search(
        keyword: String,
        type: SearchType,
        page: Int,
    ): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val limit = SEARCH_PAGE_SIZE
            val payload = linkedMapOf<String, Any>(
                "s" to keyword,
                "type" to type.apiValue,
                "limit" to limit,
                "offset" to (page - 1).coerceAtLeast(0) * limit,
                "total" to true,
                "e_r" to false,
                "header" to NetEaseHeader.build(deviceId),
            )
            val uri = SEARCH_URI
            val jsonText = gson.toJson(payload)
            val params = NetEaseCrypto.eapiParams(uri, jsonText)
            val body = "params=" + URLEncoder.encode(params, "UTF-8")
            val headers = mapOf(
                "User-Agent" to NetEaseHeader.UA,
                "Cookie" to NetEaseHeader.cookieHeader(NetEaseHeader.build(deviceId)),
                "Referer" to "https://music.163.com",
            )

            val response = transport.post(EAPI_BASE + uri.replace("/api", "/eapi"), body, headers)

            val dto = try {
                gson.fromJson(response.body, SearchRespDto::class.java)
            } catch (e: JsonSyntaxException) {
                throw SourceError.Parse("响应不是合法 JSON", e)
            } ?: throw SourceError.Parse("响应为空", null)

            if (dto.code != 200) {
                throw SourceError.Api(dto.code, null)
            }

            val songs = dto.result?.songs.orEmpty().map { it.toSong(id) }
            Result.success(songs)
        } catch (e: SourceError) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(SourceError.Network(e))
        }
    }

    override suspend fun getStreamUrl(songId: Long): Result<StreamInfo> =
        Result.failure(SourceError.NotImplementedYet("MI-2"))

    override suspend fun getLyric(songId: Long): Result<LyricData> =
        Result.failure(SourceError.NotImplementedYet("MI-3"))

    private companion object {
        const val EAPI_BASE = "https://interfacepc.music.163.com"
        const val SEARCH_URI = "/api/cloudsearch/pc"
        const val SEARCH_PAGE_SIZE = 30
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.musicbox.app.source.netease.NetEaseProviderTest"`
Expected: `BUILD SUCCESSFUL`，3 个测试通过。

- [ ] **Step 5: 提交**

```bash
git add app/build.gradle.kts app/src/main/java/com/musicbox/app/source/netease/NetEaseProvider.kt \
        app/src/test/java/com/musicbox/app/source/netease/NetEaseProviderTest.kt
git commit -m "feat: implement netease provider search with offline end-to-end test"
```

---

## Task 7: 搜索界面接线 + 装配 + 真机验收

**Files:**
- Create: `app/src/main/java/com/musicbox/app/AppContainer.kt`
- Modify: `app/src/main/java/com/musicbox/app/ui/Theme.kt`
- Modify: `app/src/main/java/com/musicbox/app/MainActivity.kt`
- Create: `app/src/main/java/com/musicbox/app/ui/search/SearchViewModel.kt`
- Create: `app/src/main/java/com/musicbox/app/ui/search/SearchScreen.kt`

- [ ] **Step 1: 装配容器**

`app/src/main/java/com/musicbox/app/AppContainer.kt`：

```kotlin
package com.musicbox.app

import android.content.Context
import com.musicbox.app.source.MusicProvider
import com.musicbox.app.source.netease.NetEaseHeader
import com.musicbox.app.source.netease.NetEaseProvider
import com.musicbox.app.source.netease.OkHttpTransport

/**
 * 极简依赖装配。MI-4 之前不引入 DI 框架。
 * deviceId 持久化在私有 SharedPreferences，保证设备身份跨启动稳定。
 */
class AppContainer(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("musicbox", Context.MODE_PRIVATE)

    private val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null)
            ?: NetEaseHeader.newDeviceId().also {
                prefs.edit().putString(KEY_DEVICE_ID, it).apply()
            }

    val provider: MusicProvider = NetEaseProvider(
        transport = OkHttpTransport(),
        deviceId = deviceId,
    )

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
    }
}
```

- [ ] **Step 2: 补主题文件**

把 Task 1 里临时放的 `Theme.kt` 扩成这样：

`app/src/main/java/com/musicbox/app/ui/Theme.kt`：

```kotlin
package com.musicbox.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AppColors = darkColorScheme()

@Composable
internal fun MusicBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, content = content)
}
```

并把 `MainActivity.kt` 里的 `darkScheme()` 改成 `MusicBoxTheme { ... }`（见 Step 4）。

- [ ] **Step 3: ViewModel 与界面**

`app/src/main/java/com/musicbox/app/ui/search/SearchViewModel.kt`：

```kotlin
package com.musicbox.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicbox.app.model.SearchType
import com.musicbox.app.model.Song
import com.musicbox.app.source.MusicProvider
import com.musicbox.app.source.SourceError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<Song> = emptyList(),
    val message: String? = null,
    val searched: Boolean = false,
)

class SearchViewModel(
    private val provider: MusicProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun search() {
        val keyword = _state.value.query.trim()
        if (keyword.isEmpty()) return

        _state.update { it.copy(loading = true, message = null) }
        viewModelScope.launch {
            provider.search(keyword, SearchType.SONG, page = 1)
                .onSuccess { songs ->
                    _state.update {
                        it.copy(loading = false, results = songs, searched = true, message = null)
                    }
                }
                .onFailure { error ->
                    val text = when (error) {
                        is SourceError.Network -> "网络不可用，请检查网络"
                        is SourceError.Api -> "音源暂时不可用"
                        is SourceError.Parse -> "解析失败，可能接口已变更"
                        is SourceError.NotImplementedYet -> "该功能尚未实现"
                        else -> "出错了"
                    }
                    _state.update {
                        it.copy(loading = false, searched = true, message = text)
                    }
                }
        }
    }
}
```

`app/src/main/java/com/musicbox/app/ui/search/SearchScreen.kt`：

```kotlin
package com.musicbox.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                label = { Text("搜索歌曲") },
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSearch) { Text("搜索") }
        }

        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
        }

        state.message?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        if (state.searched && !state.loading && state.message == null && state.results.isEmpty()) {
            Text("没有找到结果", modifier = Modifier.padding(top = 16.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items(state.results, key = { it.id }) { song ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(
                        text = song.name,
                        style = MaterialTheme.typography.bodyLarge,
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
        }
    }
}
```

`collectAsStateWithLifecycle` 需要 `androidx.lifecycle:lifecycle-runtime-compose`，在 `app/build.gradle.kts` 里补：

```kotlin
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
```

- [ ] **Step 4: 入口接线**

`app/src/main/java/com/musicbox/app/MainActivity.kt` 全文替换为：

```kotlin
package com.musicbox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.musicbox.app.ui.MusicBoxTheme
import com.musicbox.app.ui.search.SearchScreen
import com.musicbox.app.ui.search.SearchViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = AppContainer(this)
        setContent {
            MusicBoxTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SearchRoute(container)
                }
            }
        }
    }
}

@Composable
private fun SearchRoute(container: AppContainer) {
    val vm: SearchViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SearchViewModel(container.provider) }
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    SearchScreen(
        state = state,
        onQueryChange = vm::onQueryChange,
        onSearch = vm::search,
    )
}
```

- [ ] **Step 5: 编译 + 全量单测**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`，全部单测通过。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/musicbox/app app/build.gradle.kts
git commit -m "feat: wire search screen to netease provider"
```

- [ ] **Step 7: 真机/模拟器验收（**需先补运行环境，见 §0.1**）**

```bash
# 确认设备在线
/Users/zhuguanyu/Library/Android/sdk/platform-tools/adb devices
# 安装并启动
./gradlew :app:installDebug
/Users/zhuguanyu/Library/Android/sdk/platform-tools/adb shell am start -n com.musicbox.app.debug/com.musicbox.app.MainActivity
```

Expected（手工核对）：
1. App 启动进入搜索页；
2. 输入「海阔天空」，点搜索，**能列出真实歌曲列表**（这是 MI-1 的验收标准）；
3. 输入乱码关键词不崩，显示"没有找到结果"；
4. 开飞行模式再搜，显示"网络不可用，请检查网络"而不是崩溃。

若第 2 步失败，按下面顺序排查：

| 现象 | 可能原因 | 检查点 |
|---|---|---|
| 提示"解析失败" | 响应字段名与 §1.5 不符 | 抓 `response.body` 日志，对照 `NetEaseDto.kt` 改注解 |
| 提示"音源暂时不可用"（Api） | code 非 200 | 打印 `dto.code`，-460 通常是风控 |
| 提示"网络不可用" | DNS/网络/超时 | 先 `curl` 一下 `https://interfacepc.music.163.com` |
| 签名被拒 | payload 生成有偏差 | 用 Task 3 的向量单测兜底，并核对 §1.2 |

- [ ] **Step 8: 验收通过后提交记录**

```bash
git commit --allow-empty -m "test: MI-1 verified on device - search returns real results"
```

---

## 3. 自查

**3.1 设计文档覆盖检查**

| 设计文档条目 | 本计划对应 |
|---|---|
| §2.1 搜索（歌曲/专辑/歌手） | Task 2（`SearchType`）、Task 6（`search`）、Task 7（界面，MI-1 只暴露单曲） |
| §3 技术选型（Kotlin/Compose/Gradle） | Task 1 |
| §4.1 分层 `model`/`source`/`ui` | Task 2、Task 6、Task 7；`lyric`/`player`/`data` 属后续里程碑 |
| §4.3 `MusicProvider` 契约 | Task 2（四方法齐全） |
| §7 方案 1（端上自包含签名） | Task 3、Task 4、Task 6 |
| §9 错误处理（`Result<T>`、不抛到 UI） | Task 2（`SourceError`）、Task 6、Task 7（错误文案） |
| §10 测试策略（签名向量 / 响应解析） | Task 3、Task 6 |
| §11 MI-1 里程碑 | 全篇 |
| §13 环境待办（缺 emulator） | §0.1 与 Task 7 Step 7 |

**未覆盖但属预期**：`getStreamUrl`（MI-2）、`getLyric`（MI-3）、收藏/历史/缓存/扫码登录（MI-4）、备用源（MI-4）——这些是后续里程碑，本计划只钉死契约。

**3.2 占位符扫描**

已逐条检查：无 "TODO"/"TBD"/"稍后补"；每个代码步骤都给了完整可编译代码；每个测试步骤都给了完整测试代码与期望结果。`proguard-rules.pro` 是空规则文件，属正常工程文件而非占位符。

**3.3 类型一致性检查**

- `MusicProvider` 三方法签名 ↔ `NetEaseProvider` 实现 ↔ Task 7 ViewModel 调用：一致（`search(keyword, type, page)`）。
- `SourceError` 五个子类 ↔ Task 7 文案 `when` 分支：`Network`/`Api`/`Parse`/`NotImplementedYet` 四个显式分支 + `else` 兜底，覆盖全部子类。
- `NetEaseCrypto.EAPI_KEY` 在 Task 3 测试与实现中同名同值。
- `NetEaseHeader.build/cookieHeader/newDeviceId/UA` 在 Task 4 定义、Task 6 使用：一致。
- `SongDto.toSong(providerId)` 在 Task 5 定义、Task 6 调用：一致。
- `AppContainer.provider` 类型 `MusicProvider` ↔ `SearchViewModel(provider: MusicProvider)`：一致。

---

## 4. 执行方式二选一

计划已完成并保存到 `docs/superpowers/plans/2026-10-15-mi1-netease-signature-and-search.md`。

- **方式 1（推荐）Subagent 驱动**：每个 Task 派一个全新 subagent 实现，我在 Task 之间做两阶段审查，迭代快、上下文干净。
- **方式 2 本会话内联执行**：用 executing-plans 在当前会话按批次执行，带检查点供你确认。

选定后我立即开始 Task 1。
