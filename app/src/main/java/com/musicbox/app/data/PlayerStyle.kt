package com.musicbox.app.data

/**
 * 播放页风格枚举。
 *
 * [key] 是持久化标识，写入现有 SettingsRepository（SharedPreferences）。
 * 新增风格时不要修改已有 key，避免用户已选风格失效。
 */
enum class PlayerStyle(
    val key: String,
    val label: String,
    val description: String,
) {
    MINIMAL(
        key = "minimal",
        label = "极简沉浸",
        description = "大封面居中，纤细进度条，控制按钮干净居中",
    ),
    LIQUID_GLASS(
        key = "liquid_glass",
        label = "液态玻璃",
        description = "封面延伸到状态栏下方，底部悬浮半透明控制条",
    ),
    STRUCTURED(
        key = "structured",
        label = "结构三栏",
        description = "中心播放键固定，左右按钮对称，进度条独立成行",
    ),
    VINYL(
        key = "vinyl",
        label = "黑胶唱片",
        description = "旋转唱盘与唱针，左右滑动切歌，控制区弱化",
    ),
    GESTURE(
        key = "gesture",
        label = "手势优先",
        description = "点击显隐控制栏，拖动调节进度、亮度与音量",
    ),
    SPLIT_VIEW(
        key = "split_view",
        label = "双栏队列",
        description = "上半播放控制，下半播放队列，可上滑展开",
    ),
    RETRO_CASSETTE(
        key = "retro_cassette",
        label = "复古卡带",
        description = "卡带拟物与怀旧配色，播放时卷轴转动",
    ),
    TOOL_MINIMAL(
        key = "tool_minimal",
        label = "工具极简",
        description = "低饱和灰蓝，高密度信息，无边框卡片",
    ),
    FROSTED(
        key = "frosted",
        label = "毛玻璃氛围",
        description = "封面模糊铺底，前景毛玻璃浮层卡片",
    ),
    ;

    companion object {
        /** 首次启动的默认风格。 */
        val Default: PlayerStyle = MINIMAL

        /** 容错解析：未知或空值一律回到默认风格。 */
        fun fromKey(value: String?): PlayerStyle =
            values().firstOrNull { it.key == value } ?: Default
    }
}
