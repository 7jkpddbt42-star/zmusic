package com.musicbox.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 应用自有线性图标集（24dp 视口、1.8dp 圆头描边）。
 *
 * 为什么不用 Material Icons / 文字字形：
 * - Material Filled 与项目「细线 + 液态玻璃」的视觉语言不一致；
 * - `⏸` / `▶` 这类字形是字体里的符号，字重、留白不可控，在不同设备上会跳字形。
 *
 * 所有图标都用同一套描边参数手绘，保证粗细、圆角、视觉重心一致；
 * 颜色由 `Icon(tint = ...)` 统一着色，这里固定用黑色作为 tint 映射源。
 */
object MusicIcons {

    /** 搜索：放大镜。 */
    val Search: ImageVector = strokeIcon("music_search") {
        line { circle(10.8f, 10.8f, 6.3f) }
        line { moveTo(15.4f, 15.4f); lineTo(20f, 20f) }
    }

    /** 收藏（未选中）：心形描边。 */
    val Heart: ImageVector = strokeIcon("music_heart") {
        line { heartPath() }
    }

    /** 收藏（已选中）：实心心形。 */
    val HeartFilled: ImageVector = strokeIcon("music_heart_filled") {
        solid { heartPath() }
    }

    /** 历史：时钟。 */
    val History: ImageVector = strokeIcon("music_history") {
        line { circle(12f, 12f, 8.1f) }
        line {
            moveTo(12f, 7.4f)
            lineTo(12f, 12.2f)
            lineTo(15.6f, 14.1f)
        }
    }

    /** 发现：指南针（圆环 + 菱形指针）。 */
    val Compass: ImageVector = strokeIcon("music_compass") {
        line { circle(12f, 12f, 8.3f) }
        line {
            moveTo(15.9f, 8.1f)
            lineTo(13.4f, 13.4f)
            lineTo(8.1f, 15.9f)
            lineTo(10.6f, 10.6f)
            close()
        }
    }

    /** 我的：用户轮廓。 */
    val Person: ImageVector = strokeIcon("music_person") {
        line {
            moveTo(5.8f, 19.6f)
            curveTo(5.8f, 16f, 8.5f, 13.4f, 12f, 13.4f)
            curveTo(15.5f, 13.4f, 18.2f, 16f, 18.2f, 19.6f)
        }
        line { circle(12f, 8.2f, 3.2f) }
    }

    /** 播放：实心圆角三角。 */
    val Play: ImageVector = strokeIcon("music_play") {
        solid {
            moveTo(9f, 5.9f)
            curveTo(9f, 5.1f, 9.9f, 4.6f, 10.6f, 5f)
            lineTo(18.6f, 11.1f)
            curveTo(19.2f, 11.5f, 19.2f, 12.5f, 18.6f, 12.9f)
            lineTo(10.6f, 19f)
            curveTo(9.9f, 19.4f, 9f, 18.9f, 9f, 18.1f)
            close()
        }
    }

    /** 暂停：两根圆头竖线。 */
    val Pause: ImageVector = strokeIcon("music_pause") {
        line(3f) {
            moveTo(9f, 6.2f)
            lineTo(9f, 17.8f)
            moveTo(15f, 6.2f)
            lineTo(15f, 17.8f)
        }
    }

    /** 上一首。 */
    val SkipPrevious: ImageVector = strokeIcon("music_prev") {
        solid {
            moveTo(17.8f, 6.4f)
            lineTo(9.8f, 12f)
            lineTo(17.8f, 17.6f)
            close()
        }
        line(2.2f) {
            moveTo(6.8f, 6.2f)
            lineTo(6.8f, 17.8f)
        }
    }

    /** 下一首。 */
    val SkipNext: ImageVector = strokeIcon("music_next") {
        solid {
            moveTo(6.2f, 6.4f)
            lineTo(14.2f, 12f)
            lineTo(6.2f, 17.6f)
            close()
        }
        line(2.2f) {
            moveTo(17.2f, 6.2f)
            lineTo(17.2f, 17.8f)
        }
    }

    /** 歌词：带两行文字的对话气泡。 */
    val Lyrics: ImageVector = strokeIcon("music_lyrics") {
        line {
            moveTo(6.2f, 4.6f)
            lineTo(17.8f, 4.6f)
            curveTo(19.5f, 4.6f, 20.8f, 5.9f, 20.8f, 7.6f)
            lineTo(20.8f, 13.6f)
            curveTo(20.8f, 15.3f, 19.5f, 16.6f, 17.8f, 16.6f)
            lineTo(11.2f, 16.6f)
            lineTo(7.4f, 20f)
            lineTo(7.4f, 16.6f)
            lineTo(6.2f, 16.6f)
            curveTo(4.5f, 16.6f, 3.2f, 15.3f, 3.2f, 13.6f)
            lineTo(3.2f, 7.6f)
            curveTo(3.2f, 5.9f, 4.5f, 4.6f, 6.2f, 4.6f)
            close()
        }
        line(1.8f) {
            moveTo(7.2f, 9f)
            lineTo(16.8f, 9f)
            moveTo(7.2f, 12.6f)
            lineTo(13.4f, 12.6f)
        }
    }

    /** 播放队列：列表。 */
    val Queue: ImageVector = strokeIcon("music_queue") {
        line {
            moveTo(9.5f, 7f)
            lineTo(20f, 7f)
            moveTo(9.5f, 12f)
            lineTo(20f, 12f)
            moveTo(9.5f, 17f)
            lineTo(20f, 17f)
        }
        solid {
            circle(5f, 7f, 1.15f)
            circle(5f, 12f, 1.15f)
            circle(5f, 17f, 1.15f)
        }
    }

    /** 更多：水平三点。 */
    val More: ImageVector = strokeIcon("music_more") {
        solid {
            circle(5.6f, 12f, 1.7f)
            circle(12f, 12f, 1.7f)
            circle(18.4f, 12f, 1.7f)
        }
    }

    /** 倍速：速度表（弧 + 指针 + 轴心）。 */
    val Speed: ImageVector = strokeIcon("music_speed") {
        line {
            moveTo(4.2f, 16.2f)
            curveTo(4.2f, 12f, 7.7f, 8.6f, 12f, 8.6f)
            curveTo(16.3f, 8.6f, 19.8f, 12f, 19.8f, 16.2f)
        }
        line {
            moveTo(12f, 16.2f)
            lineTo(15.7f, 11.9f)
        }
        solid {
            circle(12f, 16.2f, 1.7f)
        }
    }

    /** 收起 / 返回到上一层：向下箭头。 */
    val ChevronDown: ImageVector = strokeIcon("music_chevron_down") {
        line {
            moveTo(6.8f, 9.6f)
            lineTo(12f, 14.8f)
            lineTo(17.2f, 9.6f)
        }
    }

    /** 返回：左箭头。 */
    val ChevronLeft: ImageVector = strokeIcon("music_chevron_left") {
        line {
            moveTo(14.6f, 6.4f)
            lineTo(9.4f, 12f)
            lineTo(14.6f, 17.6f)
        }
    }

    /** 进入：右箭头。 */
    val ChevronRight: ImageVector = strokeIcon("music_chevron_right") {
        line {
            moveTo(9.4f, 6.4f)
            lineTo(14.6f, 12f)
            lineTo(9.4f, 17.6f)
        }
    }

    /** 清空输入：叉。 */
    val Close: ImageVector = strokeIcon("music_close") {
        line {
            moveTo(6.6f, 6.6f)
            lineTo(17.4f, 17.4f)
            moveTo(17.4f, 6.6f)
            lineTo(6.6f, 17.4f)
        }
    }

    /** 设置：三条推子。 */
    val Sliders: ImageVector = strokeIcon("music_sliders") {
        line {
            moveTo(4f, 7f)
            lineTo(20f, 7f)
            moveTo(4f, 12f)
            lineTo(20f, 12f)
            moveTo(4f, 17f)
            lineTo(20f, 17f)
        }
        // 旋钮用实心圆盖住横线，形成「推子」观感
        solid {
            circle(9.2f, 7f, 2.1f)
            circle(14.8f, 12f, 2.1f)
            circle(7.6f, 17f, 2.1f)
        }
    }

    /** 删除 / 清空：垃圾桶。 */
    val Trash: ImageVector = strokeIcon("music_trash") {
        line {
            moveTo(4.5f, 7f)
            lineTo(19.5f, 7f)
            moveTo(9.5f, 7f)
            lineTo(9.5f, 5.3f)
            curveTo(9.5f, 4.8f, 9.9f, 4.5f, 10.4f, 4.5f)
            lineTo(13.6f, 4.5f)
            curveTo(14.1f, 4.5f, 14.5f, 4.8f, 14.5f, 5.3f)
            lineTo(14.5f, 7f)
        }
        line {
            moveTo(6.6f, 7f)
            lineTo(7.4f, 19.2f)
            curveTo(7.5f, 20.2f, 8.3f, 21f, 9.3f, 21f)
            lineTo(14.7f, 21f)
            curveTo(15.7f, 21f, 16.5f, 20.2f, 16.6f, 19.2f)
            lineTo(17.4f, 7f)
        }
        line(1.6f) {
            moveTo(10.1f, 10.5f)
            lineTo(10.4f, 17.4f)
            moveTo(13.9f, 10.5f)
            lineTo(13.6f, 17.4f)
        }
    }

    /** 顺序播放：上下两条带箭头的回路。 */
    val Repeat: ImageVector = strokeIcon("music_repeat") {
        line {
            // 上排：左向箭头 + 横线
            moveTo(8.4f, 5.6f)
            lineTo(6.2f, 7.8f)
            lineTo(8.4f, 10f)
            moveTo(6.2f, 7.8f)
            lineTo(17.2f, 7.8f)
            // 下排：右向箭头 + 横线
            moveTo(15.6f, 14f)
            lineTo(17.8f, 16.2f)
            lineTo(15.6f, 18.4f)
            moveTo(17.8f, 16.2f)
            lineTo(6.8f, 16.2f)
        }
    }

    /** 单曲循环：回路中间插一个「1」。 */
    val RepeatOne: ImageVector = strokeIcon("music_repeat_one") {
        line {
            moveTo(8.4f, 5.6f)
            lineTo(6.2f, 7.8f)
            lineTo(8.4f, 10f)
            moveTo(6.2f, 7.8f)
            lineTo(17.2f, 7.8f)
            moveTo(15.6f, 14f)
            lineTo(17.8f, 16.2f)
            lineTo(15.6f, 18.4f)
            moveTo(17.8f, 16.2f)
            lineTo(6.8f, 16.2f)
        }
        line(1.7f) {
            moveTo(11.1f, 10.6f)
            lineTo(12.4f, 10.2f)
            lineTo(12.4f, 13.6f)
        }
    }

    /** 随机播放：两条交叉并各自带箭头的连线。 */
    val Shuffle: ImageVector = strokeIcon("music_shuffle") {
        line {
            moveTo(4.6f, 7f)
            lineTo(7.8f, 7f)
            curveTo(10.6f, 7f, 12.4f, 17f, 15.4f, 17f)
            lineTo(17.6f, 17f)
            moveTo(15.2f, 14.6f)
            lineTo(17.6f, 17f)
            lineTo(15.2f, 19.4f)

            moveTo(4.6f, 17f)
            lineTo(7.8f, 17f)
            curveTo(10.6f, 17f, 12.4f, 7f, 15.4f, 7f)
            lineTo(17.6f, 7f)
            moveTo(15.2f, 4.6f)
            lineTo(17.6f, 7f)
            lineTo(15.2f, 9.4f)
        }
    }

    /** 空状态：音符。 */
    val MusicNote: ImageVector = strokeIcon("music_note") {
        solid {
            circle(7.6f, 17.4f, 2.4f)
            circle(16.4f, 15.6f, 2.4f)
        }
        line {
            moveTo(9.6f, 17f)
            lineTo(9.6f, 6.4f)
            moveTo(18.4f, 15.2f)
            lineTo(18.4f, 4.6f)
            moveTo(9.6f, 6.4f)
            lineTo(18.4f, 4.6f)
            moveTo(9.6f, 9.4f)
            lineTo(18.4f, 7.6f)
        }
    }
}

/** 统一的描边图标构造器。 */
private fun strokeIcon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()

/** 描边路径：圆头 + 圆角连接。 */
private fun ImageVector.Builder.line(
    width: Float = 1.8f,
    pathBuilder: PathBuilder.() -> Unit,
) {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero,
        pathBuilder = pathBuilder,
    )
}

/** 实心路径。 */
private fun ImageVector.Builder.solid(pathBuilder: PathBuilder.() -> Unit) {
    path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero,
        pathBuilder = pathBuilder,
    )
}

/** 整圆（两段半圆弧）。 */
private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, false, true, cx + r, cy)
    arcTo(r, r, 0f, false, true, cx - r, cy)
    close()
}

/** 心形轮廓，供描边与实心两版共用。 */
private fun PathBuilder.heartPath() {
    moveTo(12f, 20.3f)
    curveTo(6.4f, 16.4f, 3.4f, 13.2f, 3.4f, 9.4f)
    curveTo(3.4f, 6.5f, 5.6f, 4.4f, 8.3f, 4.4f)
    curveTo(10.1f, 4.4f, 11.3f, 5.4f, 12f, 6.6f)
    curveTo(12.7f, 5.4f, 13.9f, 4.4f, 15.7f, 4.4f)
    curveTo(18.4f, 4.4f, 20.6f, 6.5f, 20.6f, 9.4f)
    curveTo(20.6f, 13.2f, 17.6f, 16.4f, 12f, 20.3f)
    close()
}
