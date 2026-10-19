package com.musicbox.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.musicbox.app.player.PositionSource
import com.musicbox.app.ui.lyric.rememberFramePosition
import kotlin.math.sin

/** 64 位黄金分割常数，用于把下标打散成互不相关的散列值。 */
private const val GOLDEN_RATIO_64 = 0x9E3779B97F4A7C15uL

/** 单颗粒子的确定性参数（全部由下标推导，无随机数）。 */
data class ParticleSeed(
    /** 横向初始位置比例，[0,1)。 */
    val x: Float,
    /** 纵向初始位置比例，[0,1)。 */
    val y: Float,
    /** 上浮速度系数，[0.05,0.25]。 */
    val speed: Float,
    /** 半径系数（相对基准半径），[0.4,1]。 */
    val radiusFactor: Float,
    /** 相位偏移，[0,2π)，让各粒子的摆动/脉动错开。 */
    val phase: Float,
    /** 基础不透明度，[0.10,0.35]。 */
    val alpha: Float,
)

/**
 * 由 [index]（与总数 [count]）确定性地推导一颗粒子的参数。纯函数、无随机、无分配，可单测。
 *
 * 这是**装饰性**粒子，不读取任何音频数据，只由播放位置与播放状态驱动。
 */
fun particleParams(index: Int, count: Int): ParticleSeed {
    // 用不同的 salt 取多组互不相关的哈希值
    fun unit(salt: Int): Float {
        var x = index.toULong() * GOLDEN_RATIO_64 xor (salt.toULong() * 0xC2B2AE3D27D4EB4FuL)
        x = x xor (x shr 33)
        x = x * 0xFF51AFD7ED558CCDuL
        x = x xor (x shr 33)
        x = x * 0xC4CEB9FE1A85EC53uL
        x = x xor (x shr 33)
        return (x and 0xFFFFFFFFuL).toFloat() / 4294967296f
    }

    val n = count.coerceAtLeast(1)
    // 横向：把下标均分到 [0,1) 再加哈希抖动 → 分布均匀且不同下标必然不同
    val spread = ((index + unit(1)) / n).coerceIn(0f, 0.999f)
    return ParticleSeed(
        x = spread,
        y = unit(2),
        speed = 0.05f + 0.20f * unit(3),
        radiusFactor = 0.4f + 0.6f * unit(4),
        phase = TWO_PI * unit(5),
        alpha = 0.10f + 0.25f * unit(6),
    )
}

private const val TWO_PI = 6.2831855f

/**
 * 粒子背景层：固定数量（默认 40）的细小粒子缓慢上浮、横向微摆、明暗脉动。
 *
 * 全部运动由**播放位置**（[rememberFramePosition]，毫秒）推导：暂停时位置恒定 →
 * 粒子自然静止（"暂停即静止"），无需额外判断；[isPlaying] 只用来压低暂停时的亮度。
 *
 * 逐帧值只在 Canvas 的 draw lambda 里读取 → 每帧只失效 draw 阶段，不触发重组。
 * 粒子参数在 `remember` 中一次性算好，draw 过程中不新建对象/列表（[Offset]/[androidx.compose.ui.graphics.Color]
 * 均为 inline value class，无堆分配）。
 *
 * 纯装饰层：不含任何 pointerInput/clickable，不拦截点击。
 */
@Composable
fun ParticleField(
    source: PositionSource,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    particleCount: Int = 40,
) {
    val seeds = remember(particleCount) {
        List(particleCount) { particleParams(it, particleCount) }
    }
    val framePosition = rememberFramePosition(source)
    val baseColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val p = framePosition.value.toFloat()
        val baseRadius = size.minDimension * 0.006f
        val playFactor = if (isPlaying) 1f else 0.55f

        for (i in seeds.indices) {
            val seed = seeds[i]
            // 竖直：向上漂移，对 1 取模让粒子循环出现（手动取模避免负数）
            val yFraction = ((seed.y - p * seed.speed / 12000f) % 1f + 1f) % 1f
            // 水平：小幅正弦摆动
            val xFraction = seed.x + 0.02f * sin(p / 2500f + seed.phase)
            val alpha = (seed.alpha * (0.55f + 0.45f * sin(p / 900f + seed.phase)) * playFactor)
                .coerceIn(0f, 1f)
            drawCircle(
                color = baseColor.copy(alpha = alpha),
                radius = baseRadius * seed.radiusFactor,
                center = Offset(xFraction * size.width, yFraction * size.height),
            )
        }
    }
}
