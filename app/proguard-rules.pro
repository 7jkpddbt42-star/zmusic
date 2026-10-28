# ZMusic 正式版（R8）混淆规则
#
# 重点保护「通过反射读写」的类：Gson 反序列化的 DTO、本地持久化的数据类，
# 以及泛型签名信息。其余业务类正常参与压缩与混淆。

# Gson 依赖泛型签名、注解与内部类信息判断字段类型
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# @SerializedName 字段名不能被裁剪，否则反序列化拿不到值
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 网络接口响应模型（全部通过 Gson 反射构造）
-keep class com.musicbox.app.source.netease.** { *; }

# 本地持久化模型（历史记录、歌词缓存、主题/背景配置等）
-keep class com.musicbox.app.model.** { *; }
-keep class com.musicbox.app.data.** { *; }
-keep class com.musicbox.app.lyric.** { *; }

# Gson 自身的泛型 TypeToken 子类
-keep class * extends com.google.gson.reflect.TypeToken { *; }

# OkHttp 在 JVM 平台上会尝试加载可选依赖，Android 上没有，忽略警告
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
