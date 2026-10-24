# ZMusic

纯原生安卓在线音乐播放器：**Kotlin + Jetpack Compose + Media3**，没有 WebView，没有套壳，也不需要任何 API Key 或账号配置。

- 应用名：`musicbox`
- 包名：`com.musicbox.app`
- 版本：`0.1.0`
- 系统要求：Android 8.0（API 26）及以上，`targetSdk 36`

核心体验是「搜索即播 + 逐字歌词」：搜索网易云曲库、在线播放、逐字歌词跟唱，并在系统层面完整接入媒体通知、锁屏控制与耳机线控。

---

## 功能特性

### 音乐与音源

- 搜索歌曲 / 歌手 / 专辑，结果按相关度过滤，选中即播
- 网易云接口签名在**客户端本地**完成（eapi，AES-128-ECB + PKCS7），不依赖任何自建远程 API
- 统一 `MusicProvider` 抽象，内置备用音源兜底：端上签名路径临时失效时可切换到设置中的备用地址
- 播放地址短期缓存、歌词磁盘缓存，减少重复请求

### 发现与账号

- 每日推荐、私人 FM（游客态也可试听）
- 我喜欢的音乐、我的歌单、全部歌单、歌单详情
- 可选扫码登录：二维码本地生成（ZXing）；登录凭证同时从响应体与 `Set-Cookie` 提取，失败时自动切换域名重试
- 游客模式可用核心功能，登录后自动同步推荐、喜欢与个人歌单
- 「喜欢」与网易云「我喜欢的音乐」歌单双向同步

### 播放

- 9 种结构完全不同的播放页风格，切换不重建页面、不中断播放、不丢失进度：

| 标识 | 名称 | 特征 |
|---|---|---|
| `minimal` | 极简沉浸 | 大封面居中，纤细进度条，控制按钮干净居中（默认） |
| `liquid_glass` | 液态玻璃 | 三段式布局，玻璃胶囊与控制条浮在封面背景之上 |
| `structured` | 结构三栏 | 中心播放键固定，左右按钮对称，进度条独立成行 |
| `vinyl` | 黑胶唱片 | 旋转唱盘与唱针，左右滑动切歌 |
| `gesture` | 手势优先 | 点击显隐控制栏，横向拖动调进度、竖向调亮度与音量 |
| `split_view` | 双栏队列 | 上半播放控制，下半播放队列 |
| `retro_cassette` | 复古卡带 | 卡带拟物与怀旧配色，播放时卷轴转动 |
| `tool_minimal` | 工具极简 | 低饱和灰蓝，高密度信息，无边框卡片 |
| `frosted` | 毛玻璃氛围 | 封面模糊铺底，前景毛玻璃浮层卡片 |

- 播放队列面板：查看、跳转、删除、切换播放模式（顺序 / 单曲循环 / 随机）
- 细线进度条，支持拖动预览与两端时间显示
- 封面以最高 1080px 精确解码，缓存按「尺寸 + URL」分键
- 切歌时封面背景交叉淡入淡出，不出现空白闪烁

### 歌词

- YRC 逐字填充动画，逐帧时钟驱动（毫秒级精度），并兼容普通 LRC
- 两种歌词样式可在设置中切换：`聚焦`（当前行居中放大、其余行按距离缩小淡出，默认）与 `经典`
- 译文、罗马音默认显示开关，点击歌词行可跳转
- 桌面歌词悬浮窗：可拖动位置、切换字号与颜色，需要「显示在其他应用上层」权限

### 系统集成

- 基于 Media3 `MediaSessionService` 的后台播放，通知栏 / 锁屏媒体卡片展示封面、标题、进度与控制按钮
- 点击媒体卡片直达播放页（`SINGLE_TOP | CLEAR_TOP` + `onNewIntent` 深链）
- 注册 `MediaButtonReceiver`，耳机线控与蓝牙媒体键可用
- 全面屏 edgeToEdge，内容避让状态栏、导航栏与刘海

### 外观与个性化

- 主题模式：跟随系统 / 浅色 / 深色
- 取色来源：封面 / 壁纸 / 固定色，并支持「播放页独立主题」
- 播放背景：封面 / 自选图片 / 自选视频；背景模糊四档（关闭 / 轻柔 / 标准 / 浓郁）
- 液态玻璃：基于 [Kyant0/Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) 2.0.1，折射为主、模糊为辅；
  API 33+ 使用真实 backdrop 管线，API 32 及以下自动降级为渐变伪玻璃
- 全界面手绘线性图标，统一淡入淡出过渡，全局胶囊式操作提示

### 本地数据

- 播放历史、累计听歌时长（按真实播放秒数统计）持久化在本地
- 所有设置项与登录态本地存储，卸载即清空

---

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 / UI | Kotlin 2.4.21、Jetpack Compose（BOM 2026.09.00）、Material 3 |
| 播放引擎 | AndroidX Media3 / ExoPlayer 1.11.1、`media3-session` |
| 网络 | OkHttp 5.5.0、Gson 2.11.0 |
| 玻璃效果 | `io.github.kyant0:backdrop:2.0.1` |
| 二维码 | ZXing 3.5.4 |
| 主题取色 | androidx.palette 1.0.0 |
| 协程 | kotlinx-coroutines 1.10.2 |
| 构建 | AGP 9.4.1、Gradle 9.8.1、JDK 17 |

---

## 项目结构

```
zmusic/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/musicbox/app/
│       │   │   ├── MainActivity.kt        # 入口、导航、全局状态
│       │   │   ├── AppContainer.kt        # 依赖装配
│       │   │   ├── data/                  # 设置、登录态、历史、缓存、播放风格
│       │   │   ├── source/                # 音源抽象、网易云 eapi 实现、备用音源
│       │   │   ├── player/                # 播放服务、流地址解析、播放模式映射
│       │   │   ├── lyric/                 # LRC / YRC 解析与时间轴
│       │   │   ├── desktoplyric/          # 桌面歌词悬浮窗
│       │   │   ├── model/                 # 数据模型
│       │   │   └── ui/                    # Compose 界面与组件
│       │   └── res/
│       └── test/                          # 单元测试（签名、解析、时间轴、缓存等）
├── docs/                                  # 设计文档与实现计划
├── gradle/wrapper/                        # Gradle Wrapper
├── build-apk.sh                           # 一键打包脚本
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 构建与运行

### 环境要求

- JDK 17 或更高（`java -version` 可查）
- Android SDK：Platform 37、Build-Tools 36.0.0（`ANDROID_HOME` 或默认安装路径）
- 能访问 Maven 仓库的网络环境

### 一键打包

```bash
bash build-apk.sh
```

脚本会依次完成：检查 JDK 与 Android SDK → 执行 `:app:assembleDebug` → 把产物复制到 `dist/ZMusic-debug.apk`。

### 手动构建

```bash
chmod +x gradlew
./gradlew :app:assembleDebug
```

产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

### 安装

```bash
adb install -r dist/ZMusic-debug.apk
```

仓库根目录也附带了一份早前构建的 `app-debug.apk`，可直接安装；如需与源码完全一致，请运行脚本重新打包。

### 单元测试

```bash
./gradlew :app:testDebugUnitTest
```

---

## 已知限制

- 音源来自网易云音乐的非官方公开接口，接口随时可能变更或失效；可在设置中配置备用音源地址作为兜底
- 目前只接入网易云一个音源，QQ 音乐等其他平台仅预留了 `MusicProvider` 接口
- 液态玻璃效果需要 Android 13 及以上才是真实折射，Android 12 及以下为渐变降级；Android 8 以下不提供
- 桌面歌词需要在系统设置中授予「显示在其他应用上层」权限
- vivo 等厂商的私有锁屏歌词没有公开接口，只能使用标准媒体通知与锁屏控件
- 不含本地音乐扫描与本地播放能力，定位是纯在线播放器
- 仅提供 debug 构建，未配置正式签名与上架

---

## 免责声明

本项目仅用于个人学习 Kotlin / Jetpack Compose 与音源接口研究，**不得用于任何商业用途**。
音乐版权归各家平台与版权方所有，接口数据来自公开 Web 接口，如涉及侵权请联系删除。请支持正版音乐。

本项目未附带开源许可证，保留所有权利。
