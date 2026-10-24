#!/usr/bin/env bash
# ZMusic 一键打包：还原 Gradle Wrapper → 编译 Debug APK → 复制到 dist/
#
# 用法：
#   bash build-apk.sh              # 编译并把 APK 复制到 dist/ZMusic-debug.apk
#   bash build-apk.sh --stacktrace # 其余参数原样透传给 Gradle

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
APK_SRC="app/build/outputs/apk/debug/app-debug.apk"
DIST_DIR="dist"
APK_DIST="$DIST_DIR/ZMusic-debug.apk"
SDK_PLATFORM="android-37"
MIN_JDK=17

info() { printf '\033[36m[ZMusic]\033[0m %s\n' "$*"; }
warn() { printf '\033[33m[ZMusic]\033[0m %s\n' "$*" >&2; }
fail() { printf '\033[31m[ZMusic]\033[0m %s\n' "$*" >&2; exit 1; }

# 1. Gradle Wrapper
[ -s "$WRAPPER_JAR" ] || fail "缺少 ${WRAPPER_JAR}，请确认仓库拉取完整"
chmod +x gradlew

# 2. JDK 检查
command -v java >/dev/null 2>&1 || fail "未找到 java，请安装 JDK ${MIN_JDK} 或更高版本"
JAVA_VERSION_RAW="$(java -version 2>&1 | awk -F'"' '/version/ {print $2; exit}')"
JAVA_MAJOR="$(printf '%s' "$JAVA_VERSION_RAW" | awk -F'.' '{ if ($1 == 1) print $2; else print $1 }')"
[ -n "$JAVA_MAJOR" ] || fail "无法识别 Java 版本：$JAVA_VERSION_RAW"
[ "$JAVA_MAJOR" -ge "$MIN_JDK" ] || fail "当前 Java ${JAVA_VERSION_RAW} 过低，需要 JDK ${MIN_JDK} 或更高版本"
info "JDK ${JAVA_VERSION_RAW}"

# 3. Android SDK 检查
SDK_DIR=""
for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
    if [ -n "$candidate" ] && [ -d "$candidate" ]; then
        SDK_DIR="$candidate"
        break
    fi
done
[ -n "$SDK_DIR" ] || fail "未找到 Android SDK，请安装后设置 ANDROID_HOME 环境变量"
info "Android SDK：$SDK_DIR"

# 只在缺失时生成 local.properties，避免覆盖已有配置（该文件不入版本库）
if [ ! -f local.properties ] || ! grep -q '^sdk.dir=' local.properties; then
    printf 'sdk.dir=%s\n' "$SDK_DIR" > local.properties
fi

[ -d "$SDK_DIR/platforms/$SDK_PLATFORM" ] || \
    warn "SDK Platform 37 未安装，Gradle 会尝试自动下载（需已接受许可协议）"

# 4. 编译
info "开始编译 Debug APK…"
./gradlew :app:assembleDebug "$@"

# 5. 输出
[ -f "$APK_SRC" ] || fail "编译结束但未找到 APK：$APK_SRC"
mkdir -p "$DIST_DIR"
cp -f "$APK_SRC" "$APK_DIST"

info "打包完成：${APK_DIST}（$(du -h "$APK_DIST" | awk '{print $1}')）"
info "安装命令：adb install -r ${APK_DIST}"
