# MI-5 美化与主题（参考椒盐音乐 Salt Player）实现计划

> 日期：2026-10-19
> 基线：HEAD = `e3dc1fc`（MI-4 收尾）
> 目标：在 musicbox 上落地"直接能做"与"需要花功夫做"的全部美化/主题能力，并逐项实机验收。

---

## 0. 背景

用户在调研安卓"美化向"播放器（椒盐音乐 Salt Player、网易云 DIY 播放器、波点音乐、Namela、Poweramp 皮肤、OreoTunes 等）后，要求：

> 把直接能做的和需要花功夫做的功能都给做了，然后验证，参考椒盐音乐的美化和主题方式进行结合。

椒盐音乐参考点（实测/官方口径）：

- **分控主题**：主页与播放页分开设置主题色
- **壁纸取色**：支持 Android 12+ 壁纸取色
- **动态流光 V2**
- **多层模糊**（毛玻璃材质）
- **沉浸模式**：长按播放键进入
- **逐字（卡拉 OK）歌词上浮动画**
- 桌面歌词、迷你播放条歌词、滚动歌词
- 大量自定义项（约百项）

椒盐音乐本身即 **Jetpack Compose** 实现，证明上述效果在 Compose 技术栈全部可行。

---

## 1. 现状侦察结论（关键事实，均已核实）

| # | 事实 | 证据 |
|---|---|---|
| 1 | **无图片加载库、无 Palette** | `app/build.gradle.kts` 依赖清单（Compose BOM / Material3 / OkHttp / Gson / Media3 / ZXing，无 Coil/Glide/palette），封面当前**未在任何 UI 展示** |
| 2 | `Song` 已有 `coverUrl`、`album`、`durationMs` | `model/Song.kt` |
| 3 | 主题为**硬编码暗色**，无动态色 | `ui/Theme.kt`，`MusicBoxTheme` 使用 `darkColorScheme()` |
| 4 | 播放页结构 | `ui/player/PlayerScreen.kt`：顶部标题/队列 L37-L83（含中间 `Artwork`）、信息+进度+控制 L98-L171 |
| 5 | 逐帧时钟已存在 | `ui/lyric/FrameClock.kt`（`withFrameNanos`，每帧读 ExoPlayer 真实位置）；接口 `PositionSource` 由 `ui/player/PlaybackConnection.kt:63` 实现 |
| 6 | 逐字渲染位置 | `ui/lyric/LyricScreen.kt:247-283` `KaraokeWord`（`drawWithContent` + `clipRect` 按 `wordProgress` 裁剪）→ **上浮/缩放动画就加在这里** |
| 7 | 歌词滚动/居中 | `ui/lyric/LyricScreen.kt:137-174`（`LazyColumn` + `animateScrollToItem` 滚到约 1/3 高度） |
| 8 | 已有动画极少 | 仅 `ui/lyric/LyricScreen.kt:194` 一行 `animateFloatAsState`（行透明度）；`AlertDialog` 在 `ui/settings/SettingsScreen.kt:98`；**全项目无** `Modifier.blur` / `infiniteTransition` / `graphicsLayer` |
| 9 | 设置体系 | `data/SettingsRepository.kt`（`KeyValueStore` 抽象 + `StateFlow` + setter）；装配 `AppContainer.kt:44`；UI 接线 `MainActivity.kt:106-320`；设置页 `ui/settings/SettingsScreen.kt`（参数在 L29-L48） |
| 10 | 现有设置项 | 歌词译文开关、歌词罗马音开关、备用音源地址、歌词缓存计数/清空、错误日志查看/清空 |

### 1.1 顺带确认的登录缺陷（本计划 T0 一并修）

实机 logcat 实证（模拟器 74 分钟内，证据存档 `/tmp/musicbox_qrdbg_archive.log`）：

```
2233 次 code=801（等待扫码）
  16 次 code=800（二维码过期 → 端上静默换新码）
   0 次 code=802 / 803（扫码事件从未被服务端受理）
```

- 请求层本身**已被证明正确**：Mac 独立复现同一 eapi 请求得到 `code:200` + 有效 unikey；端上二维码经截屏解码验证为 `https://music.163.com/login?codekey=<unikey>`，与轮询 key 一致。
- **缺陷 A（已读码确认）**：`LoginViewModel` 轮询 `result.isFailure -> Unit` 把所有失败（含非网络类）**静默吞掉**，用户零反馈。
- **缺陷 B（日志实证）**：收到 `800` 时**静默换新码**，用户刚扫的码当场作废且界面无任何提示（74 分钟内发生 16 次）。
- 另有待查项：0 次 802 说明扫码未生效，需用户侧手机提示才能定性（不是端上请求问题）。

---

## 2. 依赖变更

| 依赖 | 版本 | 用途 | 风险 |
|---|---|---|---|
| `androidx.palette:palette-ktx` | 1.0.0 | 封面位图取色 | 纯 Java，低风险 |
| `io.coil-kt.coil3:coil-compose` | 3.7.0 | 异步加载网络封面 + 内存/磁盘缓存 | **需即时验证构建**；若与 Kotlin 2.4.21 / AGP 9.4.1 不兼容，降级为自建轻量加载器（OkHttp + BitmapFactory + LruCache，约 100 行，无新依赖） |

> 教训沿用：上一个里程碑因 KSP 版本不兼容放弃 Room。**任何新依赖必须在加入后立刻 `assembleDebug` 验证**，失败即走降级方案，不硬撑。

`minSdk` 保持 26 不变，**不提升**。

---

## 3. 全局工程约定（不变）

1. 纯原生 Kotlin + Compose，禁套壳。
2. 所有音源调用返回 `Result<T>`，异常不抛到 UI 层。
3. 歌词为可选依赖，拉取失败不影响播放。
4. 逐字歌词必须逐帧时钟驱动（不得退化为回调驱动）。
5. 登录完全可选，游客可正常使用。
6. 每个 Task 结束后必须 `./gradlew :app:assembleDebug :app:testDebugUnitTest` 全绿。
7. 每个 Task 一次提交，commit 用英文 conventional 前缀；代码注释与文档中文。
8. **不得回退已有功能**（搜索/播放/队列/收藏/历史/设置/歌词/断网提示）。

---

## 4. 任务拆解

### T0 清理与登录可见性修复

**目标**：清掉调试残留；让登录失败/过期不再静默。

**涉及文件**
- `source/netease/NetEaseLoginApi.kt`（删除两行 `android.util.Log.d("QRDBG", ...)`）
- `ui/login/LoginViewModel.kt`（轮询错误处理 + 过期提示）
- `app/src/test/java/com/musicbox/app/ui/login/LoginViewModelTest.kt`

**步骤**
1. 删除 `NetEaseLoginApi.kt` L109、L111 附近的两行 `QRDBG` 日志。
2. `pollLoop`：区分错误类型 —— `SourceError.Network`（保留现状：静默、下一轮重试）；其它错误（`Api`/`Parse`/其它）→ 置 `LoginUiState.Failed(中文文案)` 并结束轮询，让用户看到真实原因。
3. `QrPollResult.Expired`：不再纯静默换码 —— 先置 `LoginUiState.Expired`，再申请新码；`Waiting` 状态下若发生过自动刷新，界面必须显示明确提示「二维码已过期，已为你刷新，请重新扫描」。
4. 补单测：803 无 cookie → `Failed`；轮询返回非预期 code → `Failed`；800 → 重新申请 key 且状态带过期提示。

**验收**：`testDebugUnitTest` 全绿；实机上断网轮询 → 界面出现错误文案而非永远转圈。

**提交**：`fix: surface qr login failures and warn before silent qr refresh`

---

### T1 主题系统基座（封面动态取色 + 壁纸取色 + 分控主题）

**目标**：把"硬编码暗色"换成可随封面/壁纸/固定色驱动的主题系统，并支持主页与播放页两套主题色。

**涉及文件**
- `app/build.gradle.kts`（加 palette + coil）
- 新建 `ui/theme/ArtworkImage.kt`：封面异步加载 Composable（Coil 或降级加载器），带 shape/clip 参数，暴露 `onBitmap` 回调供取色
- 新建 `ui/theme/CoverPalette.kt`：`Palette` → `Color`（优先 `Vibrant`，退化 `Muted`/`Dominant`），并保证可读性（与黑/白对比度校正）
- 新建 `ui/theme/ThemeState.kt`：`ThemeSpec`（`mode`: 跟随系统/浅/深；`seedSource`: 封面/壁纸/固定色；`fixedSeed: Color`；`separatePlayerTheme: Boolean`；`playerSeedSource`/`playerFixedSeed`）与 `fun MaterialTheme` 入口所需的 `buildScheme(seed, dark): ColorScheme`
- 改造 `ui/Theme.kt`：`MusicBoxTheme(themeSpec, seed: Color?, content)` → 动态 `lightColorScheme`/`darkColorScheme`（用 seed 生成 primary/secondary/tertiary/surface 系列）
- `data/SettingsRepository.kt`：新增 key 与 StateFlow/setter（`themeMode`、`seedSource`、`fixedSeedArgb`、`separatePlayerTheme`、`playerSeedSource`、`playerFixedSeedArgb`）
- `ui/settings/SettingsScreen.kt`：新增「外观」分区（主题模式三选、取色来源三选、固定色色板、"播放页独立主题"开关）
- `MainActivity.kt`：读取设置 → 计算当前 seed → 传入 `MusicBoxTheme`；播放页/歌词页在"分控主题开启"时切换到 player seed（`CompositionLocal` 提供当前页 seed）
- `AppContainer.kt`：暴露 `WallpaperManager` 取色（`getWallpaperColors`，Android 12+；低版本不可用时该项在 UI 上禁用）

**步骤**
1. 加依赖并**立刻**构建验证（失败即降级为自建加载器，palette 保留）。
2. 落 `ArtworkImage` + `CoverPalette`，先用测试封面验证取色链路（可写 JVM 单测：给定 Bitmap 断言产出颜色非空/可读）。
3. 落 `ThemeState` + `Theme.kt` 改造，接入 `MainActivity`。
4. 加设置项与设置页 UI。
5. 播放页/歌词页接 `CompositionLocal`（`LocalPageSeed`）实现分控。

**验收**
- 三张不同封面的歌曲切换，播放页主色肉眼可见变化；主页色不受影响（分控开启时）
- 主题模式切浅色后界面整体变亮；重启后保持
- 壁纸取色在 Android 12+ 生效（模拟器 API 30 → 该项禁用且不崩）
- `assembleDebug` + `testDebugUnitTest` 全绿

**提交**：`feat: cover/wallpaper driven theme with separate player seed`

---

### T2 液态玻璃材质（毛玻璃背景 + 玻璃卡片）

**目标**：播放页/迷你条/底栏换毛玻璃；播放页加全屏模糊背景层。

**涉及文件**
- 新建 `ui/components/GlassSurface.kt`：`Modifier.glass(tint, borderAlpha, highlight)`（半透明 surface + 细亮边 + 顶部高光）
- 新建 `ui/components/BlurredBackdrop.kt`：全屏背景层 = 封面放大铺满 + 模糊 + 暗化/渐变遮罩
  - **API 31+**：`Modifier.blur(radius)`（真实模糊）
  - **API 26–30 降级**：位图先降到 ~24px 再放大铺满 + 半透明叠层（视觉近似毛玻璃，成本极低）
- `ui/player/PlayerScreen.kt`：在 L37-L83 插入背景层（作为 `Box` 最底层），信息区/控制区包 `glass()` 卡片
- `ui/player/NowPlayingBar.kt`：容器改毛玻璃
- `ui/BottomTabs`（所在文件）：底栏改毛玻璃

**步骤**
1. 落 `GlassSurface` + `BlurredBackdrop`（把两级模糊降级写进同一 API，调用方无感）。
2. 播放页接入背景层与玻璃卡片。
3. 迷你条、底栏接入毛玻璃。
4. 保证不遮挡点击（背景层 `pointerInput` 透明/不拦截）。

**验收**
- 播放页出现毛玻璃背景（模拟器 API 30 走降级路径，效果为柔和铺底）+ 玻璃卡片
- 切歌/滚动不卡顿、无掉帧观感崩溃
- 迷你条与底栏有玻璃质感

**提交**：`feat: liquid glass surfaces and blurred backdrop`

---

### T3 动效（旋转唱片 + 流光 + 沉浸模式 + 页面过渡）

**目标**：补齐"活的"视觉反馈。

**涉及文件**
- `ui/player/PlayerScreen.kt`（Artwork → 唱片）
- 新建 `ui/components/VinylRecord.kt`：圆形唱片 + 中心封面 + `infiniteTransition` 旋转（播放旋转、暂停停转且角度保持）
- 新建 `ui/components/FlowingBorder.kt` / 在播放页用 `Brush.linearGradient` + `infiniteTransition` 做流光（进度条/标题/背景描边）
- `ui/player/PlayerScreen.kt` + `MainActivity.kt`: 沉浸模式（长按播放键进入：隐藏顶栏/底栏/迷你条，只留唱片+歌词+极简控制；再次长按或返回退出）
- 页面过渡：播放页 / 歌词页 / 队列 的出现与消失改用 `AnimatedVisibility` + 位移/缩放（替换当前的瞬时切换）

**步骤**
1. `VinylRecord` 落地并接入播放页。
2. 流光用于进度条已播放段与播放页标题下划线。
3. 沉浸模式：用 `LocalImmersive` 状态驱动 `MainActivity` 的栏位显隐（`AnimatedVisibility`）。
4. 三处页面切换加过渡动画。

**验收**
- 播放时唱片持续旋转，暂停即停在当前角度，恢复后继续
- 进度条有流动光带；标题有流光
- 长按播放键可进出沉浸模式，动画平滑
- 打开/关闭播放页、歌词页、队列有过渡动画

**提交**：`feat: vinyl rotation, flowing gradient and immersive mode`

---

### T4 逐字歌词上浮动画 + 迷你播放条歌词

**目标**：把已有的逐字裁剪渲染升级为「上浮 + 缩放」；迷你条显示当前歌词。

**涉及文件**
- `ui/lyric/LyricScreen.kt:247-283`（`KaraokeWord`）
- `ui/lyric/LyricScreen.kt:194` 附近（行过渡）
- `ui/player/NowPlayingBar.kt`
- `ui/lyric/LyricViewModel.kt`（暴露"当前行文本" StateFlow 供迷你条使用）

**步骤**
1. `KaraokeWord`：以 `wordProgress` 驱动 `graphicsLayer { translationY = -risePx * p; scaleX = scaleY = 1 + 0.06f * p }`，`risePx` 取字号的一定比例；**必须以逐帧值驱动，不得用 `animateFloatAsState` 替代**（会丢毫秒精度）。
2. 当前行整体：进入时轻微 `scale 1.0→1.02` + `alpha 0.85→1`（可保留现有 `animateFloatAsState`）。
3. `LyricViewModel` 增加 `currentLineText: StateFlow<String>`（当前行 + 可选译文），仅在行变化时更新。
4. `NowPlayingBar` 在歌名下方加一行歌词（单行省略号），随行变化淡入淡出。

**验收**
- 逐字高亮时字形明显上浮+放大，120/60Hz 下流畅；同帧内不闪烁
- 暂停后不再推进（逐帧时钟停）
- 迷你条显示当前歌词并随行更新

**提交**：`feat: karaoke word rise animation and miniplayer lyric line`

---

### T5 桌面歌词（悬浮窗）

**目标**：系统级悬浮歌词窗口，可拖动、可开关。

**涉及文件**
- `ui/lyric/LyricBus.kt`（新建：`object` 持 `MutableStateFlow<LyricOverlayState>`，含当前行、下一行、译文、播放状态）
- `ui/lyric/LyricViewModel.kt`（行变化时写 `LyricBus`）
- `desktoplyric/DesktopLyricService.kt`（新建：前台服务 + `WindowManager` `TYPE_APPLICATION_OVERLAY`）
- `AndroidManifest.xml`（服务声明 + `SYSTEM_ALERT_WINDOW` 权限）
- `data/SettingsRepository.kt` + `ui/settings/SettingsScreen.kt`（开关 + 字号/颜色 + 权限引导）
- `MainActivity.kt`（权限申请 `ACTION_MANAGE_OVERLAY_PERMISSION`，返回后回读状态）

**步骤**
1. `LyricBus` 只发布**行级**变化（不含每帧数据），服务侧自分栏渲染，避免高频跨层。
2. 服务用 `TYPE_APPLICATION_OVERLAY` 绘制一个胶囊视图：当前行（高亮）+ 下一行（半透明）；行变化时淡入淡出。
3. 支持拖动（`onTouch` 更新 `WindowManager.LayoutParams` 坐标，坐标持久化到设置）。
4. 权限未授予时：设置页开关点击 → 跳系统授权页；返回后自动重读并启用。
5. 播放停止/服务关闭时自动移除悬浮窗。

**验收**
- 授权后开启开关 → 屏幕上出现歌词悬浮条，切歌/切行时文本更新
- 可拖动且位置重启后保持
- 关闭开关 → 悬浮窗立即消失，无残留
- 未授权时给出引导文案，不崩溃

**提交**：`feat: desktop lyric overlay with drag and permission flow`

---

### T6 波形可视化 + 粒子特效

**目标**：波形进度条与随播放起伏的粒子背景。

**设计决策（重要）**
- **不申请录音权限**：`android.media.audiofx.Visualizer` 需要 `RECORD_AUDIO`，且在模拟器/部分机型上返回空数据，无法稳定验收。
- 改用**确定性伪波形**：由 `songId` + 播放位置经哈希 + 平滑生成一组稳定的条高（同一首歌同一位置高度一致，看起来像真实波形），逐帧由 `FrameClock` 推进（进度条左侧已播放段着色）。
- 粒子特效同理：由播放位置 / 播放状态驱动（呼吸、漂移、亮度脉动），暂停即静止。

**涉及文件**
- 新建 `ui/components/WaveformSeekBar.kt`
- 新建 `ui/components/ParticleField.kt`
- `ui/player/PlayerScreen.kt`（进度条替换为波形；背景层叠加粒子）

**步骤**
1. `WaveformSeekBar`：Canvas 绘制 N 根竖条；已播放段用主题色、未播放段低透明度；支持点击/拖动跳转（保留原进度条功能）。
2. `ParticleField`：固定数量粒子（约 40），位置/透明度由 `position` 与播放状态推导；用 `Canvas` + `withFrameNanos` 驱动。
3. 接入播放页（粒子在背景层之上、玻璃卡片之下）。

**验收**
- 波形条随播放推进着色，点击/拖动可跳转
- 粒子在播放时动、暂停时静
- 连续播放 2 分钟无卡顿/无内存增长异常（看 logcat 无 GC 风暴/无 OOM）

**提交**：`feat: deterministic waveform seekbar and particle field`

---

### T7 自定义背景（图片 / 视频）与播放页背景视频

**目标**：用户自选播放页背景（图片或视频），对标网易云 DIY 播放器与波点音乐（但只用本地素材，不提供官方素材库）。

**涉及文件**
- `ui/settings/SettingsScreen.kt`（三个入口：选择背景图片 / 选择背景视频 / 恢复默认）
- `data/SettingsRepository.kt`（保存自选背景的本地路径与类型）
- 新建 `data/BackgroundStore.kt`：把选中的素材**复制到应用私有目录**并返回稳定路径（避免相册 URI 授权失效）
- 新建 `ui/components/BackgroundLayer.kt`：按类型渲染
  - 图片：`BitmapFactory` 采样加载 → 走 T2 的模糊背景层路径
  - 视频：复用 Media3 `ExoPlayer`（已依赖）循环 + 静音 + `setRepeatMode(REPEAT_MODE_ALL)`，绑定到 `AndroidView(PlayerView)` 底层；不可用时回退图片/纯色
- `MainActivity.kt`：`ActivityResultContracts.PickVisualMedia`（图片）+ `GetContent("video/*")`（视频），拿到后交给 `BackgroundStore`

**步骤**
1. `BackgroundStore` 落地（复制 + 元数据）。
2. `BackgroundLayer` 落地，优先视频 > 图片 > 封面模糊（T2 默认）。
3. 设置页加入三个入口并接线；选中后即时生效（StateFlow）。
4. **明确限制**：不提供官方素材库/在线 MV（版权与内容问题），UI 文案写清"仅支持本机素材"。

**验收**
- 选一张本地图片 → 播放页背景立即变为该图（模糊铺满），重启后保持
- 选一个本地短视频 → 播放页背景播放视频（静音循环），切歌不中断
- 选"恢复默认" → 回到封面模糊
- 取消选择（系统选择器返回空）不崩溃

**提交**：`feat: user-picked image/video background for player`

---

### T8 实机验收 + 收尾

**验收清单（模拟器 `emulator-5554`，包名 `com.musicbox.app.debug`）**

1. 别名：启动进入搜索页与四标签，无崩溃
2. 封面取色：三首歌切换，播放页主色变化；主页不受影响（分控开启）
3. 主题模式：切浅色 → 整体变亮；重启保持
4. 毛玻璃：播放页背景模糊层 + 玻璃卡片可见；迷你条/底栏有玻璃质感
5. 唱片旋转：播放转、暂停停、恢复继续
6. 流光：进度条光带、标题流光可见
7. 沉浸模式：长按播放键进出，动画平滑
8. 逐字上浮：字/词上浮+缩放，流畅；暂停不推进
9. 迷你条歌词：显示当前行并随行更新
10. 桌面歌词：授权后开关生效、可拖动、关闭即消失
11. 波形：随播放着色，点击/拖动可跳转
12. 粒子：播放时动、暂停时静
13. 自定义背景：选图/选视频生效并持久化；恢复默认可用
14. 稳定性：连续 20 次切歌无崩溃；logcat 全 buffer 无 `FATAL EXCEPTION` / ANR

**收尾**
- `./gradlew :app:assembleDebug :app:testDebugUnitTest` 全绿
- 空提交：`test: MI-5 verified on device - theming, glass, motion, desktop lyric and backgrounds`

---

## 5. 风险与降级方案

| 风险 | 降级方案 |
|---|---|
| Coil 3 与 Kotlin 2.4.21 / AGP 9.4.1 不兼容 | 自建轻量图片加载器（OkHttp + BitmapFactory + LruCache），palette 保留 |
| `Modifier.blur` 在 API 26–30 无效果 | 位图降采样再放大的视觉近似（T2 已内置） |
| 色度提取出的主色对比度不足导致文字看不清 | `CoverPalette` 内置亮度校正，退化到 `Muted`/`Dominant` |
| 桌面歌词悬浮窗在模拟器上被系统策略拦截 | 验收时以 `dumpsys window` 确认窗口存在；若系统限制则记录为"真机验证项"并明确写入交付说明 |
| 视频背景在模拟器解码异常 | `BackgroundLayer` 捕获异常回退图片/纯色，不崩溃 |
| 伪波形被误认为"真实频谱" | UI 不宣称是频谱分析；文档写明为"确定性伪波形" |
| 逐字动画引入掉帧 | 一律走逐帧值驱动，禁用会引入插值的 `animate*AsState` 参与逐字进度 |

---

## 6. 交付判定

- 第 4 节 8 个 Task 全部完成、各自提交、构建与单测全绿
- 第 T8 节 14 项实机验收全部通过并留证据
- 项目总结文档（`temp_20261011_01/musicbox/`）补充 MI-5 章节
